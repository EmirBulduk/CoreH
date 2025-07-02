package org.arch.me.listeners;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.BufferZone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BufferZoneListener implements Listener {

    private final EnhancedCoreH plugin;
    private final Map<UUID, String> lastBufferZone = new HashMap<>();

    public BufferZoneListener(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        BufferZone zone = plugin.getBufferZoneManager().getBufferZoneAtLocation(event.getBlock().getLocation());
        if (zone != null) {
            if (!plugin.getBufferZoneManager().canBuild(event.getBlock().getLocation(), event.getPlayer())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou cannot build in this buffer zone!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        BufferZone zone = plugin.getBufferZoneManager().getBufferZoneAtLocation(event.getBlock().getLocation());
        if (zone != null) {
            if (!plugin.getBufferZoneManager().canDestroy(event.getBlock().getLocation(), event.getPlayer())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou cannot break blocks in this buffer zone!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled() || event.getClickedBlock() == null) return;

        BufferZone zone = plugin.getBufferZoneManager().getBufferZoneAtLocation(event.getClickedBlock().getLocation());
        if (zone != null) {
            if (!plugin.getBufferZoneManager().canInteract(event.getClickedBlock().getLocation(), event.getPlayer())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou cannot interact with blocks in this buffer zone!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) return;

        BufferZone zone = plugin.getBufferZoneManager().getBufferZoneAtLocation(event.getLocation());
        if (zone != null && !zone.getFlag("mob_spawning")) {
            // Cancel natural mob spawning in buffer zones
            CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();

            // Always cancel these common spawn reasons
            if (reason == CreatureSpawnEvent.SpawnReason.NATURAL ||
                reason == CreatureSpawnEvent.SpawnReason.SPAWNER) {
                event.setCancelled(true);
                return;
            }

            // Handle version-specific spawn reasons using reflection
            String reasonName = reason.name();
            if ("CHUNK_GEN".equals(reasonName) || "WORLD_GENERATION".equals(reasonName)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        BufferZone zone = plugin.getBufferZoneManager().getBufferZoneAtLocation(player.getLocation());

        if (zone != null && zone.getFlag("no_starve")) {
            // Prevent hunger decrease in buffer zones
            if (event.getFoodLevel() < player.getFoodLevel()) {
                event.setCancelled(true);
                // Keep food level at maximum
                player.setFoodLevel(20);
                player.setSaturation(20.0f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHealthRegain(EntityRegainHealthEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        BufferZone zone = plugin.getBufferZoneManager().getBufferZoneAtLocation(player.getLocation());

        if (zone != null && zone.getFlag("regeneration")) {
            // Enhanced regeneration in buffer zones
            if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN ||
                event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
                event.setAmount(event.getAmount() * 2.0); // Double regeneration rate
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        BufferZone zone = plugin.getBufferZoneManager().getBufferZoneAtLocation(player.getLocation());

        String currentZone = zone != null ? zone.getName() : null;
        String lastZone = lastBufferZone.get(player.getUniqueId());

        // Check if player entered or left a buffer zone
        if (!java.util.Objects.equals(currentZone, lastZone)) {
            if (currentZone != null) {
                // Player entered buffer zone
                showBufferZoneActionBar(player, zone);
                lastBufferZone.put(player.getUniqueId(), currentZone);
            } else {
                // Player left buffer zone
                clearBufferZoneActionBar(player);
                lastBufferZone.remove(player.getUniqueId());
            }
        } else if (currentZone != null) {
            // Player is still in buffer zone, keep showing action bar
            showBufferZoneActionBar(player, zone);
        }
    }

    private void showBufferZoneActionBar(Player player, BufferZone zone) {
        String message = ChatColor.BLUE + "★ " + ChatColor.BOLD + "UN BUFFER ZONE" + ChatColor.RESET +
                        ChatColor.BLUE + " - " + zone.getName() + " ★";

        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (Exception e) {
            // Fallback for older versions
            player.sendMessage(message);
        }
    }

    private void clearBufferZoneActionBar(Player player) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        } catch (Exception e) {
            // Ignore for older versions
        }
    }
}
