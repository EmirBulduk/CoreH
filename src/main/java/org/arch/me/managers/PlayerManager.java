package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.database.DatabaseManager;
import org.arch.me.models.Rank;
import org.arch.me.models.TownyPlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// PlayerManager
public class PlayerManager {

    private final EnhancedCoreH plugin;
    private final Map<UUID, TownyPlayer> playerCache = new ConcurrentHashMap<>();

    public PlayerManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    public TownyPlayer getPlayer(UUID uuid) {
        return playerCache.computeIfAbsent(uuid, this::loadPlayer);
    }

    public TownyPlayer getPlayer(String name) {
        return playerCache.values().stream()
                .filter(player -> player.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private TownyPlayer loadPlayer(UUID uuid) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT * FROM %splayers WHERE uuid = ?".formatted(db.getTablePrefix());

            return db.queryObject(sql, rs -> {
                TownyPlayer player = new TownyPlayer(uuid, rs.getString("name"));

                String townUuidStr = rs.getString("town_uuid");
                if (townUuidStr != null) {
                    player.setTownUuid(UUID.fromString(townUuidStr));
                }

                String nationUuidStr = rs.getString("nation_uuid");
                if (nationUuidStr != null) {
                    player.setNationUuid(UUID.fromString(nationUuidStr));
                }

                player.setRankId(rs.getInt("rank_id"));
                player.setBalance(BigDecimal.valueOf(rs.getDouble("balance")));
                player.setLastOnline(rs.getTimestamp("last_online"));
                player.setJoinedTown(rs.getTimestamp("joined_town"));

                // Load permissions and metadata
                loadPlayerPermissions(player, rs.getString("permissions"));
                loadPlayerMetadata(player, rs.getString("metadata"));

                return player;
            }, uuid.toString());

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player: " + e.getMessage());
            return null;
        }
    }

    private void loadPlayerPermissions(TownyPlayer player, String permissionsJson) {
        if (permissionsJson != null && !permissionsJson.isEmpty()) {
            Set<String> permissions = new HashSet<>(Arrays.asList(permissionsJson.split(",")));
            player.setPermissions(permissions);
        }
    }

    private void loadPlayerMetadata(TownyPlayer player, String metadataJson) {
        // TODO: Implement JSON parsing for metadata
    }

    public CompletableFuture<Void> createPlayer(Player bukkitPlayer) {
        return CompletableFuture.runAsync(() -> {
            try {
                UUID uuid = bukkitPlayer.getUniqueId();
                String name = bukkitPlayer.getName();

                // Check if player already exists
                if (playerExists(uuid)) {
                    // Update name if changed and load into cache
                    TownyPlayer existing = loadPlayer(uuid);
                    if (existing != null) {
                        if (!existing.getName().equals(name)) {
                            existing.setName(name);
                            savePlayer(existing);
                        }
                        playerCache.put(uuid, existing);
                    }
                    return;
                }

                TownyPlayer player = new TownyPlayer(uuid, name);
                player.setLastOnline(new Timestamp(System.currentTimeMillis()));
                player.setBalance(BigDecimal.valueOf(plugin.getConfigManager().getEconomyValue("starting-money")));

                // Set default rank
                Rank defaultRank = plugin.getRankManager().getDefaultRank();
                if (defaultRank != null) {
                    player.setRankId(defaultRank.getId());
                } else {
                    player.setRankId(1); // Fallback rank ID
                }

                // Save to database first
                savePlayer(player);

                // Add to cache after successful save
                playerCache.put(uuid, player);

                plugin.getLogger().info("Created new player: " + name + " (" + uuid + ")");

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create player: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void savePlayer(TownyPlayer player) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();

            String sql;
            if (db.isSQLServer()) {
                sql = """
                    MERGE INTO %splayers AS target
                    USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)) AS source (uuid, name, town_uuid, nation_uuid, rank_id, balance, last_online, joined_town, permissions, metadata)
                    ON target.uuid = source.uuid
                    WHEN MATCHED THEN
                        UPDATE SET
                            name = source.name,
                            town_uuid = source.town_uuid,
                            nation_uuid = source.nation_uuid,
                            rank_id = source.rank_id,
                            balance = source.balance,
                            last_online = source.last_online,
                            joined_town = source.joined_town,
                            permissions = source.permissions,
                            metadata = source.metadata
                    WHEN NOT MATCHED THEN
                        INSERT (uuid, name, town_uuid, nation_uuid, rank_id, balance, last_online, joined_town, permissions, metadata)
                        VALUES (source.uuid, source.name, source.town_uuid, source.nation_uuid, source.rank_id, source.balance, source.last_online, source.joined_town, source.permissions, source.metadata);
                    """.formatted(db.getTablePrefix());
            } else {
                sql = """
                    INSERT INTO %splayers (uuid, name, town_uuid, nation_uuid, rank_id, balance, last_online, 
                    joined_town, permissions, metadata) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    name = VALUES(name), town_uuid = VALUES(town_uuid), nation_uuid = VALUES(nation_uuid),
                    rank_id = VALUES(rank_id), balance = VALUES(balance), last_online = VALUES(last_online),
                    joined_town = VALUES(joined_town), permissions = VALUES(permissions), metadata = VALUES(metadata)
                    """.formatted(db.getTablePrefix());
            }


            db.executeUpdate(sql,
                    player.getUuid().toString(),
                    player.getName(),
                    player.getTownUuid() != null ? player.getTownUuid().toString() : null,
                    player.getNationUuid() != null ? player.getNationUuid().toString() : null,
                    player.getRankId(),
                    player.getBalance().doubleValue(),
                    player.getLastOnline(),
                    player.getJoinedTown(),
                    serializePermissions(player.getPermissions()),
                    serializeMetadata(player.getMetadata())
            );

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveAll() {
        for (TownyPlayer player : playerCache.values()) {
            savePlayer(player);
        }
    }

    public boolean playerExists(UUID uuid) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT COUNT(*) FROM %splayers WHERE uuid = ?".formatted(db.getTablePrefix());
            return db.queryInt(sql, uuid.toString()) > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check if player exists: " + e.getMessage());
            return false;
        }
    }

    public void updateLastOnline(UUID uuid) {
        TownyPlayer player = getPlayer(uuid);
        if (player != null) {
            player.setLastOnline(new Timestamp(System.currentTimeMillis()));
            savePlayer(player);
        }
    }

    public Collection<TownyPlayer> getAllPlayers() {
        return playerCache.values();
    }

    private String serializePermissions(Set<String> permissions) {
        return String.join(",", permissions);
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        return ""; // TODO: Implement JSON serialization
    }


}


// RankManager
