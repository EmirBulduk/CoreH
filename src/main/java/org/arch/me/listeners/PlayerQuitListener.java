package org.arch.me.listeners;

import org.arch.me.EnhancedCoreH;
import org.arch.me.util.TeleportationUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final EnhancedCoreH plugin;

    public PlayerQuitListener(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPlayerManager().updateLastOnline(event.getPlayer().getUniqueId());
        TeleportationUtil.cancelPendingTeleport(event.getPlayer().getUniqueId());
    }
}
