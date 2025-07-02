package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.database.DatabaseManager;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TownManager {

    private final EnhancedCoreH plugin;
    private final Map<UUID, Town> townCache = new ConcurrentHashMap<>();
    private final Map<String, UUID> townNameCache = new ConcurrentHashMap<>();

    public TownManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        loadAllTowns();
    }

    private void loadAllTowns() {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT * FROM %stowns".formatted(db.getTablePrefix());

            List<Town> towns = db.queryList(sql, rs -> {
                Town town = new Town(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        UUID.fromString(rs.getString("mayor_uuid"))
                );

                // Set all properties
                String nationUuidStr = rs.getString("nation_uuid");
                if (nationUuidStr != null) {
                    town.setNationUuid(UUID.fromString(nationUuidStr));
                }

                // Set spawn location
                String spawnWorld = rs.getString("spawn_world");
                if (spawnWorld != null) {
                    Location spawn = new Location(
                            Bukkit.getWorld(spawnWorld),
                            rs.getDouble("spawn_x"),
                            rs.getDouble("spawn_y"),
                            rs.getDouble("spawn_z"),
                            rs.getFloat("spawn_yaw"),
                            rs.getFloat("spawn_pitch")
                    );
                    town.setSpawn(spawn);
                }

                town.setFounded(rs.getTimestamp("founded"));
                town.setBalance(BigDecimal.valueOf(rs.getDouble("balance")));
                town.setTaxRate(BigDecimal.valueOf(rs.getDouble("tax_rate")));
                town.setUpkeepCost(BigDecimal.valueOf(rs.getDouble("upkeep_cost")));
                town.setMaxResidents(rs.getInt("max_residents"));

                // Handle max_chunks with fallback for existing towns
                try {
                    int maxChunks = rs.getInt("max_chunks");
                    if (maxChunks == 0) {
                        maxChunks = 50; // Default value for existing towns
                        // Update database to fix 0 values
                        fixMaxChunksForTown(town.getUuid(), maxChunks);
                    }
                    town.setMaxChunks(maxChunks);
                } catch (SQLException e) {
                    // Column doesn't exist or is null, set default value
                    town.setMaxChunks(50);
                    fixMaxChunksForTown(town.getUuid(), 50);
                }

                town.setOpen(rs.getBoolean("is_open"));
                town.setPublic(rs.getBoolean("is_public"));
                town.setBoard(rs.getString("board"));

                // Load permissions, flags, and metadata from JSON strings
                loadTownPermissions(town, rs.getString("permissions"));
                loadTownFlags(town, rs.getString("flags"));
                loadTownMetadata(town, rs.getString("metadata"));

                return town;
            });

            for (Town town : towns) {
                townCache.put(town.getUuid(), town);
                townNameCache.put(town.getName().toLowerCase(), town.getUuid());

                // Load residents
                loadTownResidents(town);
                // Load claimed chunks
                loadTownChunks(town);
            }

            plugin.getLogger().info("Loaded " + towns.size() + " towns from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load towns: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void fixMaxChunksForTown(UUID townUuid, int maxChunks) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "UPDATE %stowns SET max_chunks = ? WHERE uuid = ?".formatted(db.getTablePrefix());
            db.executeUpdate(sql, maxChunks, townUuid.toString());
            plugin.getLogger().info("Fixed max_chunks value for town: " + townUuid + " to " + maxChunks);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to fix max_chunks for town " + townUuid + ": " + e.getMessage());
        }
    }

    private void loadTownResidents(Town town) throws SQLException {
        DatabaseManager db = plugin.getDatabaseManager();
        String sql = "SELECT uuid FROM %splayers WHERE town_uuid = ?".formatted(db.getTablePrefix());

        List<UUID> residents = db.queryList(sql, rs -> UUID.fromString(rs.getString("uuid")), town.getUuid().toString());
        town.setResidents(new HashSet<>(residents));
    }

    private void loadTownChunks(Town town) {
        // This will be handled by ChunkManager
        // We'll load chunks when ChunkManager is initialized
    }

    private void loadTownPermissions(Town town, String permissionsJson) {
        // Parse JSON string to Set<String>
        // For now, we'll use a simple comma-separated format
        if (permissionsJson != null && !permissionsJson.isEmpty()) {
            Set<String> permissions = new HashSet<>(Arrays.asList(permissionsJson.split(",")));
            town.setPermissions(permissions);
        }
    }

    private void loadTownFlags(Town town, String flagsJson) {
        // Parse JSON string to Map<String, Boolean>
        // For now, we'll use a simple format: flag1:true,flag2:false
        if (flagsJson != null && !flagsJson.isEmpty()) {
            Map<String, Boolean> flags = new HashMap<>();
            String[] flagPairs = flagsJson.split(",");
            for (String pair : flagPairs) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    flags.put(parts[0], Boolean.parseBoolean(parts[1]));
                }
            }
            town.setFlags(flags);
        }
    }

    private void loadTownMetadata(Town town, String metadataJson) {
        // Parse JSON string to Map<String, Object>
        // For now, we'll leave this empty and implement later
        if (metadataJson != null && !metadataJson.isEmpty()) {
            // TODO: Implement JSON parsing for metadata
        }
    }

    // Town creation
    public CompletableFuture<Town> createTown(String name, UUID mayorUuid, Location spawn) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if town name already exists
                if (townNameExists(name)) {
                    return null;
                }

                // Check if player is already in a town
                TownyPlayer player = plugin.getPlayerManager().getPlayer(mayorUuid);
                if (player != null && player.hasTown()) {
                    return null;
                }

                // Check if location is in buffer zone
                if (plugin.getBufferZoneManager().isInBufferZone(spawn)) {
                    plugin.getLogger().info("Town creation failed: Location is in buffer zone");
                    return null;
                }

                // Check if world allows town creation
                if (!plugin.getConfigManager().getEnabledWorlds().contains(spawn.getWorld().getName())) {
                    plugin.getLogger().info("Town creation failed: World " + spawn.getWorld().getName() + " not enabled for towns");
                    return null;
                }

                if (plugin.getConfigManager().getDisabledClaimingWorlds().contains(spawn.getWorld().getName())) {
                    plugin.getLogger().info("Town creation failed: World " + spawn.getWorld().getName() + " disabled for claiming");
                    return null;
                }

                // Check if chunk is already claimed
                if (plugin.getChunkManager().isChunkClaimed(spawn)) {
                    plugin.getLogger().info("Town creation failed: Chunk already claimed");
                    return null;
                }

                // Check economy requirements
                BigDecimal townCreationCost = BigDecimal.valueOf(plugin.getConfigManager().getEconomyValue("town-creation-cost"));
                // Remove chunk claim cost since initial chunk is free
                BigDecimal totalCost = townCreationCost;

                if (!plugin.getEconomyManager().hasPlayerBalance(mayorUuid, totalCost)) {
                    plugin.getLogger().info("Town creation failed: Insufficient funds. Required: " + totalCost);
                    return null;
                }

                // Create town
                UUID townUuid = UUID.randomUUID();
                Town town = new Town(townUuid, name, mayorUuid);
                town.setSpawn(spawn);

                // Withdraw money for town creation only (initial chunk is free)
                plugin.getEconomyManager().withdrawPlayer(mayorUuid, totalCost);

                // Save town to database first
                saveTown(town);

                // Update player
                if (player != null) {
                    player.setTownUuid(townUuid);
                    player.setJoinedTown(new Timestamp(System.currentTimeMillis()));
                    plugin.getPlayerManager().savePlayer(player);
                }

                // Add to cache
                townCache.put(townUuid, town);
                townNameCache.put(name.toLowerCase(), townUuid);

                // Now claim the initial chunk
                boolean chunkClaimed = plugin.getChunkManager().claimChunk(townUuid, spawn, mayorUuid).join();
                if (!chunkClaimed) {
                    plugin.getLogger().warning("Town created but failed to claim initial chunk for: " + name);
                    // Don't fail the town creation if chunk claiming fails
                }

                plugin.getLogger().info("Created town: " + name + " with mayor: " + mayorUuid + " and claimed initial chunk");
                return town;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create town: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    // Town deletion
    public CompletableFuture<Boolean> deleteTown(UUID townUuid, boolean force) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Town town = getTown(townUuid);
                if (town == null) {
                    return false;
                }

                // Remove from nation if applicable
                if (town.hasNation()) {
                    plugin.getNationManager().removeTownFromNation(town.getNationUuid(), townUuid);
                }

                // Update all residents
                for (UUID residentUuid : town.getResidents()) {
                    TownyPlayer resident = plugin.getPlayerManager().getPlayer(residentUuid);
                    if (resident != null) {
                        resident.setTownUuid(null);
                        resident.setNationUuid(null);
                        resident.setJoinedTown(null);
                        plugin.getPlayerManager().savePlayer(resident);
                    }
                }

                // Unclaim all chunks
                plugin.getChunkManager().unclaimAllChunks(townUuid);

                // Delete from database
                DatabaseManager db = plugin.getDatabaseManager();
                String sql = "DELETE FROM %stowns WHERE uuid = ?".formatted(db.getTablePrefix());
                db.executeUpdate(sql, townUuid.toString());

                // Remove from cache
                townCache.remove(townUuid);
                townNameCache.remove(town.getName().toLowerCase());

                plugin.getLogger().info("Deleted town: " + town.getName());
                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to delete town: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // Player joining town
    public CompletableFuture<Boolean> addPlayerToTown(UUID townUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Town town = getTown(townUuid);
                if (town == null || !town.canAddResident()) {
                    return false;
                }

                TownyPlayer player = plugin.getPlayerManager().getPlayer(playerUuid);
                if (player == null || player.hasTown()) {
                    return false;
                }

                // Add player to town
                town.addResident(playerUuid);
                player.setTownUuid(townUuid);
                player.setJoinedTown(new Timestamp(System.currentTimeMillis()));

                // Set nation if town has one
                if (town.hasNation()) {
                    player.setNationUuid(town.getNationUuid());
                }

                // Save changes
                saveTown(town);
                plugin.getPlayerManager().savePlayer(player);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to add player to town: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // Player leaving town
    public CompletableFuture<Boolean> removePlayerFromTown(UUID townUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Town town = getTown(townUuid);
                if (town == null) {
                    return false;
                }

                TownyPlayer player = plugin.getPlayerManager().getPlayer(playerUuid);
                if (player == null || !player.hasTown()) {
                    return false;
                }

                // Check if player is mayor
                if (town.isMayor(playerUuid)) {
                    // Cannot leave if mayor and has other residents
                    if (town.getResidentCount() > 1) {
                        return false;
                    }
                    // If only mayor, delete the town
                    deleteTown(townUuid, false);
                    return true;
                }

                // Remove player from town
                town.removeResident(playerUuid);
                player.setTownUuid(null);
                player.setNationUuid(null);
                player.setJoinedTown(null);

                // Remove from any owned plots
                plugin.getChunkManager().removePlayerPlotsInTown(townUuid, playerUuid);

                // Save changes
                saveTown(town);
                plugin.getPlayerManager().savePlayer(player);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to remove player from town: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // Town management methods
    public void saveTown(Town town) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();

            String sql;
            if (db.isSQLServer()) {
                sql = """
                    MERGE INTO %stowns AS target
                    USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)) AS source 
                    (uuid, name, mayor_uuid, nation_uuid, spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, founded, balance, tax_rate, upkeep_cost, max_residents, max_chunks, is_open, is_public, board, permissions, flags, metadata)
                    ON target.uuid = source.uuid
                    WHEN MATCHED THEN
                        UPDATE SET
                            name = source.name,
                            mayor_uuid = source.mayor_uuid,
                            nation_uuid = source.nation_uuid,
                            spawn_world = source.spawn_world,
                            spawn_x = source.spawn_x,
                            spawn_y = source.spawn_y,
                            spawn_z = source.spawn_z,
                            spawn_yaw = source.spawn_yaw,
                            spawn_pitch = source.spawn_pitch,
                            balance = source.balance,
                            tax_rate = source.tax_rate,
                            upkeep_cost = source.upkeep_cost,
                            max_residents = source.max_residents,
                            max_chunks = source.max_chunks,
                            is_open = source.is_open,
                            is_public = source.is_public,
                            board = source.board,
                            permissions = source.permissions,
                            flags = source.flags,
                            metadata = source.metadata
                    WHEN NOT MATCHED THEN
                        INSERT (uuid, name, mayor_uuid, nation_uuid, spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, founded, balance, tax_rate, upkeep_cost, max_residents, max_chunks, is_open, is_public, board, permissions, flags, metadata)
                        VALUES (source.uuid, source.name, source.mayor_uuid, source.nation_uuid, source.spawn_world, source.spawn_x, source.spawn_y, source.spawn_z, source.spawn_yaw, source.spawn_pitch, source.founded, source.balance, source.tax_rate, source.upkeep_cost, source.max_residents, source.max_chunks, source.is_open, source.is_public, source.board, source.permissions, source.flags, source.metadata);
                    """.formatted(db.getTablePrefix());
            } else {
                sql = """
                    INSERT INTO %stowns (uuid, name, mayor_uuid, nation_uuid, spawn_world, spawn_x, spawn_y, spawn_z, 
                    spawn_yaw, spawn_pitch, founded, balance, tax_rate, upkeep_cost, max_residents, max_chunks, is_open, is_public, 
                    board, permissions, flags, metadata) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    name = VALUES(name), mayor_uuid = VALUES(mayor_uuid), nation_uuid = VALUES(nation_uuid),
                    spawn_world = VALUES(spawn_world), spawn_x = VALUES(spawn_x), spawn_y = VALUES(spawn_y),
                    spawn_z = VALUES(spawn_z), spawn_yaw = VALUES(spawn_yaw), spawn_pitch = VALUES(spawn_pitch),
                    balance = VALUES(balance), tax_rate = VALUES(tax_rate), upkeep_cost = VALUES(upkeep_cost),
                    max_residents = VALUES(max_residents), max_chunks = VALUES(max_chunks), is_open = VALUES(is_open), is_public = VALUES(is_public),
                    board = VALUES(board), permissions = VALUES(permissions), flags = VALUES(flags), metadata = VALUES(metadata)
                    """.formatted(db.getTablePrefix());
            }

            Location spawn = town.getSpawn();
            db.executeUpdate(sql,
                    town.getUuid().toString(),
                    town.getName(),
                    town.getMayorUuid().toString(),
                    town.getNationUuid() != null ? town.getNationUuid().toString() : null,
                    spawn != null ? spawn.getWorld().getName() : null,
                    spawn != null ? spawn.getX() : 0,
                    spawn != null ? spawn.getY() : 0,
                    spawn != null ? spawn.getZ() : 0,
                    spawn != null ? spawn.getYaw() : 0,
                    spawn != null ? spawn.getPitch() : 0,
                    town.getFounded(),
                    town.getBalance().doubleValue(),
                    town.getTaxRate().doubleValue(),
                    town.getUpkeepCost().doubleValue(),
                    town.getMaxResidents(),
                    town.getMaxChunks(),
                    town.isOpen(),
                    town.isPublic(),
                    town.getBoard(),
                    serializePermissions(town.getPermissions()),
                    serializeFlags(town.getFlags()),
                    serializeMetadata(town.getMetadata())
            );

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save town: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveAll() {
        for (Town town : townCache.values()) {
            saveTown(town);
        }
    }

    // Utility methods
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
        // TODO: Implement JSON serialization
        return "";
    }

    // Getters
    public Town getTown(UUID uuid) {
        return townCache.get(uuid);
    }

    public Town getTown(String name) {
        UUID uuid = townNameCache.get(name.toLowerCase());
        return uuid != null ? townCache.get(uuid) : null;
    }

    public Collection<Town> getAllTowns() {
        return townCache.values();
    }

    public boolean townExists(UUID uuid) {
        return townCache.containsKey(uuid);
    }

    public boolean renameTown(UUID townUuid, String newName) {
        if (townNameExists(newName)) return false;
        Town town = getTown(townUuid);
        if (town == null) return false;

        townNameCache.remove(town.getName().toLowerCase());
        town.setName(newName);
        townNameCache.put(newName.toLowerCase(), townUuid);
        saveTown(town);
        return true;
    }

    // For transferring mayorship
    public boolean transferMayorship(UUID townUuid, UUID newMayorUuid) {
        Town town = getTown(townUuid);
        if (town == null || !town.hasResident(newMayorUuid)) return false;

        town.setMayorUuid(newMayorUuid);
        saveTown(town);
        return true;
    }

    // For checking if player can create town
    public boolean canCreateTown(UUID playerUuid) {
        TownyPlayer player = plugin.getPlayerManager().getPlayer(playerUuid);
        return player != null && !player.hasTown();
    }

    public boolean townNameExists(String name) {
        return townNameCache.containsKey(name.toLowerCase());
    }

    public Town getTownByPlayer(UUID playerUuid) {
        TownyPlayer player = plugin.getPlayerManager().getPlayer(playerUuid);
        if (player != null && player.hasTown()) {
            return getTown(player.getTownUuid());
        }
        return null;
    }

    public List<Town> getTownsByNation(UUID nationUuid) {
        return townCache.values().stream()
                .filter(town -> town.hasNation() && town.getNationUuid().equals(nationUuid))
                .toList();
    }

    public int getTownCount() {
        return townCache.size();
    }
}

