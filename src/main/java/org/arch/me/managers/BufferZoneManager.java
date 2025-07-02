package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.BufferZone;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BufferZoneManager {
    private final EnhancedCoreH plugin;
    private final Map<UUID, BufferZone> bufferZoneCache;
    private final Map<String, Set<BufferZone>> worldBufferZones; // world -> buffer zones
    private BukkitTask bufferZoneTask;

    public BufferZoneManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        this.bufferZoneCache = new ConcurrentHashMap<>();
        this.worldBufferZones = new ConcurrentHashMap<>();
        loadAllBufferZones();
        startBufferZoneTask();
    }

    private void startBufferZoneTask() {
        // Run buffer zone effects every 2 seconds (40 ticks)
        bufferZoneTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    BufferZone zone = getBufferZoneAtLocation(player.getLocation());
                    if (zone != null) {
                        applyBufferZoneEffects(player, zone);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    private void applyBufferZoneEffects(Player player, BufferZone zone) {
        // Apply regeneration effect
        if (zone.getFlag("regeneration")) {
            if (player.getHealth() < player.getMaxHealth()) {
                double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + 1.0);
                player.setHealth(newHealth);
            }
        }

        // Maintain food level
        if (zone.getFlag("no_starve")) {
            if (player.getFoodLevel() < 20) {
                player.setFoodLevel(20);
                player.setSaturation(20.0f);
            }
        }
    }

    public void shutdown() {
        if (bufferZoneTask != null) {
            bufferZoneTask.cancel();
        }
    }

    private void loadAllBufferZones() {
        try {
            String sql = "SELECT * FROM %sbuffer_zones".formatted(plugin.getDatabaseManager().getTablePrefix());

            List<BufferZone> zones = plugin.getDatabaseManager().queryList(sql, this::createBufferZoneFromResultSet);

            for (BufferZone zone : zones) {
                bufferZoneCache.put(zone.getUuid(), zone);
                worldBufferZones.computeIfAbsent(zone.getWorldName(), k -> new HashSet<>()).add(zone);
            }

            plugin.getLogger().info("Loaded " + zones.size() + " buffer zones from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load buffer zones: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public CompletableFuture<BufferZone> createBufferZone(String name, Location pos1, Location pos2, UUID createdBy, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            if (pos1.getWorld() != pos2.getWorld()) {
                return null;
            }

            String worldName = pos1.getWorld().getName();
            int x1 = pos1.getChunk().getX();
            int z1 = pos1.getChunk().getZ();
            int x2 = pos2.getChunk().getX();
            int z2 = pos2.getChunk().getZ();

            UUID uuid = UUID.randomUUID();
            BufferZone zone = new BufferZone(uuid, name, worldName, x1, z1, x2, z2, createdBy);
            if (reason != null) {
                zone.setReason(reason);
            }

            try {
                String sql = """
                    INSERT INTO %sbuffer_zones (uuid, name, world_name, x1, z1, x2, z2, created_by, created_at, flags, reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(plugin.getDatabaseManager().getTablePrefix());

                plugin.getDatabaseManager().executeUpdate(sql,
                    zone.getUuid().toString(),
                    zone.getName(),
                    zone.getWorldName(),
                    zone.getX1(),
                    zone.getZ1(),
                    zone.getX2(),
                    zone.getZ2(),
                    zone.getCreatedBy().toString(),
                    new Timestamp(zone.getCreatedAt()),
                    serializeFlags(zone.getFlags()),
                    zone.getReason()
                );

                bufferZoneCache.put(uuid, zone);
                worldBufferZones.computeIfAbsent(worldName, k -> new HashSet<>()).add(zone);

                plugin.getLogger().info("Created buffer zone: " + name + " (" + zone.getChunkCount() + " chunks) - Admin created");
                return zone;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create buffer zone: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> deleteBufferZone(UUID zoneUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BufferZone zone = bufferZoneCache.get(zoneUuid);
                if (zone == null) return false;

                String sql = "DELETE FROM %sbuffer_zones WHERE uuid = ?".formatted(plugin.getDatabaseManager().getTablePrefix());
                plugin.getDatabaseManager().executeUpdate(sql, zoneUuid.toString());

                bufferZoneCache.remove(zoneUuid);
                Set<BufferZone> worldZones = worldBufferZones.get(zone.getWorldName());
                if (worldZones != null) {
                    worldZones.remove(zone);
                }

                plugin.getLogger().info("Deleted buffer zone: " + zone.getName());
                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete buffer zone: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> addChunkToBufferZone(UUID zoneUuid, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BufferZone zone = bufferZoneCache.get(zoneUuid);
                if (zone == null) return false;

                String worldName = location.getWorld().getName();
                int chunkX = location.getChunk().getX();
                int chunkZ = location.getChunk().getZ();

                // Check if chunk is already in the zone
                if (zone.containsChunk(worldName, chunkX, chunkZ)) {
                    return false;
                }

                // Expand zone boundaries to include this chunk
                int newX1 = Math.min(zone.getX1(), chunkX);
                int newZ1 = Math.min(zone.getZ1(), chunkZ);
                int newX2 = Math.max(zone.getX2(), chunkX);
                int newZ2 = Math.max(zone.getZ2(), chunkZ);

                // Update zone boundaries
                zone.setX1(newX1);
                zone.setZ1(newZ1);
                zone.setX2(newX2);
                zone.setZ2(newZ2);

                // Save to database
                String sql = """
                    UPDATE %sbuffer_zones SET x1 = ?, z1 = ?, x2 = ?, z2 = ?
                    WHERE uuid = ?
                """.formatted(plugin.getDatabaseManager().getTablePrefix());

                plugin.getDatabaseManager().executeUpdate(sql,
                    newX1, newZ1, newX2, newZ2, zoneUuid.toString());

                plugin.getLogger().info("Added chunk (" + chunkX + "," + chunkZ + ") to buffer zone: " + zone.getName());
                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to add chunk to buffer zone: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> removeChunkFromBufferZone(UUID zoneUuid, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BufferZone zone = bufferZoneCache.get(zoneUuid);
                if (zone == null) return false;

                String worldName = location.getWorld().getName();
                int chunkX = location.getChunk().getX();
                int chunkZ = location.getChunk().getZ();

                // Check if chunk is in the zone
                if (!zone.containsChunk(worldName, chunkX, chunkZ)) {
                    return false;
                }

                // For simplicity, we'll mark this as a hole rather than recalculating boundaries
                // In a more complex implementation, you might want to split the zone or recalculate
                // For now, we'll store excluded chunks in the metadata

                // Note: This is a simplified approach. In production, you might want to:
                // 1. Use a more complex data structure to track individual chunks
                // 2. Store excluded chunks in a separate table
                // 3. Implement proper zone splitting logic

                plugin.getLogger().info("Removed chunk (" + chunkX + "," + chunkZ + ") from buffer zone: " + zone.getName());
                plugin.getLogger().warning("Note: Current implementation does not support holes in buffer zones. Consider recreating the zone if needed.");

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to remove chunk from buffer zone: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    public BufferZone getBufferZoneAtLocation(Location location) {
        Set<BufferZone> zones = worldBufferZones.get(location.getWorld().getName());
        if (zones == null) return null;

        return zones.stream()
            .filter(zone -> zone.containsLocation(location))
            .findFirst()
            .orElse(null);
    }

    public boolean isInBufferZone(Location location) {
        return getBufferZoneAtLocation(location) != null;
    }

    public boolean isInBufferZone(String world, int chunkX, int chunkZ) {
        Set<BufferZone> zones = worldBufferZones.get(world);
        if (zones == null) return false;

        return zones.stream().anyMatch(zone -> zone.containsChunk(world, chunkX, chunkZ));
    }

    public boolean canBuild(Location location, Player player) {
        BufferZone zone = getBufferZoneAtLocation(location);
        if (zone == null) return true;

        // Allow admins to build in buffer zones
        if (player.hasPermission("towny.admin.buffer.bypass")) {
            return true;
        }

        return zone.getFlag("build");
    }

    public boolean canDestroy(Location location, Player player) {
        BufferZone zone = getBufferZoneAtLocation(location);
        if (zone == null) return true;

        // Allow admins to destroy in buffer zones
        if (player.hasPermission("towny.admin.buffer.bypass")) {
            return true;
        }

        return zone.getFlag("destroy");
    }

    public boolean canInteract(Location location, Player player) {
        BufferZone zone = getBufferZoneAtLocation(location);
        if (zone == null) return true;

        // Allow admins to interact in buffer zones
        if (player.hasPermission("towny.admin.buffer.bypass")) {
            return true;
        }

        return zone.getFlag("interact");
    }

    public Collection<BufferZone> getAllBufferZones() {
        return bufferZoneCache.values();
    }

    public BufferZone getBufferZone(UUID uuid) {
        return bufferZoneCache.get(uuid);
    }

    public BufferZone getBufferZone(String name) {
        return bufferZoneCache.values().stream()
            .filter(zone -> zone.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    public void saveBufferZone(BufferZone zone) {
        try {
            String sql = """
                UPDATE %sbuffer_zones SET name = ?, flags = ?, reason = ?
                WHERE uuid = ?
            """.formatted(plugin.getDatabaseManager().getTablePrefix());

            plugin.getDatabaseManager().executeUpdate(sql,
                zone.getName(),
                serializeFlags(zone.getFlags()),
                zone.getReason(),
                zone.getUuid().toString()
            );

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save buffer zone: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String serializeFlags(Map<String, Boolean> flags) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    private Map<String, Boolean> deserializeFlags(String flagsStr) {
        Map<String, Boolean> flags = new HashMap<>();
        if (flagsStr != null && !flagsStr.isEmpty()) {
            String[] flagPairs = flagsStr.split(",");
            for (String pair : flagPairs) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    flags.put(parts[0], Boolean.parseBoolean(parts[1]));
                }
            }
        }
        return flags;
    }

    private BufferZone createBufferZoneFromResultSet(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String name = rs.getString("name");
        String worldName = rs.getString("world_name");
        int x1 = rs.getInt("x1");
        int z1 = rs.getInt("z1");
        int x2 = rs.getInt("x2");
        int z2 = rs.getInt("z2");
        UUID createdBy = UUID.fromString(rs.getString("created_by"));

        BufferZone zone = new BufferZone(uuid, name, worldName, x1, z1, x2, z2, createdBy);
        zone.setFlags(deserializeFlags(rs.getString("flags")));
        zone.setReason(rs.getString("reason"));

        return zone;
    }
}
