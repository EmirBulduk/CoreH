package org.arch.me.util;

import org.arch.me.EnhancedCoreH;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportationUtil {

    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final Map<UUID, BukkitRunnable> pendingTeleports = new HashMap<>();

    /**
     * Teleport player with cooldown and delay
     */
    public static void teleportPlayer(Player player, Location destination, EnhancedCoreH plugin,
                                      int delay, int cooldown, String cooldownMessage) {
        UUID playerId = player.getUniqueId();

        // Check cooldown
        if (isOnCooldown(playerId, cooldown)) {
            long remaining = getRemainingCooldown(playerId, cooldown);
            MessageUtil.sendError(player, cooldownMessage.replace("{time}", String.valueOf(remaining)));
            return;
        }

        // Cancel existing teleport
        cancelPendingTeleport(playerId);

        if (delay <= 0) {
            // Instant teleport
            performTeleport(player, destination, plugin);
            setCooldown(playerId);
        } else {
            // Delayed teleport
            startDelayedTeleport(player, destination, plugin, delay);
        }
    }

    /**
     * Teleport to town spawn
     */
    public static void teleportToTownSpawn(Player player, Location spawn, EnhancedCoreH plugin) {
        int delay = plugin.getConfig().getInt("towns.spawn-delay", 5);
        int cooldown = plugin.getConfig().getInt("towns.spawn-cooldown", 30);

        teleportPlayer(player, spawn, plugin, delay, cooldown,
                "§cYou must wait {time} seconds before teleporting again!");
    }

    /**
     * Teleport to nation spawn
     */
    public static void teleportToNationSpawn(Player player, Location spawn, EnhancedCoreH plugin) {
        int delay = plugin.getConfig().getInt("nations.spawn-delay", 10);
        int cooldown = plugin.getConfig().getInt("nations.spawn-cooldown", 60);

        teleportPlayer(player, spawn, plugin, delay, cooldown,
                "§cYou must wait {time} seconds before teleporting again!");
    }

    /**
     * Start delayed teleport with movement cancellation
     */
    private static void startDelayedTeleport(Player player, Location destination, EnhancedCoreH plugin, int delay) {
        UUID playerId = player.getUniqueId();
        Location startLocation = player.getLocation().clone();

        MessageUtil.sendMessage(player, "§eTeleporting in " + delay + " seconds. Don't move!");

        BukkitRunnable teleportTask = new BukkitRunnable() {
            int countdown = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    pendingTeleports.remove(playerId);
                    return;
                }

                // Check if player moved
                if (hasMoved(player.getLocation(), startLocation)) {
                    MessageUtil.sendError(player, "Teleportation cancelled - you moved!");
                    cancel();
                    pendingTeleports.remove(playerId);
                    return;
                }

                if (countdown <= 0) {
                    performTeleport(player, destination, plugin);
                    setCooldown(playerId);
                    cancel();
                    pendingTeleports.remove(playerId);
                } else {
                    if (countdown <= 3) {
                        MessageUtil.sendActionBar(player, "§eTeleporting in §c" + countdown + "§e...");
                    }
                    countdown--;
                }
            }
        };

        pendingTeleports.put(playerId, teleportTask);
        teleportTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Perform the actual teleport
     */
    private static void performTeleport(Player player, Location destination, EnhancedCoreH plugin) {
        // Ensure destination is safe
        Location safeDest = findSafeLocation(destination);

        player.teleport(safeDest);
        MessageUtil.sendSuccess(player, "Teleported successfully!");

        // Play sound effect if configured
        if (plugin.getConfig().getBoolean("teleport.play-sound", true)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }
    }

    /**
     * Find safe location for teleportation
     */
    private static Location findSafeLocation(Location location) {
        Location safe = location.clone();

        // Ensure we're not in a block
        while (safe.getBlock().getType().isSolid() && safe.getY() < safe.getWorld().getMaxHeight()) {
            safe.add(0, 1, 0);
        }

        // Ensure we have ground beneath
        while (!safe.clone().subtract(0, 1, 0).getBlock().getType().isSolid() && safe.getY() > 0) {
            safe.subtract(0, 1, 0);
        }

        return safe;
    }

    /**
     * Check if player moved significantly
     */
    private static boolean hasMoved(Location current, Location start) {
        return current.distance(start) > 1.0;
    }

    /**
     * Cancel pending teleport
     */
    public static void cancelPendingTeleport(UUID playerId) {
        BukkitRunnable task = pendingTeleports.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Set cooldown for player
     */
    private static void setCooldown(UUID playerId) {
        cooldowns.put(playerId, System.currentTimeMillis());
    }

    /**
     * Check if player is on cooldown
     */
    private static boolean isOnCooldown(UUID playerId, int cooldownSeconds) {
        Long lastTeleport = cooldowns.get(playerId);
        if (lastTeleport == null) return false;

        long elapsed = (System.currentTimeMillis() - lastTeleport) / 1000;
        return elapsed < cooldownSeconds;
    }

    /**
     * Get remaining cooldown time
     */
    private static long getRemainingCooldown(UUID playerId, int cooldownSeconds) {
        Long lastTeleport = cooldowns.get(playerId);
        if (lastTeleport == null) return 0;

        long elapsed = (System.currentTimeMillis() - lastTeleport) / 1000;
        return Math.max(0, cooldownSeconds - elapsed);
    }

    /**
     * Clear old cooldowns
     */
    public static void cleanupCooldowns() {
        long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}