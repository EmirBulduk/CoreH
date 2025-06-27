package org.arch.me.listeners;

import org.arch.me.EnhancedCoreH;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final EnhancedCoreH plugin;

    public PlayerJoinListener(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getPlayerManager().createPlayer(event.getPlayer());
    }
}
