package org.arch.me.listeners;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.ClaimedChunk;
import org.arch.me.models.TownyPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class WarListener implements Listener {

    private final EnhancedCoreH plugin;

    public WarListener(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if player moved to a different chunk
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }

        Player player = event.getPlayer();

        // Handle leaving a chunk
        handleChunkLeave(player, event.getFrom());

        // Handle entering a new chunk
        handleChunkEnter(player, event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // Handle leaving previous chunk
        handleChunkLeave(player, event.getFrom());

        // Handle entering new chunk
        handleChunkEnter(player, event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Handle leaving current chunk when disconnecting
        handleChunkLeave(player, player.getLocation());
    }

    private void handleChunkEnter(Player player, org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) return;

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(location);
        if (chunk == null) return;

        // Check if this is a capital chunk and handle capitulation
        plugin.getWarManager().handlePlayerEnterCapitalChunk(player, chunk);
    }

    private void handleChunkLeave(Player player, org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) return;

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(location);
        if (chunk == null) return;

        // Check if this is a capital chunk and handle capitulation
        plugin.getWarManager().handlePlayerLeaveCapitalChunk(player, chunk);
    }
}
