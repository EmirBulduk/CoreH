package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Nation;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NationManager {
    private final EnhancedCoreH plugin;
    private final Map<UUID, Nation> nationCache;

    public NationManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        this.nationCache = new ConcurrentHashMap<>();
        loadAllNations();
    }

    private void loadAllNations() {
        try {
            String sql = "SELECT * FROM %snations".formatted(plugin.getDatabaseManager().getTablePrefix());

            List<Nation> nations = plugin.getDatabaseManager().queryList(sql, rs -> {
                Nation nation = createNationFromResultSet(rs);
                return nation;
            });

            for (Nation nation : nations) {
                nationCache.put(nation.getUuid(), nation);
            }

            plugin.getLogger().info("Loaded " + nations.size() + " nations from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load nations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public CompletableFuture<Nation> createNation(String name, UUID kingUuid, UUID capitalTownUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (nationNameExists(name)) {
                return null;
            }

            UUID nationUuid = UUID.randomUUID();

            // Get capital town and automatically select a capital chunk
            Town capitalTown = plugin.getTownManager().getTown(capitalTownUuid);
            UUID capitalChunkUuid = null;

            if (capitalTown != null && !capitalTown.getClaimedChunks().isEmpty()) {
                // Prioritize chunks near spawn, or pick randomly if no spawn set
                if (capitalTown.getSpawn() != null) {
                    capitalChunkUuid = findBestCapitalChunk(capitalTown);
                } else {
                    // Pick first available chunk if no spawn
                    capitalChunkUuid = capitalTown.getClaimedChunks().iterator().next().getUuid();
                }

                plugin.getLogger().info("Auto-selected capital chunk " + capitalChunkUuid + " for new nation " + name);
            }

            Nation nation = new Nation(nationUuid, name, kingUuid, capitalTownUuid, capitalChunkUuid);

            try {
                String sql = """
                    INSERT INTO %snations (uuid, name, king_uuid, capital_town_uuid, capital_chunk_uuid, 
                                           balance, tax_rate, max_towns, is_open, is_public, board, 
                                           permissions, flags, metadata, towns) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(plugin.getDatabaseManager().getTablePrefix());

                plugin.getDatabaseManager().executeUpdate(sql,
                    nation.getUuid().toString(),
                    nation.getName(),
                    nation.getKingUuid().toString(),
                    nation.getCapitalTownUuid() != null ? nation.getCapitalTownUuid().toString() : null,
                    nation.getCapitalChunkUuid() != null ? nation.getCapitalChunkUuid().toString() : null,
                    nation.getBalance(),
                    nation.getTaxRate(),
                    nation.getMaxTowns(),
                    nation.isOpen(),
                    nation.isPublic(),
                    nation.getBoard(),
                    plugin.getGson().toJson(nation.getPermissions()),
                    plugin.getGson().toJson(nation.getFlags()),
                    plugin.getGson().toJson(nation.getMetadata()),
                    plugin.getGson().toJson(nation.getTowns())
                );

                // Update town to belong to nation
                if (capitalTown != null) {
                    capitalTown.setNationUuid(nationUuid);
                    plugin.getTownManager().saveTown(capitalTown);
                }

                // Update player to be in nation
                TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(kingUuid);
                if (townyPlayer != null) {
                    townyPlayer.setNationUuid(nationUuid);
                    plugin.getPlayerManager().savePlayer(townyPlayer);
                }

                // Update war manager if needed
                if (plugin.getWarManager() != null) {
                    plugin.getWarManager().handleTownNationChange(capitalTownUuid, null, nationUuid);
                }

                nationCache.put(nationUuid, nation);
                return nation;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create nation: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    /**
     * Find the best capital chunk - prioritizes chunks near town spawn
     */
    private UUID findBestCapitalChunk(Town town) {
        if (town.getSpawn() == null || town.getClaimedChunks().isEmpty()) {
            return town.getClaimedChunks().iterator().next().getUuid();
        }

        int spawnChunkX = town.getSpawn().getChunk().getX();
        int spawnChunkZ = town.getSpawn().getChunk().getZ();
        String spawnWorld = town.getSpawn().getWorld().getName();

        // Find chunk closest to spawn
        UUID bestChunk = null;
        double closestDistance = Double.MAX_VALUE;

        for (org.arch.me.models.ClaimedChunk chunk : town.getClaimedChunks()) {
            if (!chunk.getWorldName().equals(spawnWorld)) continue;

            double distance = Math.sqrt(
                Math.pow(chunk.getX() - spawnChunkX, 2) +
                Math.pow(chunk.getZ() - spawnChunkZ, 2)
            );

            if (distance < closestDistance) {
                closestDistance = distance;
                bestChunk = chunk.getUuid();
            }
        }

        return bestChunk != null ? bestChunk : town.getClaimedChunks().iterator().next().getUuid();
    }

    /**
     * Change nation capital chunk - expensive operation only for kings
     */
    public CompletableFuture<Boolean> setCapitalChunk(UUID nationUuid, UUID kingUuid, UUID newCapitalChunkUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Nation nation = getNation(nationUuid);
                if (nation == null || !nation.isKing(kingUuid)) {
                    return false;
                }

                // Check if the chunk belongs to the capital town
                Town capitalTown = plugin.getTownManager().getTown(nation.getCapitalTownUuid());
                if (capitalTown == null) {
                    return false;
                }

                boolean chunkBelongsToCapital = capitalTown.getClaimedChunks().stream()
                    .anyMatch(chunk -> chunk.getUuid().equals(newCapitalChunkUuid));

                if (!chunkBelongsToCapital) {
                    return false;
                }

                // Calculate expensive cost for changing capital chunk
                java.math.BigDecimal cost = java.math.BigDecimal.valueOf(
                    plugin.getConfigManager().getEconomyValue("capital-chunk-change-cost")
                );

                // Check if nation has enough funds
                if (!plugin.getEconomyManager().hasNationBalance(nationUuid, cost)) {
                    plugin.getLogger().info("Nation " + nation.getName() + " lacks funds to change capital chunk. Required: " + cost);
                    return false;
                }

                // Withdraw cost
                if (!plugin.getEconomyManager().withdrawNation(nationUuid, cost)) {
                    return false;
                }

                // Update capital chunk
                nation.setCapitalChunkUuid(newCapitalChunkUuid);
                saveNation(nation);

                plugin.getLogger().info("Nation " + nation.getName() + " changed capital chunk to " + newCapitalChunkUuid + " for cost " + cost);
                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to set capital chunk: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Boolean> deleteNation(UUID nationUuid, boolean force) {
        return CompletableFuture.supplyAsync(() -> {
            Nation nation = getNation(nationUuid);
            if (nation == null) return false;

            try {
                // Remove all towns from nation
                for (UUID townUuid : new HashSet<>(nation.getTowns())) {
                    removeTownFromNationSync(nationUuid, townUuid);
                }

                String sql = "DELETE FROM %snations WHERE uuid = ?".formatted(plugin.getDatabaseManager().getTablePrefix());
                plugin.getDatabaseManager().executeUpdate(sql, nationUuid.toString());

                nationCache.remove(nationUuid);
                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete nation: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Boolean> addTownToNation(UUID nationUuid, UUID townUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return addTownToNationSync(nationUuid, townUuid);
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    private boolean addTownToNationSync(UUID nationUuid, UUID townUuid) {
        Nation nation = getNation(nationUuid);
        Town town = plugin.getTownManager().getTown(townUuid);

        if (nation == null || town == null || town.hasNation()) {
            return false;
        }

        if (!nation.canAddTown()) {
            return false;
        }

        nation.addTown(townUuid);
        town.setNationUuid(nationUuid);

        saveNation(nation);
        plugin.getTownManager().saveTown(town);

        // Update all town residents to be in nation
        for (UUID residentUuid : town.getResidents()) {
            TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(residentUuid);
            if (townyPlayer != null) {
                townyPlayer.setNationUuid(nationUuid);
                plugin.getPlayerManager().savePlayer(townyPlayer);
            }
        }

        // Update war manager
        if (plugin.getWarManager() != null) {
            plugin.getWarManager().handleTownNationChange(townUuid, null, nationUuid);
        }

        return true;
    }

    public CompletableFuture<Boolean> removeTownFromNation(UUID nationUuid, UUID townUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return removeTownFromNationSync(nationUuid, townUuid);
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    private boolean removeTownFromNationSync(UUID nationUuid, UUID townUuid) {
        Nation nation = getNation(nationUuid);
        Town town = plugin.getTownManager().getTown(townUuid);

        if (nation == null || town == null || !nation.hasTown(townUuid)) {
            return false;
        }

        // Can't remove capital town unless it's the last town
        if (nation.isCapitalTown(townUuid) && nation.getTownCount() > 1) {
            return false;
        }

        nation.removeTown(townUuid);
        town.setNationUuid(null);

        saveNation(nation);
        plugin.getTownManager().saveTown(town);

        // Update all town residents to not be in nation
        for (UUID residentUuid : town.getResidents()) {
            TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(residentUuid);
            if (townyPlayer != null) {
                townyPlayer.setNationUuid(null);
                plugin.getPlayerManager().savePlayer(townyPlayer);
            }
        }

        // Update war manager
        if (plugin.getWarManager() != null) {
            plugin.getWarManager().handleTownNationChange(townUuid, nationUuid, null);
        }

        return true;
    }

    public CompletableFuture<Boolean> setNationKing(UUID nationUuid, UUID newKingUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Nation nation = getNation(nationUuid);
            if (nation == null) return false;

            // Check if new king is a mayor in the nation
            TownyPlayer newKing = plugin.getPlayerManager().getPlayer(newKingUuid);
            if (newKing == null || !newKing.hasNation() || !newKing.getNationUuid().equals(nationUuid)) {
                return false;
            }

            Town newKingTown = plugin.getTownManager().getTown(newKing.getTownUuid());
            if (newKingTown == null || !newKingTown.isMayor(newKingUuid)) {
                return false;
            }

            nation.setKingUuid(newKingUuid);
            saveNation(nation);
            return true;
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Boolean> setNationCapital(UUID nationUuid, UUID newCapitalUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Nation nation = getNation(nationUuid);
            if (nation == null || !nation.hasTown(newCapitalUuid)) {
                return false;
            }

            Town newCapital = plugin.getTownManager().getTown(newCapitalUuid);
            if (newCapital == null) return false;

            nation.setCapitalTownUuid(newCapitalUuid);

            // Set capital chunk to first chunk of new capital
            if (!newCapital.getClaimedChunks().isEmpty()) {
                UUID capitalChunkUuid = newCapital.getClaimedChunks().iterator().next().getUuid();
                nation.setCapitalChunkUuid(capitalChunkUuid);
            }

            saveNation(nation);
            return true;
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public void saveNation(Nation nation) {
        if (nation == null) return;

        try {
            String sql = """
                UPDATE %snations SET name = ?, king_uuid = ?, capital_town_uuid = ?, capital_chunk_uuid = ?,
                                 balance = ?, tax_rate = ?, max_towns = ?, is_open = ?, is_public = ?, 
                                 board = ?, permissions = ?, flags = ?, metadata = ?, towns = ?
                WHERE uuid = ?
            """.formatted(plugin.getDatabaseManager().getTablePrefix());

            plugin.getDatabaseManager().executeUpdate(sql,
                nation.getName(),
                nation.getKingUuid().toString(),
                nation.getCapitalTownUuid() != null ? nation.getCapitalTownUuid().toString() : null,
                nation.getCapitalChunkUuid() != null ? nation.getCapitalChunkUuid().toString() : null,
                nation.getBalance(),
                nation.getTaxRate(),
                nation.getMaxTowns(),
                nation.isOpen(),
                nation.isPublic(),
                nation.getBoard(),
                plugin.getGson().toJson(nation.getPermissions()),
                plugin.getGson().toJson(nation.getFlags()),
                plugin.getGson().toJson(nation.getMetadata()),
                plugin.getGson().toJson(nation.getTowns()),
                nation.getUuid().toString()
            );

            nationCache.put(nation.getUuid(), nation);

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save nation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int getNationCount() {
        return nationCache.size();
    }

    public Nation getNation(UUID uuid) {
        if (uuid == null) return null;

        Nation cached = nationCache.get(uuid);
        if (cached != null) return cached;

        try {
            String sql = "SELECT * FROM %snations WHERE uuid = ?".formatted(plugin.getDatabaseManager().getTablePrefix());
            Nation nation = plugin.getDatabaseManager().queryObject(sql, this::createNationFromResultSet, uuid.toString());

            if (nation != null) {
                nationCache.put(uuid, nation);
            }
            return nation;

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public Nation getNation(String name) {
        if (name == null) return null;

        // Check cache first
        for (Nation nation : nationCache.values()) {
            if (nation.getName().equalsIgnoreCase(name)) {
                return nation;
            }
        }

        try {
            String sql = "SELECT * FROM %snations WHERE LOWER(name) = LOWER(?)".formatted(plugin.getDatabaseManager().getTablePrefix());
            Nation nation = plugin.getDatabaseManager().queryObject(sql, this::createNationFromResultSet, name);

            if (nation != null) {
                nationCache.put(nation.getUuid(), nation);
            }
            return nation;

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation by name: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean nationNameExists(String name) {
        return getNation(name) != null;
    }

    public Collection<Nation> getAllNations() {
        if (!nationCache.isEmpty()) {
            return new ArrayList<>(nationCache.values());
        }

        try {
            String sql = "SELECT * FROM %snations".formatted(plugin.getDatabaseManager().getTablePrefix());
            List<Nation> nations = plugin.getDatabaseManager().queryList(sql, this::createNationFromResultSet);

            for (Nation nation : nations) {
                nationCache.put(nation.getUuid(), nation);
            }

            return nations;

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all nations: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private Nation createNationFromResultSet(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String name = rs.getString("name");
        UUID kingUuid = UUID.fromString(rs.getString("king_uuid"));

        String capitalTownUuidStr = rs.getString("capital_town_uuid");
        UUID capitalTownUuid = capitalTownUuidStr != null ? UUID.fromString(capitalTownUuidStr) : null;

        String capitalChunkUuidStr = rs.getString("capital_chunk_uuid");
        UUID capitalChunkUuid = capitalChunkUuidStr != null ? UUID.fromString(capitalChunkUuidStr) : null;

        Nation nation = new Nation(uuid, name, kingUuid, capitalTownUuid, capitalChunkUuid);

        nation.setFounded(rs.getTimestamp("founded"));
        nation.setBalance(rs.getBigDecimal("balance"));
        nation.setTaxRate(rs.getBigDecimal("tax_rate"));
        nation.setMaxTowns(rs.getInt("max_towns"));
        nation.setOpen(rs.getBoolean("is_open"));
        nation.setPublic(rs.getBoolean("is_public"));
        nation.setBoard(rs.getString("board"));

        // Deserialize JSON fields
        String permissionsJson = rs.getString("permissions");
        if (permissionsJson != null && !permissionsJson.isEmpty()) {
            try {
                Set<String> permissions = plugin.getGson().fromJson(permissionsJson, Set.class);
                nation.setPermissions(permissions != null ? permissions : new HashSet<>());
            } catch (Exception e) {
                nation.setPermissions(new HashSet<>());
            }
        }

        String flagsJson = rs.getString("flags");
        if (flagsJson != null && !flagsJson.isEmpty()) {
            try {
                Map<String, Boolean> flags = plugin.getGson().fromJson(flagsJson, Map.class);
                nation.setFlags(flags != null ? flags : new HashMap<>());
            } catch (Exception e) {
                nation.setFlags(new HashMap<>());
            }
        }

        String metadataJson = rs.getString("metadata");
        if (metadataJson != null && !metadataJson.isEmpty()) {
            try {
                Map<String, Object> metadata = plugin.getGson().fromJson(metadataJson, Map.class);
                nation.setMetadata(metadata != null ? metadata : new HashMap<>());
            } catch (Exception e) {
                nation.setMetadata(new HashMap<>());
            }
        }

        String townsJson = rs.getString("towns");
        if (townsJson != null && !townsJson.isEmpty()) {
            try {
                Set<String> townStrings = plugin.getGson().fromJson(townsJson, Set.class);
                if (townStrings != null) {
                    Set<UUID> townUuids = new HashSet<>();
                    for (Object townObj : townStrings) {
                        try {
                            townUuids.add(UUID.fromString(townObj.toString()));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid town UUID in nation " + name + ": " + townObj);
                        }
                    }
                    nation.setTowns(townUuids);
                }
            } catch (Exception e) {
                nation.setTowns(new HashSet<>());
            }
        }

        return nation;
    }

    public void saveAll() {
        for (Nation nation : nationCache.values()) {
            saveNation(nation);
        }
    }

    public void clearCache() {
        nationCache.clear();
    }

    public void removeFromCache(UUID uuid) {
        nationCache.remove(uuid);
    }
}

