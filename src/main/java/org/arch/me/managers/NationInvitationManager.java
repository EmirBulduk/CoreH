package org.arch.me.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Nation;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NationInvitationManager {

    private final EnhancedCoreH plugin;
    private final ConcurrentHashMap<UUID, NationInvitation> activeInvitations; // townUuid -> invitation

    public NationInvitationManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        this.activeInvitations = new ConcurrentHashMap<>();
        loadActiveInvitations();
    }

    private void loadActiveInvitations() {
        try {
            String sql = """
                SELECT * FROM %snation_invitations 
                WHERE expires_at > ? AND status = 'PENDING'
                """.formatted(plugin.getDatabaseManager().getTablePrefix());

            Timestamp now = new Timestamp(System.currentTimeMillis());

            var invitations = plugin.getDatabaseManager().queryList(sql, rs -> {
                return new NationInvitation(
                    UUID.fromString(rs.getString("nation_uuid")),
                    UUID.fromString(rs.getString("town_uuid")),
                    UUID.fromString(rs.getString("inviter_uuid")),
                    rs.getTimestamp("invited_at"),
                    rs.getTimestamp("expires_at")
                );
            }, now);

            for (NationInvitation invitation : invitations) {
                activeInvitations.put(invitation.getTownUuid(), invitation);
            }

            plugin.getLogger().info("Loaded " + invitations.size() + " active nation invitations");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load nation invitations: " + e.getMessage());
        }
    }

    public CompletableFuture<Boolean> sendInvitation(UUID nationUuid, UUID inviterUuid, UUID townUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if town already has active invitation
                if (hasActiveInvitation(townUuid)) {
                    return false;
                }

                Nation nation = plugin.getNationManager().getNation(nationUuid);
                Town town = plugin.getTownManager().getTown(townUuid);

                if (nation == null || town == null || town.hasNation()) {
                    return false;
                }

                // Create invitation
                Timestamp now = new Timestamp(System.currentTimeMillis());
                Timestamp expires = new Timestamp(now.getTime() + (24 * 60 * 60 * 1000)); // 24 hours

                NationInvitation invitation = new NationInvitation(nationUuid, townUuid, inviterUuid, now, expires);

                // Save to database
                String sql = """
                    INSERT INTO %snation_invitations (nation_uuid, town_uuid, inviter_uuid, invited_at, expires_at, status)
                    VALUES (?, ?, ?, ?, ?, 'PENDING')
                    """.formatted(plugin.getDatabaseManager().getTablePrefix());

                plugin.getDatabaseManager().executeUpdate(sql,
                    nationUuid.toString(),
                    townUuid.toString(),
                    inviterUuid.toString(),
                    now,
                    expires
                );

                activeInvitations.put(townUuid, invitation);

                // Send notification to town mayor
                Player mayor = plugin.getServer().getPlayer(town.getMayorUuid());
                if (mayor != null) {
                    sendInvitationNotification(mayor, nation, town, invitation);
                }

                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to send nation invitation: " + e.getMessage());
                return false;
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    private void sendInvitationNotification(Player mayor, Nation nation, Town town, NationInvitation invitation) {
        mayor.sendMessage("§6=== Nation Invitation ===");
        mayor.sendMessage("§eYour town §a" + town.getName() + "§e has been invited to join the nation §a" + nation.getName() + "§e!");
        mayor.sendMessage("§eNation King: §f" + getPlayerName(nation.getKingUuid()));
        mayor.sendMessage("§eNation Towns: §f" + nation.getTownCount());
        mayor.sendMessage("§eNation Tax: §f" + nation.getTaxRate() + "%");
        mayor.sendMessage("");
        mayor.sendMessage("§eThis invitation will expire in §c24 hours§e.");
        mayor.sendMessage("");

        // Create clickable buttons
        Component acceptButton = Component.text("§a[ACCEPT]")
            .clickEvent(ClickEvent.runCommand("/nation invite accept"))
            .hoverEvent(HoverEvent.showText(Component.text("§aClick to accept the invitation")));

        Component denyButton = Component.text("§c[DENY]")
            .clickEvent(ClickEvent.runCommand("/nation invite deny"))
            .hoverEvent(HoverEvent.showText(Component.text("§cClick to deny the invitation")));

        Component message = acceptButton
            .append(Component.text("§7   "))
            .append(denyButton);

        mayor.sendMessage(message);
        mayor.sendMessage("§7You can also use: §e/nation invite accept§7 or §e/nation invite deny");
    }

    public CompletableFuture<Boolean> acceptInvitation(UUID townUuid) {
        NationInvitation invitation = activeInvitations.get(townUuid);
        if (invitation == null || invitation.isExpired()) {
            return CompletableFuture.completedFuture(false);
        }

        // Chain the operations asynchronously instead of using .join()
        return plugin.getNationManager().addTownToNation(
            invitation.getNationUuid(),
            townUuid
        ).thenApply(success -> {
            if (success) {
                // Update invitation status
                updateInvitationStatus(invitation, "ACCEPTED");
                activeInvitations.remove(townUuid);

                // Notify players
                notifyInvitationResult(invitation, true);
            }
            return success;
        }).exceptionally(e -> {
            plugin.getLogger().severe("Failed to accept nation invitation: " + e.getMessage());
            e.printStackTrace();
            return false;
        });
    }

    public CompletableFuture<Boolean> denyInvitation(UUID townUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                NationInvitation invitation = activeInvitations.get(townUuid);
                if (invitation == null) {
                    return false;
                }

                // Update invitation status
                updateInvitationStatus(invitation, "DENIED");
                activeInvitations.remove(townUuid);

                // Notify players
                notifyInvitationResult(invitation, false);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to deny nation invitation: " + e.getMessage());
                return false;
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    private void updateInvitationStatus(NationInvitation invitation, String status) {
        try {
            String sql = """
                UPDATE %snation_invitations 
                SET status = ?, responded_at = ?
                WHERE nation_uuid = ? AND town_uuid = ? AND status = 'PENDING'
                """.formatted(plugin.getDatabaseManager().getTablePrefix());

            plugin.getDatabaseManager().executeUpdate(sql,
                status,
                new Timestamp(System.currentTimeMillis()),
                invitation.getNationUuid().toString(),
                invitation.getTownUuid().toString()
            );

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update invitation status: " + e.getMessage());
        }
    }

    private void notifyInvitationResult(NationInvitation invitation, boolean accepted) {
        Nation nation = plugin.getNationManager().getNation(invitation.getNationUuid());
        Town town = plugin.getTownManager().getTown(invitation.getTownUuid());

        if (nation == null || town == null) return;

        // Notify inviter
        Player inviter = plugin.getServer().getPlayer(invitation.getInviterUuid());
        if (inviter != null) {
            if (accepted) {
                inviter.sendMessage("§a✓ " + town.getName() + " has accepted your nation invitation!");
            } else {
                inviter.sendMessage("§c✗ " + town.getName() + " has denied your nation invitation.");
            }
        }

        // Notify town mayor
        Player mayor = plugin.getServer().getPlayer(town.getMayorUuid());
        if (mayor != null) {
            if (accepted) {
                mayor.sendMessage("§a✓ Your town has successfully joined the nation " + nation.getName() + "!");
            } else {
                mayor.sendMessage("§c✗ You have denied the invitation to join " + nation.getName() + ".");
            }
        }

        // Notify nation king if different from inviter
        if (!invitation.getInviterUuid().equals(nation.getKingUuid())) {
            Player king = plugin.getServer().getPlayer(nation.getKingUuid());
            if (king != null) {
                if (accepted) {
                    king.sendMessage("§a" + town.getName() + " has joined your nation!");
                } else {
                    king.sendMessage("§c" + town.getName() + " denied the invitation to join your nation.");
                }
            }
        }
    }

    public boolean hasActiveInvitation(UUID townUuid) {
        NationInvitation invitation = activeInvitations.get(townUuid);
        if (invitation == null) return false;

        if (invitation.isExpired()) {
            activeInvitations.remove(townUuid);
            return false;
        }

        return true;
    }

    public NationInvitation getActiveInvitation(UUID townUuid) {
        return activeInvitations.get(townUuid);
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

    public static class NationInvitation {
        private final UUID nationUuid;
        private final UUID townUuid;
        private final UUID inviterUuid;
        private final Timestamp invitedAt;
        private final Timestamp expiresAt;

        public NationInvitation(UUID nationUuid, UUID townUuid, UUID inviterUuid, Timestamp invitedAt, Timestamp expiresAt) {
            this.nationUuid = nationUuid;
            this.townUuid = townUuid;
            this.inviterUuid = inviterUuid;
            this.invitedAt = invitedAt;
            this.expiresAt = expiresAt;
        }

        public UUID getNationUuid() { return nationUuid; }
        public UUID getTownUuid() { return townUuid; }
        public UUID getInviterUuid() { return inviterUuid; }
        public Timestamp getInvitedAt() { return invitedAt; }
        public Timestamp getExpiresAt() { return expiresAt; }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt.getTime();
        }
    }
}
