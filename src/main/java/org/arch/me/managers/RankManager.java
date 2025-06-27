package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.database.DatabaseManager;
import org.arch.me.models.Rank;
import org.arch.me.models.TownyPlayer;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {

    private final EnhancedCoreH plugin;
    private final Map<Integer, Rank> rankCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> rankNameCache = new ConcurrentHashMap<>();

    public RankManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        loadAllRanks();
    }

    private void loadAllRanks() {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT * FROM %sranks ORDER BY priority DESC".formatted(db.getTablePrefix());

            List<Rank> ranks = db.queryList(sql, rs -> {
                Rank rank = new Rank(rs.getInt("id"), rs.getString("name"));

                rank.setPrefix(rs.getString("prefix"));
                rank.setSuffix(rs.getString("suffix"));
                rank.setPriority(rs.getInt("priority"));
                rank.setDefault(rs.getBoolean("is_default"));

                // Load permissions and metadata
                loadRankPermissions(rank, rs.getString("permissions"));
                loadRankMetadata(rank, rs.getString("metadata"));

                return rank;
            });

            for (Rank rank : ranks) {
                rankCache.put(rank.getId(), rank);
                rankNameCache.put(rank.getName().toLowerCase(), rank.getId());
            }

            plugin.getLogger().info("Loaded " + ranks.size() + " ranks from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load ranks: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadRankPermissions(Rank rank, String permissionsJson) {
        if (permissionsJson != null && !permissionsJson.isEmpty()) {
            Set<String> permissions = new HashSet<>(Arrays.asList(permissionsJson.split(",")));
            rank.setPermissions(permissions);
        }
    }

    private void loadRankMetadata(Rank rank, String metadataJson) {
        // TODO: Implement JSON parsing for metadata
    }

    public boolean rankNameExists(String name) {
        return rankNameCache.containsKey(name.toLowerCase());
    }

    public Rank getRank(int id) {
        return rankCache.get(id);
    }

    public Rank getDefaultRank() {
        return rankCache.values().stream()
                .filter(Rank::isDefault)
                .findFirst()
                .orElse(null);
    }

    public CompletableFuture<Rank> createRank(String name, String prefix, String suffix, int priority) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if rank name already exists
                if (rankNameExists(name)) {
                    return null;
                }

                // Generate new ID
                int newId = getNextRankId();

                Rank rank = new Rank(newId, name);
                rank.setPrefix(prefix);
                rank.setSuffix(suffix);
                rank.setPriority(priority);

                // Save to database
                saveRank(rank);

                // Add to cache
                rankCache.put(newId, rank);
                rankNameCache.put(name.toLowerCase(), newId);

                plugin.getLogger().info("Created new rank: " + name);
                return rank;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create rank: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> deleteRank(int rankId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Rank rank = getRank(rankId);
                if (rank == null || rank.isDefault()) {
                    return false;
                }

                // Move all players with this rank to default rank
                Rank defaultRank = getDefaultRank();
                if (defaultRank != null) {
                    DatabaseManager db = plugin.getDatabaseManager();
                    String updateSql = "UPDATE %splayers SET rank_id = ? WHERE rank_id = ?".formatted(db.getTablePrefix());
                    db.executeUpdate(updateSql, defaultRank.getId(), rankId);
                }

                // Delete from database
                DatabaseManager db = plugin.getDatabaseManager();
                String sql = "DELETE FROM %sranks WHERE id = ?".formatted(db.getTablePrefix());
                db.executeUpdate(sql, rankId);

                // Remove from cache
                rankCache.remove(rankId);
                rankNameCache.remove(rank.getName().toLowerCase());

                plugin.getLogger().info("Deleted rank: " + rank.getName());
                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to delete rank: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    public void saveRank(Rank rank) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();

            String sql = """
                INSERT INTO %sranks (id, name, prefix, suffix, permissions, priority, is_default, metadata) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                name = VALUES(name), prefix = VALUES(prefix), suffix = VALUES(suffix),
                permissions = VALUES(permissions), priority = VALUES(priority), is_default = VALUES(is_default),
                metadata = VALUES(metadata)
                """.formatted(db.getTablePrefix());

            db.executeUpdate(sql,
                    rank.getId(),
                    rank.getName(),
                    rank.getPrefix(),
                    rank.getSuffix(),
                    serializePermissions(rank.getPermissions()),
                    rank.getPriority(),
                    rank.isDefault(),
                    serializeMetadata(rank.getMetadata())
            );

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save rank: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean setPlayerRank(UUID playerUuid, int rankId) {
        TownyPlayer player = plugin.getPlayerManager().getPlayer(playerUuid);
        Rank rank = getRank(rankId);

        if (player == null || rank == null) {
            return false;
        }

        player.setRankId(rankId);
        plugin.getPlayerManager().savePlayer(player);

        return true;
    }

    public boolean setPlayerRank(UUID playerUuid, String rankName) {
        Integer rankId = rankNameCache.get(rankName.toLowerCase());
        if (rankId == null) {
            return false;
        }

        return setPlayerRank(playerUuid, rankId);
    }

    public Rank getPlayerRank(UUID playerUuid) {
        TownyPlayer player = plugin.getPlayerManager().getPlayer(playerUuid);
        if (player == null) {
            return getDefaultRank();
        }

        Rank rank = getRank(player.getRankId());
        return rank != null ? rank : getDefaultRank();
    }

    public boolean playerHasPermission(UUID playerUuid, String permission) {
        Rank rank = getPlayerRank(playerUuid);
        if (rank != null && rank.hasPermission(permission)) {
            return true;
        }

        // Check player-specific permissions
        TownyPlayer player = plugin.getPlayerManager().getPlayer(playerUuid);
        return player != null && player.hasPermission(permission);
    }

    private int getNextRankId() {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT MAX(id) FROM %sranks".formatted(db.getTablePrefix());
            Integer maxId = db.queryObject(sql, rs -> rs.getInt(1));
            return (maxId != null ? maxId : 0) + 1;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get next rank ID: " + e.getMessage());
            return 1;
        }
    }

    private String serializePermissions(Set<String> permissions) {
        return String.join(",", permissions);
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        return ""; // TODO: Implement JSON serialization
    }

    // Getters
    public Rank getRank(String name) {
        Integer id = rankNameCache.get(name.toLowerCase());
        return id != null ? rankCache.get(id) : null;
    }

    public Collection<Rank> getAllRanks() {
        return rankCache.values();
    }



    public List<Rank> getRanksByPriority() {
        return rankCache.values().stream()
                .sorted((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()))
                .toList();
    }

    public boolean rankExists(int id) {
        return rankCache.containsKey(id);
    }

    public int getRankCount() {
        return rankCache.size();
    }

}