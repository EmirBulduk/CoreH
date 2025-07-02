package org.arch.me.listeners;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.ClaimedChunk;
import org.arch.me.models.Town;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TownListener implements Listener {

    private final EnhancedCoreH plugin;
    private final Map<UUID, String> lastTown = new HashMap<>();

    public TownListener(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // First check for buffer zones (higher priority)
        if (plugin.getBufferZoneManager().isInBufferZone(player.getLocation())) {
            // Buffer zone takes priority, clear town tracking
            lastTown.remove(player.getUniqueId());
            return;
        }

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());

        String currentTown = null;
        if (chunk != null) {
            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            if (town != null) {
                currentTown = town.getName();
            }
        }

        String lastTownName = lastTown.get(player.getUniqueId());

        // Check if player entered or left a town
        if (!java.util.Objects.equals(currentTown, lastTownName)) {
            if (currentTown != null) {
                // Player entered town
                Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
                showTownActionBar(player, town);
                lastTown.put(player.getUniqueId(), currentTown);
            } else {
                // Player left town to wilderness
                showWildernessActionBar(player);
                lastTown.remove(player.getUniqueId());
            }
        } else if (currentTown != null) {
            // Player is still in town, keep showing action bar
            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            if (town != null) {
                showTownActionBar(player, town);
            }
        } else {
            // Player is in wilderness
            showWildernessActionBar(player);
        }
    }

    private void showTownActionBar(Player player, Town town) {
        String message = ChatColor.GREEN + "⚡ " + ChatColor.BOLD + town.getName().toUpperCase() +
                        ChatColor.RESET + ChatColor.GREEN + " ⚡";

        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (Exception e) {
            // Fallback for older versions
            player.sendMessage(message);
        }
    }

    private void showWildernessActionBar(Player player) {
        String message = ChatColor.GRAY + "◈ " + ChatColor.BOLD + "WILDERNESS" + ChatColor.RESET +
                        ChatColor.GRAY + " ◈";

        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (Exception e) {
            // Fallback for older versions
            player.sendMessage(message);
        }
    }
}
