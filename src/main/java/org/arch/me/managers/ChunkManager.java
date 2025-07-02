package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.database.DatabaseManager;
import org.arch.me.models.ClaimedChunk;
import org.arch.me.models.Town;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkManager {

    private final EnhancedCoreH plugin;
    private final Map<String, ClaimedChunk> claimedChunks = new ConcurrentHashMap<>(); // Key: "world:x:z"

    public ChunkManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        loadAllClaimedChunks();
    }

    private void loadAllClaimedChunks() {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT * FROM %schunks".formatted(db.getTablePrefix());

            List<ClaimedChunk> chunks = db.queryList(sql, rs -> {
                ClaimedChunk chunk = new ClaimedChunk(
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("z"),
                        UUID.fromString(rs.getString("town_uuid"))
                );

                chunk.setId(rs.getLong("id"));

                // Load UUID if exists in database
                String chunkUuidStr = rs.getString("uuid");
                if (chunkUuidStr != null) {
                    chunk.setUuid(UUID.fromString(chunkUuidStr));
                }

                chunk.setPlotType(rs.getString("plot_type"));
                chunk.setPlotPrice(BigDecimal.valueOf(rs.getDouble("plot_price")));

                String ownerUuidStr = rs.getString("owner_uuid");
                if (ownerUuidStr != null) {
                    chunk.setOwnerUuid(UUID.fromString(ownerUuidStr));
                }

                chunk.setClaimedDate(rs.getTimestamp("claimed_date"));

                // Load permissions, flags, and metadata
                loadChunkPermissions(chunk, rs.getString("permissions"));
                loadChunkFlags(chunk, rs.getString("flags"));
                loadChunkMetadata(chunk, rs.getString("metadata"));

                return chunk;
            });

            for (ClaimedChunk chunk : chunks) {
                String key = getChunkKey(chunk.getWorldName(), chunk.getX(), chunk.getZ());
                claimedChunks.put(key, chunk);

                // Add to town's claimed chunks
                Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
                if (town != null) {
                    town.addClaimedChunk(chunk);
                }
            }

            plugin.getLogger().info("Loaded " + chunks.size() + " claimed chunks from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load claimed chunks: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadChunkPermissions(ClaimedChunk chunk, String permissionsJson) {
        if (permissionsJson != null && !permissionsJson.isEmpty()) {
            Set<String> permissions = new HashSet<>(Arrays.asList(permissionsJson.split(",")));
            chunk.setPermissions(permissions);
        }
    }

    private void loadChunkFlags(ClaimedChunk chunk, String flagsJson) {
        if (flagsJson != null && !flagsJson.isEmpty()) {
            Map<String, Boolean> flags = new HashMap<>();
            String[] flagPairs = flagsJson.split(",");
            for (String pair : flagPairs) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    flags.put(parts[0], Boolean.parseBoolean(parts[1]));
                }
            }
            chunk.setFlags(flags);
        }
    }

    private void loadChunkMetadata(ClaimedChunk chunk, String metadataJson) {
        // TODO: Implement JSON parsing for metadata
    }

    // Chunk claiming
    public CompletableFuture<Boolean> claimChunk(UUID townUuid, Location location, UUID claimerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String world = location.getWorld().getName();
                int chunkX = location.getChunk().getX();
                int chunkZ = location.getChunk().getZ();

                // Check if world allows claiming
                if (!plugin.getConfigManager().getEnabledWorlds().contains(world)) {
                    plugin.getLogger().info("Chunk claim failed: World " + world + " not enabled for claiming");
                    return false;
                }

                if (plugin.getConfigManager().getDisabledClaimingWorlds().contains(world)) {
                    plugin.getLogger().info("Chunk claim failed: World " + world + " disabled for claiming");
                    return false;
                }

                // Check if chunk is already claimed
                if (isChunkClaimed(world, chunkX, chunkZ)) {
                    plugin.getLogger().info("Chunk claim failed: Chunk already claimed at " + world + ":" + chunkX + ":" + chunkZ);
                    return false;
                }

                Town town = plugin.getTownManager().getTown(townUuid);
                if (town == null) {
                    plugin.getLogger().info("Chunk claim failed: Town not found with UUID " + townUuid);
                    return false;
                }

                // Check claiming permissions
                if (!town.isMayor(claimerUuid) && !hasClaimPermission(townUuid, claimerUuid)) {
                    plugin.getLogger().info("Chunk claim failed: Player " + claimerUuid + " lacks claim permission");
                    return false;
                }

                // Check economy requirements - Skip for initial town chunk
                boolean isInitialChunk = (town.getClaimedChunkCount() == 0);
                BigDecimal cost = BigDecimal.ZERO;

                if (!isInitialChunk) {
                    cost = BigDecimal.valueOf(plugin.getConfigManager().getEconomyValue("chunk-claim-cost"));
                    BigDecimal townBalance = plugin.getEconomyManager().getTownBalance(townUuid);

                    plugin.getLogger().info("Chunk claim check: Cost=" + cost + ", Town balance=" + townBalance);

                    if (!plugin.getEconomyManager().hasTownBalance(townUuid, cost)) {
                        plugin.getLogger().info("Chunk claim failed: Insufficient town funds. Required: " + cost + ", Available: " + townBalance);
                        return false;
                    }
                } else {
                    plugin.getLogger().info("Initial town chunk - claiming for free");
                }

                // Check if chunks need to be connected
                if (plugin.getConfigManager().getTownValue("require-chunks-connected") == 1) {
                    if (town.getClaimedChunkCount() > 0 && !isChunkConnectedToTown(townUuid, world, chunkX, chunkZ)) {
                        plugin.getLogger().info("Chunk claim failed: Chunk not connected to existing town chunks");
                        return false;
                    }
                }

                // Check max chunks limit
                int maxChunks = town.getMaxChunks(); // Use town's max chunks instead of config
                int currentChunks = town.getClaimedChunkCount();

                plugin.getLogger().info("Chunk limit check: Current=" + currentChunks + ", Max=" + maxChunks);

                if (currentChunks >= maxChunks) {
                    plugin.getLogger().info("Chunk claim failed: Town reached max chunk limit (" + maxChunks + ")");
                    return false;
                }

                // Withdraw money from town (only if not initial chunk)
                if (!isInitialChunk && cost.compareTo(BigDecimal.ZERO) > 0) {
                    if (!plugin.getEconomyManager().withdrawTown(townUuid, cost)) {
                        plugin.getLogger().info("Chunk claim failed: Failed to withdraw money from town");
                        return false;
                    }
                }

                // Create claimed chunk
                ClaimedChunk chunk = new ClaimedChunk(world, chunkX, chunkZ, townUuid);

                // Save to database
                saveClaimedChunk(chunk);

                // Add to cache and town
                String key = getChunkKey(world, chunkX, chunkZ);
                claimedChunks.put(key, chunk);
                town.addClaimedChunk(chunk);

                plugin.getLogger().info("Chunk successfully claimed at " + world + ":" + chunkX + ":" + chunkZ + " for town " + town.getName());
                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to claim chunk: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // Chunk unclaiming
    public CompletableFuture<Boolean> unclaimChunk(Location location, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String world = location.getWorld().getName();
                int chunkX = location.getChunk().getX();
                int chunkZ = location.getChunk().getZ();

                ClaimedChunk chunk = getClaimedChunk(world, chunkX, chunkZ);
                if (chunk == null) {
                    return false;
                }

                Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
                if (town == null) {
                    return false;
                }

                // Check unclaiming permissions
                if (!town.isMayor(playerUuid) && !hasUnclaimPermission(chunk.getTownUuid(), playerUuid)) {
                    return false;
                }

                // Delete from database
                DatabaseManager db = plugin.getDatabaseManager();
                String sql = "DELETE FROM %schunks WHERE id = ?".formatted(db.getTablePrefix());
                db.executeUpdate(sql, chunk.getId());

                // Remove from cache and town
                String key = getChunkKey(world, chunkX, chunkZ);
                claimedChunks.remove(key);
                town.removeClaimedChunk(chunk);

                plugin.getLogger().info("Chunk unclaimed at " + world + ":" + chunkX + ":" + chunkZ + " from town " + town.getName());
                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to unclaim chunk: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // Unclaim all chunks for a town
    public void unclaimAllChunks(UUID townUuid) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "DELETE FROM %schunks WHERE town_uuid = ?".formatted(db.getTablePrefix());
            db.executeUpdate(sql, townUuid.toString());

            // Remove from cache
            claimedChunks.entrySet().removeIf(entry -> entry.getValue().getTownUuid().equals(townUuid));

            plugin.getLogger().info("Unclaimed all chunks for town: " + townUuid);

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to unclaim all chunks: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Plot management
    public CompletableFuture<Boolean> buyPlot(Location location, UUID buyerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String world = location.getWorld().getName();
                int chunkX = location.getChunk().getX();
                int chunkZ = location.getChunk().getZ();

                ClaimedChunk chunk = getClaimedChunk(world, chunkX, chunkZ);
                if (chunk == null || !chunk.isForSale()) {
                    return false;
                }

                Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
                if (town == null || !town.hasResident(buyerUuid)) {
                    return false;
                }

                // Check if player can afford the plot
                if (!plugin.getEconomyManager().hasPlayerBalance(buyerUuid, chunk.getPlotPrice())) {
                    return false;
                }

                // Transfer money
                if (!plugin.getEconomyManager().transferPlayerToTown(buyerUuid, chunk.getTownUuid(), chunk.getPlotPrice())) {
                    return false;
                }

                // Set owner and remove from sale
                chunk.setOwnerUuid(buyerUuid);
                chunk.setPlotPrice(BigDecimal.ZERO);

                // Save changes
                saveClaimedChunk(chunk);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to buy plot: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> sellPlot(Location location, UUID sellerUuid, BigDecimal price) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String world = location.getWorld().getName();
                int chunkX = location.getChunk().getX();
                int chunkZ = location.getChunk().getZ();

                ClaimedChunk chunk = getClaimedChunk(world, chunkX, chunkZ);
                if (chunk == null || !chunk.isOwner(sellerUuid)) {
                    return false;
                }

                Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
                if (town == null) {
                    return false;
                }

                // Check selling permissions
                if (!chunk.isOwner(sellerUuid) && !town.isMayor(sellerUuid)) {
                    return false;
                }

                // Set for sale
                chunk.setPlotPrice(price);

                // Save changes
                saveClaimedChunk(chunk);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to sell plot: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // Permission checking
    public boolean hasPermission(Location location, UUID playerUuid, String permission) {
        String world = location.getWorld().getName();
        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();

        ClaimedChunk chunk = getClaimedChunk(world, chunkX, chunkZ);

        // If not claimed, check wilderness permissions
        if (chunk == null) {
            return plugin.getConfig().getBoolean("chunks.wilderness-permissions." + permission, false);
        }

        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        if (town == null) {
            return false;
        }

        // Check if player is owner of the plot
        if (chunk.isOwner(playerUuid)) {
            return true;
        }

        // Check if player is mayor
        if (town.isMayor(playerUuid)) {
            return true;
        }

        // Check if player is resident with permissions
        if (town.hasResident(playerUuid)) {
            // Check chunk-specific permissions
            if (chunk.getFlag(permission)) {
                return true;
            }

            // Check town-wide permissions
            if (town.getFlag(permission)) {
                return true;
            }
        }

        // Check if town allows outsiders for this permission
        if (town.isPublic() && town.getFlag("outsider_" + permission)) {
            return true;
        }

        return false;
    }

    // Utility methods
    private boolean isChunkConnectedToTown(UUID townUuid, String world, int chunkX, int chunkZ) {
        // Check if any adjacent chunk belongs to the same town
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int adjX = chunkX + dir[0];
            int adjZ = chunkZ + dir[1];

            ClaimedChunk adjChunk = getClaimedChunk(world, adjX, adjZ);
            if (adjChunk != null && adjChunk.getTownUuid().equals(townUuid)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasClaimPermission(UUID townUuid, UUID playerUuid) {
        Town town = plugin.getTownManager().getTown(townUuid);
        if (town == null) {
            return false;
        }

        // Check if player has claim permission through rank
        // This would integrate with your rank system
        return town.hasResident(playerUuid); // Simple check for now
    }

    private boolean hasUnclaimPermission(UUID townUuid, UUID playerUuid) {
        Town town = plugin.getTownManager().getTown(townUuid);
        if (town == null) {
            return false;
        }

        return town.isMayor(playerUuid); // Only mayors can unclaim for now
    }

    public void removePlayerPlotsInTown(UUID townUuid, UUID playerUuid) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "UPDATE %schunks SET owner_uuid = NULL, plot_price = 0 WHERE town_uuid = ? AND owner_uuid = ?".formatted(db.getTablePrefix());
            db.executeUpdate(sql, townUuid.toString(), playerUuid.toString());

            // Update cache
            for (ClaimedChunk chunk : claimedChunks.values()) {
                if (chunk.getTownUuid().equals(townUuid) && chunk.isOwner(playerUuid)) {
                    chunk.setOwnerUuid(null);
                    chunk.setPlotPrice(BigDecimal.ZERO);
                }
            }

            plugin.getLogger().info("Removed all plots owned by player " + playerUuid + " in town " + townUuid);

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove player plots: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveClaimedChunk(ClaimedChunk chunk) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();

            String sql;
            if (db.isSQLServer()) {
                sql = """
                    MERGE INTO %schunks AS target
                    USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)) AS source 
                    (uuid, world, x, z, town_uuid, plot_type, plot_price, owner_uuid, claimed_date, permissions, flags, metadata)
                    ON target.world = source.world AND target.x = source.x AND target.z = source.z
                    WHEN MATCHED THEN
                        UPDATE SET
                            uuid = source.uuid,
                            plot_type = source.plot_type,
                            plot_price = source.plot_price,
                            owner_uuid = source.owner_uuid,
                            permissions = source.permissions,
                            flags = source.flags,
                            metadata = source.metadata
                    WHEN NOT MATCHED THEN
                        INSERT (uuid, world, x, z, town_uuid, plot_type, plot_price, owner_uuid, claimed_date, permissions, flags, metadata)
                        VALUES (source.uuid, source.world, source.x, source.z, source.town_uuid, source.plot_type, source.plot_price, source.owner_uuid, source.claimed_date, source.permissions, source.flags, source.metadata);
                    """.formatted(db.getTablePrefix());
            } else {
                sql = """
                    INSERT INTO %schunks (uuid, world, x, z, town_uuid, plot_type, plot_price, owner_uuid, 
                    claimed_date, permissions, flags, metadata) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    uuid = VALUES(uuid), plot_type = VALUES(plot_type), plot_price = VALUES(plot_price), owner_uuid = VALUES(owner_uuid),
                    permissions = VALUES(permissions), flags = VALUES(flags), metadata = VALUES(metadata)
                    """.formatted(db.getTablePrefix());
            }

            int result = db.executeUpdateWithResult(sql,
                    chunk.getUuid().toString(),
                    chunk.getWorldName(),
                    chunk.getX(),
                    chunk.getZ(),
                    chunk.getTownUuid().toString(),
                    chunk.getPlotType(),
                    chunk.getPlotPrice().doubleValue(),
                    chunk.getOwnerUuid() != null ? chunk.getOwnerUuid().toString() : null,
                    chunk.getClaimedDate(),
                    serializePermissions(chunk.getPermissions()),
                    serializeFlags(chunk.getFlags()),
                    serializeMetadata(chunk.getMetadata())
            );

            if (result > 0) {
                plugin.getLogger().info("Successfully saved chunk to database: " + chunk.getUuid() + " at " + chunk.getWorldName() + ":" + chunk.getX() + ":" + chunk.getZ());
            } else {
                plugin.getLogger().warning("No rows affected when saving chunk: " + chunk.getUuid());
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save claimed chunk: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String serializePermissions(Set<String> permissions) {
        return String.join(",", permissions);
    }

    private String serializeFlags(Map<String, Boolean> flags) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        return ""; // TODO: Implement JSON serialization
    }

    private String getChunkKey(String world, int x, int z) {
        return world + ":" + x + ":" + z;
    }

    // Getters
    public ClaimedChunk getClaimedChunk(String world, int x, int z) {
        return claimedChunks.get(getChunkKey(world, x, z));
    }

    public ClaimedChunk getClaimedChunk(Location location) {
        Chunk chunk = location.getChunk();
        return getClaimedChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public ClaimedChunk getClaimedChunk(Chunk chunk) {
        return getClaimedChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public boolean isChunkClaimed(String world, int x, int z) {
        return claimedChunks.containsKey(getChunkKey(world, x, z));
    }

    public boolean isChunkClaimed(Location location) {
        Chunk chunk = location.getChunk();
        return isChunkClaimed(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public boolean isChunkClaimed(Chunk chunk) {
        return isChunkClaimed(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public List<ClaimedChunk> getChunksByTown(UUID townUuid) {
        return claimedChunks.values().stream()
                .filter(chunk -> chunk.getTownUuid().equals(townUuid))
                .toList();
    }

    public List<ClaimedChunk> getChunksByOwner(UUID ownerUuid) {
        return claimedChunks.values().stream()
                .filter(chunk -> chunk.isOwner(ownerUuid))
                .toList();
    }

    public int getClaimedChunkCount() {
        return claimedChunks.size();
    }

    public int getClaimedChunkCount(UUID townUuid) {
        return (int) claimedChunks.values().stream()
                .filter(chunk -> chunk.getTownUuid().equals(townUuid))
                .count();
    }
}

