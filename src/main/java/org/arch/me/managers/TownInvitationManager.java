package org.arch.me.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TownInvitationManager {

    private final EnhancedCoreH plugin;
    private final ConcurrentHashMap<UUID, TownInvitation> activeInvitations; // playerUuid -> invitation

    public TownInvitationManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        this.activeInvitations = new ConcurrentHashMap<>();
        loadActiveInvitations();
    }

    private void loadActiveInvitations() {
        try {
            String sql = """
                SELECT * FROM %stown_invitations 
                WHERE expires_at > ? AND status = 'PENDING'
                """.formatted(plugin.getDatabaseManager().getTablePrefix());

            Timestamp now = new Timestamp(System.currentTimeMillis());

            var invitations = plugin.getDatabaseManager().queryList(sql, rs -> {
                return new TownInvitation(
                    UUID.fromString(rs.getString("town_uuid")),
                    UUID.fromString(rs.getString("player_uuid")),
                    UUID.fromString(rs.getString("inviter_uuid")),
                    rs.getTimestamp("invited_at"),
                    rs.getTimestamp("expires_at")
                );
            }, now);

            for (TownInvitation invitation : invitations) {
                activeInvitations.put(invitation.getPlayerUuid(), invitation);
            }

            plugin.getLogger().info("Loaded " + invitations.size() + " active town invitations");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load town invitations: " + e.getMessage());
        }
    }

    public CompletableFuture<Boolean> sendInvitation(UUID townUuid, UUID inviterUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if player already has active invitation
                if (hasActiveInvitation(playerUuid)) {
                    return false;
                }

                Town town = plugin.getTownManager().getTown(townUuid);
                TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(playerUuid);

                if (town == null || townyPlayer == null || townyPlayer.hasTown()) {
                    return false;
                }

                // Create invitation
                Timestamp now = new Timestamp(System.currentTimeMillis());
                Timestamp expires = new Timestamp(now.getTime() + (24 * 60 * 60 * 1000)); // 24 hours

                TownInvitation invitation = new TownInvitation(townUuid, playerUuid, inviterUuid, now, expires);

                // Save to database
                String sql = """
                    INSERT INTO %stown_invitations (town_uuid, player_uuid, inviter_uuid, invited_at, expires_at, status)
                    VALUES (?, ?, ?, ?, ?, 'PENDING')
                    """.formatted(plugin.getDatabaseManager().getTablePrefix());

                plugin.getDatabaseManager().executeUpdate(sql,
                    townUuid.toString(),
                    playerUuid.toString(),
                    inviterUuid.toString(),
                    now,
                    expires
                );

                activeInvitations.put(playerUuid, invitation);

                // Send notification to player
                Player player = plugin.getServer().getPlayer(playerUuid);
                if (player != null) {
                    sendInvitationNotification(player, town, invitation);
                }

                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to send town invitation: " + e.getMessage());
                return false;
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    private void sendInvitationNotification(Player player, Town town, TownInvitation invitation) {
        player.sendMessage("§6=== Town Invitation ===");
        player.sendMessage("§eYou have been invited to join the town §a" + town.getName() + "§e!");
        player.sendMessage("§eMayor: §f" + getPlayerName(town.getMayorUuid()));
        player.sendMessage("§eResidents: §f" + town.getResidentCount() + "/" + town.getMaxResidents());
        player.sendMessage("§eTax Rate: §f" + town.getTaxRate() + "%");

        if (town.hasNation()) {
            var nation = plugin.getNationManager().getNation(town.getNationUuid());
            if (nation != null) {
                player.sendMessage("§eNation: §f" + nation.getName());
            }
        }

        player.sendMessage("");
        player.sendMessage("§eThis invitation will expire in §c24 hours§e.");
        player.sendMessage("");

        // Create clickable buttons
        Component acceptButton = Component.text("§a[ACCEPT]")
            .clickEvent(ClickEvent.runCommand("/town invite accept"))
            .hoverEvent(HoverEvent.showText(Component.text("§aClick to accept the invitation")));

        Component denyButton = Component.text("§c[DENY]")
            .clickEvent(ClickEvent.runCommand("/town invite deny"))
            .hoverEvent(HoverEvent.showText(Component.text("§cClick to deny the invitation")));

        Component message = acceptButton
            .append(Component.text("§7   "))
            .append(denyButton);

        player.sendMessage(message);
        player.sendMessage("§7You can also use: §e/town invite accept§7 or §e/town invite deny");
    }

    public CompletableFuture<Boolean> acceptInvitation(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TownInvitation invitation = activeInvitations.get(playerUuid);
                if (invitation == null || invitation.isExpired()) {
                    return false;
                }

                // Add player to town
                boolean success = plugin.getTownManager().addPlayerToTown(
                    invitation.getTownUuid(),
                    playerUuid
                ).join();

                if (success) {
                    // Update invitation status
                    updateInvitationStatus(invitation, "ACCEPTED");
                    activeInvitations.remove(playerUuid);

                    // Notify players
                    notifyInvitationResult(invitation, true);
                }

                return success;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to accept town invitation: " + e.getMessage());
                return false;
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Boolean> denyInvitation(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TownInvitation invitation = activeInvitations.get(playerUuid);
                if (invitation == null) {
                    return false;
                }

                // Update invitation status
                updateInvitationStatus(invitation, "DENIED");
                activeInvitations.remove(playerUuid);

                // Notify players
                notifyInvitationResult(invitation, false);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to deny town invitation: " + e.getMessage());
                return false;
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Boolean> declineInvitation(UUID playerUuid) {
        // Alias for denyInvitation to maintain consistency with command naming
        return denyInvitation(playerUuid);
    }

    private void updateInvitationStatus(TownInvitation invitation, String status) {
        try {
            String sql = """
                UPDATE %stown_invitations 
                SET status = ?, responded_at = ?
                WHERE town_uuid = ? AND player_uuid = ? AND status = 'PENDING'
                """.formatted(plugin.getDatabaseManager().getTablePrefix());

            plugin.getDatabaseManager().executeUpdate(sql,
                status,
                new Timestamp(System.currentTimeMillis()),
                invitation.getTownUuid().toString(),
                invitation.getPlayerUuid().toString()
            );

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update invitation status: " + e.getMessage());
        }
    }

    private void notifyInvitationResult(TownInvitation invitation, boolean accepted) {
        Town town = plugin.getTownManager().getTown(invitation.getTownUuid());
        if (town == null) return;

        String playerName = getPlayerName(invitation.getPlayerUuid());

        // Notify inviter
        Player inviter = plugin.getServer().getPlayer(invitation.getInviterUuid());
        if (inviter != null) {
            if (accepted) {
                inviter.sendMessage("§a✓ " + playerName + " has accepted your town invitation!");
            } else {
                inviter.sendMessage("§c✗ " + playerName + " has denied your town invitation.");
            }
        }

        // Notify player
        Player player = plugin.getServer().getPlayer(invitation.getPlayerUuid());
        if (player != null) {
            if (accepted) {
                player.sendMessage("§a✓ You have successfully joined the town " + town.getName() + "!");
            } else {
                player.sendMessage("§c✗ You have denied the invitation to join " + town.getName() + ".");
            }
        }

        // Notify mayor if different from inviter
        if (!invitation.getInviterUuid().equals(town.getMayorUuid())) {
            Player mayor = plugin.getServer().getPlayer(town.getMayorUuid());
            if (mayor != null) {
                if (accepted) {
                    mayor.sendMessage("§a" + playerName + " has joined your town!");
                } else {
                    mayor.sendMessage("§c" + playerName + " denied the invitation to join your town.");
                }
            }
        }
    }

    public boolean hasActiveInvitation(UUID playerUuid) {
        TownInvitation invitation = activeInvitations.get(playerUuid);
        if (invitation == null) return false;

        if (invitation.isExpired()) {
            activeInvitations.remove(playerUuid);
            return false;
        }

        return true;
    }

    public TownInvitation getActiveInvitation(UUID playerUuid) {
        return activeInvitations.get(playerUuid);
    }

    private String getPlayerName(UUID playerUuid) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null) return player.getName();

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(playerUuid);
        return townyPlayer != null ? townyPlayer.getName() : "Unknown";
    }

    // Clean up expired invitations periodically
    public void cleanupExpiredInvitations() {
        activeInvitations.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                try {
                    updateInvitationStatus(entry.getValue(), "EXPIRED");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to update expired invitation: " + e.getMessage());
                }
                return true;
            }
            return false;
        });
    }

    public static class TownInvitation {
        private final UUID townUuid;
        private final UUID playerUuid;
        private final UUID inviterUuid;
        private final Timestamp invitedAt;
        private final Timestamp expiresAt;

        public TownInvitation(UUID townUuid, UUID playerUuid, UUID inviterUuid, Timestamp invitedAt, Timestamp expiresAt) {
            this.townUuid = townUuid;
            this.playerUuid = playerUuid;
            this.inviterUuid = inviterUuid;
            this.invitedAt = invitedAt;
            this.expiresAt = expiresAt;
        }

        public UUID getTownUuid() { return townUuid; }
        public UUID getPlayerUuid() { return playerUuid; }
        public UUID getInviterUuid() { return inviterUuid; }
        public Timestamp getInvitedAt() { return invitedAt; }
        public Timestamp getExpiresAt() { return expiresAt; }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt.getTime();
        }
    }
}
