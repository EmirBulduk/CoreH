package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Town;
import org.arch.me.models.TownInvitation;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages town invitations and responses
 */
public class TownInvitationManager {
    private final EnhancedCoreH plugin;
    private final Map<UUID, TownInvitation> activeInvitations; // inviteeUuid -> invitation
    private final Map<UUID, Set<UUID>> playerInvitations; // inviteeUuid -> Set of invitation IDs

    public TownInvitationManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        this.activeInvitations = new ConcurrentHashMap<>();
        this.playerInvitations = new ConcurrentHashMap<>();

        // Start cleanup task for expired invitations
        startCleanupTask();
    }

    /**
     * Send an invitation to a player to join a town
     */
    public CompletableFuture<Boolean> sendInvitation(UUID townUuid, UUID inviterUuid, UUID inviteeUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if player already has a pending invitation from this town
                if (hasActiveTownInvitation(inviteeUuid, townUuid)) {
                    return false;
                }

                // Check if player is already in a town
                var townyPlayer = plugin.getPlayerManager().getPlayer(inviteeUuid);
                if (townyPlayer != null && townyPlayer.hasTown()) {
                    return false;
                }

                // Get town information
                Town town = plugin.getTownManager().getTown(townUuid);
                if (town == null) {
                    return false;
                }

                // Create invitation
                TownInvitation invitation = new TownInvitation(townUuid, inviterUuid, inviteeUuid);

                // Store invitation
                activeInvitations.put(inviteeUuid, invitation);
                playerInvitations.computeIfAbsent(inviteeUuid, k -> new HashSet<>()).add(invitation.getInvitationId());

                // Send message to invitee
                Player invitee = plugin.getServer().getPlayer(inviteeUuid);
                if (invitee != null) {
                    Player inviter = plugin.getServer().getPlayer(inviterUuid);
                    String inviterName = inviter != null ? inviter.getName() : "Unknown";

                    invitee.sendMessage("§6=== Town Invitation ===");
                    invitee.sendMessage("§eYou have been invited to join the town: §a" + town.getName());
                    invitee.sendMessage("§eInvited by: §f" + inviterName);
                    invitee.sendMessage("§eExpires in: §c" + invitation.getFormattedTimeRemaining());
                    invitee.sendMessage("§aUse /town accept to accept this invitation");
                    invitee.sendMessage("§cUse /town decline to decline this invitation");
                    invitee.sendMessage("§7=========================");
                }

                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Error sending town invitation: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Accept a town invitation
     */
    public CompletableFuture<Boolean> acceptInvitation(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TownInvitation invitation = activeInvitations.get(playerUuid);
                if (invitation == null || !invitation.isPending()) {
                    return false;
                }

                // Mark as accepted
                invitation.setAccepted(true);

                // Add player to town
                boolean success = plugin.getTownManager().addPlayerToTown(invitation.getTownUuid(), playerUuid).join();

                if (success) {
                    // Remove invitation
                    removeInvitation(playerUuid);

                    // Notify players
                    Player player = plugin.getServer().getPlayer(playerUuid);
                    Town town = plugin.getTownManager().getTown(invitation.getTownUuid());

                    if (player != null && town != null) {
                        player.sendMessage("§aYou have joined the town: " + town.getName());

                        // Notify inviter
                        Player inviter = plugin.getServer().getPlayer(invitation.getInviterUuid());
                        if (inviter != null) {
                            inviter.sendMessage("§a" + player.getName() + " has accepted your town invitation!");
                        }

                        // Notify mayor if different from inviter
                        if (!invitation.getInviterUuid().equals(town.getMayorUuid())) {
                            Player mayor = plugin.getServer().getPlayer(town.getMayorUuid());
                            if (mayor != null) {
                                mayor.sendMessage("§a" + player.getName() + " has joined your town!");
                            }
                        }
                    }

                    return true;
                } else {
                    // Failed to add to town, clean up invitation
                    removeInvitation(playerUuid);
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error accepting town invitation: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Decline a town invitation
     */
    public CompletableFuture<Boolean> declineInvitation(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TownInvitation invitation = activeInvitations.get(playerUuid);
                if (invitation == null || !invitation.isPending()) {
                    return false;
                }

                // Mark as declined
                invitation.setDeclined(true);

                // Notify players
                Player player = plugin.getServer().getPlayer(playerUuid);
                Town town = plugin.getTownManager().getTown(invitation.getTownUuid());

                if (player != null && town != null) {
                    player.sendMessage("§cYou have declined the invitation to join " + town.getName());

                    // Notify inviter
                    Player inviter = plugin.getServer().getPlayer(invitation.getInviterUuid());
                    if (inviter != null) {
                        inviter.sendMessage("§c" + player.getName() + " has declined your town invitation.");
                    }
                }

                // Remove invitation
                removeInvitation(playerUuid);
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Error declining town invitation: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Get active invitation for a player
     */
    public TownInvitation getActiveInvitation(UUID playerUuid) {
        TownInvitation invitation = activeInvitations.get(playerUuid);
        if (invitation != null && invitation.isPending()) {
            return invitation;
        }
        return null;
    }

    /**
     * Check if player has active invitation from specific town
     */
    public boolean hasActiveTownInvitation(UUID playerUuid, UUID townUuid) {
        TownInvitation invitation = activeInvitations.get(playerUuid);
        return invitation != null && invitation.isPending() && invitation.getTownUuid().equals(townUuid);
    }

    /**
     * Check if player has any active invitation
     */
    public boolean hasActiveInvitation(UUID playerUuid) {
        TownInvitation invitation = activeInvitations.get(playerUuid);
        return invitation != null && invitation.isPending();
    }

    /**
     * Remove invitation for a player
     */
    public void removeInvitation(UUID playerUuid) {
        TownInvitation invitation = activeInvitations.remove(playerUuid);
        if (invitation != null) {
            Set<UUID> invitations = playerInvitations.get(playerUuid);
            if (invitations != null) {
                invitations.remove(invitation.getInvitationId());
                if (invitations.isEmpty()) {
                    playerInvitations.remove(playerUuid);
                }
            }
        }
    }

    /**
     * Get all active invitations (for debugging/admin purposes)
     */
    public Collection<TownInvitation> getAllActiveInvitations() {
        return activeInvitations.values().stream()
                .filter(TownInvitation::isPending)
                .toList();
    }

    /**
     * Clean up expired invitations
     */
    private void cleanupExpiredInvitations() {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, TownInvitation> entry : activeInvitations.entrySet()) {
            if (entry.getValue().isExpired()) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID playerUuid : toRemove) {
            TownInvitation invitation = activeInvitations.get(playerUuid);
            if (invitation != null) {
                // Notify player about expiration
                Player player = plugin.getServer().getPlayer(playerUuid);
                if (player != null) {
                    Town town = plugin.getTownManager().getTown(invitation.getTownUuid());
                    if (town != null) {
                        player.sendMessage("§cYour invitation to join " + town.getName() + " has expired.");
                    }
                }
            }
            removeInvitation(playerUuid);
        }
    }

    /**
     * Start the cleanup task for expired invitations
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredInvitations();
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30); // Run every 30 seconds
    }

    /**
     * Cancel all invitations when plugin is disabled
     */
    public void shutdown() {
        activeInvitations.clear();
        playerInvitations.clear();
    }
}
