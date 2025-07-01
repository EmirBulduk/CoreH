package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Rank;
import org.bukkit.Bukkit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {
    private final EnhancedCoreH plugin;
    private final Map<UUID, Rank> rankCache;
    private final Map<String, UUID> rankNameCache; // name -> uuid
    private final Map<UUID, UUID> playerRanks; // playerUuid -> rankUuid

    public RankManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        this.rankCache = new ConcurrentHashMap<>();
        this.rankNameCache = new ConcurrentHashMap<>();
        this.playerRanks = new ConcurrentHashMap<>();
        initializeDefaultRanks();
        loadAllRanks();
    }

    private void initializeDefaultRanks() {
        try {
            // Create default town ranks
            createDefaultRank("resident", "TOWN", 0, Arrays.asList("towny.town.resident"));
            createDefaultRank("manager", "TOWN", 50, Arrays.asList("towny.town.resident", "towny.town.claim", "towny.town.unclaim", "towny.town.invite", "towny.town.kick", "towny.town.set.flags"));
            createDefaultRank("assistant", "TOWN", 75, Arrays.asList("towny.town.resident", "towny.town.claim", "towny.town.unclaim", "towny.town.invite", "towny.town.kick", "towny.town.set.*", "towny.town.toggle.*"));
            
            // Create default nation ranks
            createDefaultRank("citizen", "NATION", 0, Arrays.asList("towny.nation.citizen"));
            createDefaultRank("advisor", "NATION", 50, Arrays.asList("towny.nation.citizen", "towny.nation.invite", "towny.nation.kick", "towny.nation.ally", "towny.nation.enemy"));
            createDefaultRank("minister", "NATION", 75, Arrays.asList("towny.nation.citizen", "towny.nation.invite", "towny.nation.kick", "towny.nation.ally", "towny.nation.enemy", "towny.nation.set.*", "towny.nation.toggle.*"));
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize default ranks: " + e.getMessage());
        }
    }

    private void createDefaultRank(String name, String type, int priority, List<String> permissions) {
        UUID rankUuid = UUID.randomUUID();
        Rank rank = new Rank(rankUuid, name, type);
        rank.setPriority(priority);
        rank.setPermissions(new HashSet<>(permissions));
        rank.setDefault(name.equals("resident") || name.equals("citizen"));

        saveRank(rank);
        rankCache.put(rankUuid, rank);
        rankNameCache.put(name.toLowerCase(), rankUuid);
    }

    private void loadAllRanks() {
        try {
            String sql = "SELECT * FROM %sranks".formatted(plugin.getDatabaseManager().getTablePrefix());

            List<Rank> ranks = plugin.getDatabaseManager().queryList(sql, this::createRankFromResultSet);

            for (Rank rank : ranks) {
                rankCache.put(rank.getUuid(), rank);
                rankNameCache.put(rank.getName().toLowerCase(), rank.getUuid());
            }

            // Load player ranks
            loadPlayerRanks();

            plugin.getLogger().info("Loaded " + ranks.size() + " ranks from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load ranks: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadPlayerRanks() throws SQLException {
        String sql = "SELECT player_uuid, rank_uuid FROM %splayer_ranks".formatted(plugin.getDatabaseManager().getTablePrefix());

        plugin.getDatabaseManager().queryList(sql, rs -> {
            UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
            UUID rankUuid = UUID.fromString(rs.getString("rank_uuid"));
            playerRanks.put(playerUuid, rankUuid);
            return null;
        });
    }

    public CompletableFuture<Boolean> setPlayerRank(UUID playerUuid, UUID rankUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = """
                    INSERT INTO %splayer_ranks (player_uuid, rank_uuid) VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE rank_uuid = VALUES(rank_uuid)
                """.formatted(plugin.getDatabaseManager().getTablePrefix());

                plugin.getDatabaseManager().executeUpdate(sql, playerUuid.toString(), rankUuid.toString());
                playerRanks.put(playerUuid, rankUuid);
                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set player rank: " + e.getMessage());
                return false;
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public boolean playerHasPermission(UUID playerUuid, String permission) {
        UUID rankUuid = playerRanks.get(playerUuid);
        if (rankUuid == null) return false;

        Rank rank = rankCache.get(rankUuid);
        return rank != null && rank.hasPermission(permission);
    }

    public Rank getPlayerRank(UUID playerUuid) {
        UUID rankUuid = playerRanks.get(playerUuid);
        return rankUuid != null ? rankCache.get(rankUuid) : null;
    }

    public List<Rank> getRanksByType(String type) {
        return rankCache.values().stream()
            .filter(rank -> rank.getType().equals(type))
            .sorted(Comparator.comparingInt(Rank::getPriority).reversed())
            .toList();
    }

    public Rank getRank(String name) {
        UUID rankUuid = rankNameCache.get(name.toLowerCase());
        return rankUuid != null ? rankCache.get(rankUuid) : null;
    }

    public Rank getRank(UUID uuid) {
        return rankCache.get(uuid);
    }

    public void saveRank(Rank rank) {
        try {
            String sql = """
                INSERT INTO %sranks (uuid, name, display_name, priority, permissions, is_default, type)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                name = VALUES(name), display_name = VALUES(display_name), priority = VALUES(priority),
                permissions = VALUES(permissions), is_default = VALUES(is_default), type = VALUES(type)
            """.formatted(plugin.getDatabaseManager().getTablePrefix());

            plugin.getDatabaseManager().executeUpdate(sql,
                rank.getUuid().toString(),
                rank.getName(),
                rank.getDisplayName(),
                rank.getPriority(),
                String.join(",", rank.getPermissions()),
                rank.isDefault(),
                rank.getType()
            );

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save rank: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Rank createRankFromResultSet(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String name = rs.getString("name");
        String type = rs.getString("type");

        Rank rank = new Rank(uuid, name, type);
        rank.setDisplayName(rs.getString("display_name"));
        rank.setPriority(rs.getInt("priority"));
        rank.setDefault(rs.getBoolean("is_default"));

        String permissionsStr = rs.getString("permissions");
        if (permissionsStr != null && !permissionsStr.isEmpty()) {
            rank.setPermissions(new HashSet<>(Arrays.asList(permissionsStr.split(","))));
        }

        return rank;
    }
}
