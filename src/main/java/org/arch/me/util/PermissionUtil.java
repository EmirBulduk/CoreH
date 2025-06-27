package org.arch.me.util;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Rank;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.bukkit.entity.Player;

public class PermissionUtil {

    /**
     * Check if player has specific towny permission
     */
    public static boolean hasPermission(Player player, String permission, EnhancedCoreH plugin) {
        // Check bukkit permissions first
        if (player.hasPermission(permission)) {
            return true;
        }

        // Check rank permissions
        Rank rank = plugin.getRankManager().getPlayerRank(player.getUniqueId());
        if (rank != null && rank.hasPermission(permission)) {
            return true;
        }

        // Check player-specific permissions
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        return townyPlayer != null && townyPlayer.hasPermission(permission);
    }

    /**
     * Check if player is mayor of their town
     */
    public static boolean isMayor(Player player, EnhancedCoreH plugin) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            return false;
        }

        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        return town != null && town.isMayor(player.getUniqueId());
    }

    /**
     * Check if player is king of their nation
     */
    public static boolean isKing(Player player, EnhancedCoreH plugin) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            return false;
        }

        var nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        return nation != null && nation.isKing(player.getUniqueId());
    }

    /**
     * Check if player has admin bypass permission
     */
    public static boolean hasAdminBypass(Player player) {
        return player.hasPermission("towny.admin.bypass");
    }

    /**
     * Check if player can claim chunks for their town
     */
    public static boolean canClaimChunks(Player player, EnhancedCoreH plugin) {
        return hasPermission(player, "towny.claim", plugin) || isMayor(player, plugin);
    }

    /**
     * Check if player can unclaim chunks for their town
     */
    public static boolean canUnclaimChunks(Player player, EnhancedCoreH plugin) {
        return hasPermission(player, "towny.unclaim", plugin) || isMayor(player, plugin);
    }

    /**
     * Get player's rank priority
     */
    public static int getRankPriority(Player player, EnhancedCoreH plugin) {
        Rank rank = plugin.getRankManager().getPlayerRank(player.getUniqueId());
        return rank != null ? rank.getPriority() : 0;
    }
}