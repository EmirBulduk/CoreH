package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.database.DatabaseManager;
import org.arch.me.models.Nation;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NationManager {

    private final EnhancedCoreH plugin;
    private final Map<UUID, Nation> nationCache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nationNameCache = new ConcurrentHashMap<>();

    public NationManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        loadAllNations();
    }

    private void loadAllNations() {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT * FROM %snations".formatted(db.getTablePrefix());

            List<Nation> nations = db.queryList(sql, rs -> {
                Nation nation = new Nation(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        UUID.fromString(rs.getString("king_uuid")),
                        UUID.fromString(rs.getString("capital_uuid"))
                );

                nation.setFounded(rs.getTimestamp("founded"));
                nation.setBalance(BigDecimal.valueOf(rs.getDouble("balance")));
                nation.setTaxRate(BigDecimal.valueOf(rs.getDouble("tax_rate")));
                nation.setMaxTowns(rs.getInt("max_towns"));
                nation.setOpen(rs.getBoolean("is_open"));
                nation.setPublic(rs.getBoolean("is_public"));
                nation.setBoard(rs.getString("board"));

                // Load permissions, flags, and metadata
                loadNationPermissions(nation, rs.getString("permissions"));
                loadNationFlags(nation, rs.getString("flags"));
                loadNationMetadata(nation, rs.getString("metadata"));

                return nation;
            });

            for (Nation nation : nations) {
                nationCache.put(nation.getUuid(), nation);
                nationNameCache.put(nation.getName().toLowerCase(), nation.getUuid());

                // Load member towns
                loadNationTowns(nation);
            }

            plugin.getLogger().info("Loaded " + nations.size() + " nations from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load nations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadNationTowns(Nation nation) throws SQLException {
        DatabaseManager db = plugin.getDatabaseManager();
        String sql = "SELECT uuid FROM %stowns WHERE nation_uuid = ?".formatted(db.getTablePrefix());

        List<UUID> towns = db.queryList(sql, rs -> UUID.fromString(rs.getString("uuid")), nation.getUuid().toString());
        nation.setTowns(new HashSet<>(towns));
    }

    private void loadNationPermissions(Nation nation, String permissionsJson) {
        if (permissionsJson != null && !permissionsJson.isEmpty()) {
            Set<String> permissions = new HashSet<>(Arrays.asList(permissionsJson.split(",")));
            nation.setPermissions(permissions);
        }
    }

    private void loadNationFlags(Nation nation, String flagsJson) {
        if (flagsJson != null && !flagsJson.isEmpty()) {
            Map<String, Boolean> flags = new HashMap<>();
            String[] flagPairs = flagsJson.split(",");
            for (String pair : flagPairs) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    flags.put(parts[0], Boolean.parseBoolean(parts[1]));
                }
            }
            nation.setFlags(flags);
        }
    }

    private void loadNationMetadata(Nation nation, String metadataJson) {
        // TODO: Implement JSON parsing for metadata
    }

    // Nation creation
    public CompletableFuture<Nation> createNation(String name, UUID kingUuid, UUID capitalTownUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if nation name already exists
                if (nationNameExists(name)) {
                    return null;
                }

                // Check if capital town exists and is valid
                Town capitalTown = plugin.getTownManager().getTown(capitalTownUuid);
                if (capitalTown == null || capitalTown.hasNation()) {
                    return null;
                }

                // Check if king is mayor of capital town
                if (!capitalTown.isMayor(kingUuid)) {
                    return null;
                }

                // Check economy requirements
                BigDecimal cost = BigDecimal.valueOf(plugin.getConfigManager().getEconomyValue("nation-creation-cost"));
                if (!plugin.getEconomyManager().hasTownBalance(capitalTownUuid, cost)) {
                    return null;
                }

                // Create nation
                UUID nationUuid = UUID.randomUUID();
                Nation nation = new Nation(nationUuid, name, kingUuid, capitalTownUuid);

                // Withdraw money from capital town
                plugin.getEconomyManager().withdrawTown(capitalTownUuid, cost);

                // Update capital town
                capitalTown.setNationUuid(nationUuid);
                plugin.getTownManager().saveTown(capitalTown);

                // Update all residents of capital town
                for (UUID residentUuid : capitalTown.getResidents()) {
                    TownyPlayer resident = plugin.getPlayerManager().getPlayer(residentUuid);
                    if (resident != null) {
                        resident.setNationUuid(nationUuid);
                        plugin.getPlayerManager().savePlayer(resident);
                    }
                }

                // Save to database
                saveNation(nation);

                // Add to cache
                nationCache.put(nationUuid, nation);
                nationNameCache.put(name.toLowerCase(), nationUuid);

                plugin.getLogger().info("Created nation: " + name + " with king: " + kingUuid + " and capital: " + capitalTown.getName());
                return nation;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create nation: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    // Nation deletion
    public CompletableFuture<Boolean> deleteNation(UUID nationUuid, boolean force) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Nation nation = getNation(nationUuid);
                if (nation == null) {
                    return false;
                }

                // Update all member towns
                for (UUID townUuid : nation.getTowns()) {
                    Town town = plugin.getTownManager().getTown(townUuid);
                    if (town != null) {
                        town.setNationUuid(null);
                        plugin.getTownManager().saveTown(town);

                        // Update all residents
                        for (UUID residentUuid : town.getResidents()) {
                            TownyPlayer resident = plugin.getPlayerManager().getPlayer(residentUuid);
                            if (resident != null) {
                                resident.setNationUuid(null);
                                plugin.getPlayerManager().savePlayer(resident);
                            }
                        }
                    }
                }

                // Delete from database
                DatabaseManager db = plugin.getDatabaseManager();
                String sql = "DELETE FROM %snations WHERE uuid = ?".formatted(db.getTablePrefix());
                db.executeUpdate(sql, nationUuid.toString());

                // Remove from cache
                nationCache.remove(nationUuid);
                nationNameCache.remove(nation.getName().toLowerCase());

                plugin.getLogger().info("Deleted nation: " + nation.getName());
                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to delete nation: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // Town joining nation
    public CompletableFuture<Boolean> addTownToNation(UUID nationUuid, UUID townUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Nation nation = getNation(nationUuid);
                Town town = plugin.getTownManager().getTown(townUuid);

                if (nation == null || town == null) {
                    return false;
                }

                // Check if nation is open or town has invitation
                if (!nation.isOpen()) {
                    // TODO: Implement invitation system
                    return false;
                }

                // Check if nation can accept more towns
                if (!nation.canAddTown()) {
                    return false;
                }

                // Check if town is already in a nation
                if (town.hasNation()) {
                    return false;
                }

                // Add town to nation
                nation.addTown(townUuid);
                town.setNationUuid(nationUuid);

                // Update all residents
                for (UUID residentUuid : town.getResidents()) {
                    TownyPlayer resident = plugin.getPlayerManager().getPlayer(residentUuid);
                    if (resident != null) {
                        resident.setNationUuid(nationUuid);
                        plugin.getPlayerManager().savePlayer(resident);
                    }
                }

                // Save changes
                saveNation(nation);
                plugin.getTownManager().saveTown(town);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to add town to nation: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // Town leaving nation
    public CompletableFuture<Boolean> removeTownFromNation(UUID nationUuid, UUID townUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Nation nation = getNation(nationUuid);
                Town town = plugin.getTownManager().getTown(townUuid);

                if (nation == null || town == null) {
                    return false;
                }

                // Check if it's the capital town
                if (nation.getCapitalUuid().equals(townUuid)) {
                    // Capital cannot leave, must dissolve nation or transfer capital
                    if (nation.getTownCount() > 1) {
                        return false; // Cannot leave as capital with other towns
                    }
                    // If only capital town, dissolve the nation
                    deleteNation(nationUuid, false);
                    return true;
                }

                // Remove town from nation
                nation.removeTown(townUuid);
                town.setNationUuid(null);

                // Update all residents
                for (UUID residentUuid : town.getResidents()) {
                    TownyPlayer resident = plugin.getPlayerManager().getPlayer(residentUuid);
                    if (resident != null) {
                        resident.setNationUuid(null);
                        plugin.getPlayerManager().savePlayer(resident);
                    }
                }

                // Save changes
                saveNation(nation);
                plugin.getTownManager().saveTown(town);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to remove town from nation: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // Nation management methods
    public CompletableFuture<Boolean> setNationKing(UUID nationUuid, UUID newKingUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Nation nation = getNation(nationUuid);
                if (nation == null) {
                    return false;
                }

                // Check if new king is a mayor in the nation
                TownyPlayer newKing = plugin.getPlayerManager().getPlayer(newKingUuid);
                if (newKing == null || !newKing.hasNation() || !newKing.getNationUuid().equals(nationUuid)) {
                    return false;
                }

                Town newKingTown = plugin.getTownManager().getTown(newKing.getTownUuid());
                if (newKingTown == null || !newKingTown.isMayor(newKingUuid)) {
                    return false;
                }

                // Set new king
                nation.setKingUuid(newKingUuid);

                // Save changes
                saveNation(nation);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to set nation king: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> setNationCapital(UUID nationUuid, UUID newCapitalUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Nation nation = getNation(nationUuid);
                Town newCapital = plugin.getTownManager().getTown(newCapitalUuid);

                if (nation == null || newCapital == null) {
                    return false;
                }

                // Check if town is in the nation
                if (!nation.hasTown(newCapitalUuid)) {
                    return false;
                }

                // Set new capital
                nation.setCapitalUuid(newCapitalUuid);

                // Save changes
                saveNation(nation);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to set nation capital: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    public void saveNation(Nation nation) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();

            String sql;
            if (db.isSQLServer()) {
                sql = """
                    MERGE INTO %snations AS target
                    USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)) AS source 
                    (uuid, name, king_uuid, capital_uuid, founded, balance, tax_rate, max_towns, is_open, is_public, board, permissions, flags, metadata)
                    ON target.uuid = source.uuid
                    WHEN MATCHED THEN
                        UPDATE SET
                            name = source.name,
                            king_uuid = source.king_uuid,
                            capital_uuid = source.capital_uuid,
                            balance = source.balance,
                            tax_rate = source.tax_rate,
                            max_towns = source.max_towns,
                            is_open = source.is_open,
                            is_public = source.is_public,
                            board = source.board,
                            permissions = source.permissions,
                            flags = source.flags,
                            metadata = source.metadata
                    WHEN NOT MATCHED THEN
                        INSERT (uuid, name, king_uuid, capital_uuid, founded, balance, tax_rate, max_towns, is_open, is_public, board, permissions, flags, metadata)
                        VALUES (source.uuid, source.name, source.king_uuid, source.capital_uuid, source.founded, source.balance, source.tax_rate, source.max_towns, source.is_open, source.is_public, source.board, source.permissions, source.flags, source.metadata);
                    """.formatted(db.getTablePrefix());
            } else {
                sql = """
                    INSERT INTO %snations (uuid, name, king_uuid, capital_uuid, founded, balance, tax_rate, 
                    max_towns, is_open, is_public, board, permissions, flags, metadata) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    name = VALUES(name), king_uuid = VALUES(king_uuid), capital_uuid = VALUES(capital_uuid),
                    balance = VALUES(balance), tax_rate = VALUES(tax_rate), max_towns = VALUES(max_towns),
                    is_open = VALUES(is_open), is_public = VALUES(is_public), board = VALUES(board),
                    permissions = VALUES(permissions), flags = VALUES(flags), metadata = VALUES(metadata)
                    """.formatted(db.getTablePrefix());
            }

            db.executeUpdate(sql,
                    nation.getUuid().toString(),
                    nation.getName(),
                    nation.getKingUuid().toString(),
                    nation.getCapitalUuid().toString(),
                    nation.getFounded(),
                    nation.getBalance().doubleValue(),
                    nation.getTaxRate().doubleValue(),
                    nation.getMaxTowns(),
                    nation.isOpen(),
                    nation.isPublic(),
                    nation.getBoard(),
                    serializePermissions(nation.getPermissions()),
                    serializeFlags(nation.getFlags()),
                    serializeMetadata(nation.getMetadata())
            );

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save nation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveAll() {
        for (Nation nation : nationCache.values()) {
            saveNation(nation);
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
        return ""; // TODO: Implement JSON serialization
    }

    // Getters
    public Nation getNation(UUID uuid) {
        return nationCache.get(uuid);
    }

    public Nation getNation(String name) {
        UUID uuid = nationNameCache.get(name.toLowerCase());
        return uuid != null ? nationCache.get(uuid) : null;
    }

    public Collection<Nation> getAllNations() {
        return nationCache.values();
    }

    public boolean nationExists(UUID uuid) {
        return nationCache.containsKey(uuid);
    }

    public boolean nationNameExists(String name) {
        return nationNameCache.containsKey(name.toLowerCase());
    }

    public Nation getNationByPlayer(UUID playerUuid) {
        TownyPlayer player = plugin.getPlayerManager().getPlayer(playerUuid);
        if (player != null && player.hasNation()) {
            return getNation(player.getNationUuid());
        }
        return null;
    }

    public Nation getNationByTown(UUID townUuid) {
        Town town = plugin.getTownManager().getTown(townUuid);
        if (town != null && town.hasNation()) {
            return getNation(town.getNationUuid());
        }
        return null;
    }

    public int getNationCount() {
        return nationCache.size();
    }
}

