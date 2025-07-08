package org.arch.me.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.arch.me.EnhancedCoreH;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final EnhancedCoreH plugin;
    private HikariDataSource dataSource;
    private String tablePrefix;
    private String databaseType;

    public DatabaseManager(EnhancedCoreH plugin) {
     this.plugin = plugin;

    }

    public boolean initialize() {
        FileConfiguration config = plugin.getConfig();

        try {
            setupDataSource(config);
            try (Connection connection = getConnection()) {
                this.databaseType = connection.getMetaData().getDatabaseProductName();
            }
            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void setupDataSource(FileConfiguration config) {
        HikariConfig hikariConfig = new HikariConfig();

        String host = config.getString("database.host", "192.168.1.111");
        int port = config.getInt("database.port", 1433);
        String database = config.getString("database.database", "towny");
        String username = config.getString("database.username", "sa");
        String password = config.getString("database.password", "Tureet45");
        this.tablePrefix = config.getString("database.table-prefix", "towny_");

        // FIXED: SQL Server JDBC URL format
        String jdbcUrl = String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false;trustServerCertificate=true",
                host, port, database);

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        // FIXED: SQL Server driver
        hikariConfig.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

        // Connection pool settings
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    private void createTables() throws SQLException {
        // FIXED: SQL Server syntax - using proper conditional table creation
        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%splayers' AND xtype='U')
        CREATE TABLE %splayers (
            uuid NVARCHAR(36) PRIMARY KEY,
            name NVARCHAR(16) NOT NULL,
            town_uuid NVARCHAR(36),
            nation_uuid NVARCHAR(36),
            rank_id INT DEFAULT 0,
            balance DECIMAL(15,2) DEFAULT 0.00,
            last_online DATETIME2 DEFAULT GETDATE(),
            joined_town DATETIME2 NULL,
            permissions NTEXT,
            metadata NTEXT
        )
        """.formatted(tablePrefix, tablePrefix));

        // Create indexes separately in SQL Server
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_players_town') " +
                "CREATE INDEX idx_players_town ON %splayers(town_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_players_nation') " +
                "CREATE INDEX idx_players_nation ON %splayers(nation_uuid)".formatted(tablePrefix));

        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%stowns' AND xtype='U')
        CREATE TABLE %stowns (
            uuid NVARCHAR(36) PRIMARY KEY,
            name NVARCHAR(32) UNIQUE NOT NULL,
            mayor_uuid NVARCHAR(36) NOT NULL,
            nation_uuid NVARCHAR(36),
            spawn_world NVARCHAR(32),
            spawn_x FLOAT DEFAULT 0,
            spawn_y FLOAT DEFAULT 0,
            spawn_z FLOAT DEFAULT 0,
            spawn_yaw REAL DEFAULT 0,
            spawn_pitch REAL DEFAULT 0,
            founded DATETIME2 DEFAULT GETDATE(),
            balance DECIMAL(15,2) DEFAULT 0.00,
            tax_rate DECIMAL(5,2) DEFAULT 0.00,
            upkeep_cost DECIMAL(15,2) DEFAULT 0.00,
            max_residents INT DEFAULT 20,
            max_chunks INT DEFAULT 50,
            is_open BIT DEFAULT 0,
            is_public BIT DEFAULT 0,
            board NTEXT,
            permissions NTEXT,
            flags NTEXT,
            metadata NTEXT
        )
        """.formatted(tablePrefix, tablePrefix));

        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_towns_mayor') " +
                "CREATE INDEX idx_towns_mayor ON %stowns(mayor_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_towns_nation') " +
                "CREATE INDEX idx_towns_nation ON %stowns(nation_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_towns_name') " +
                "CREATE INDEX idx_towns_name ON %stowns(name)".formatted(tablePrefix));

        // Fixed nations table creation
        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%snations' AND xtype='U')
        CREATE TABLE %snations (
            uuid NVARCHAR(36) PRIMARY KEY,
            name NVARCHAR(32) UNIQUE NOT NULL,
            king_uuid NVARCHAR(36) NOT NULL,
            capital_town_uuid NVARCHAR(36),
            capital_chunk_uuid NVARCHAR(36),
            founded DATETIME2 DEFAULT GETDATE(),
            balance DECIMAL(15,2) DEFAULT 0.00,
            tax_rate DECIMAL(5,2) DEFAULT 0.00,
            max_towns INT DEFAULT 50,
            is_open BIT DEFAULT 1,
            is_public BIT DEFAULT 0,
            board NTEXT,
            permissions NTEXT,
            flags NTEXT,
            metadata NTEXT,
            towns NTEXT
        )
        """.formatted(tablePrefix, tablePrefix));

        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_nations_king') " +
                "CREATE INDEX idx_nations_king ON %snations(king_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_nations_capital') " +
                "CREATE INDEX idx_nations_capital ON %snations(capital_town_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_nations_name') " +
                "CREATE INDEX idx_nations_name ON %snations(name)".formatted(tablePrefix));

        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%schunks' AND xtype='U')
        CREATE TABLE %schunks (
            id BIGINT IDENTITY(1,1) PRIMARY KEY,
            uuid NVARCHAR(36) NOT NULL,
            world NVARCHAR(32) NOT NULL,
            x INT NOT NULL,
            z INT NOT NULL,
            town_uuid NVARCHAR(36),
            plot_type NVARCHAR(16) DEFAULT 'residential',
            plot_price DECIMAL(15,2) DEFAULT 0.00,
            owner_uuid NVARCHAR(36),
            claimed_date DATETIME2 DEFAULT GETDATE(),
            permissions NTEXT,
            flags NTEXT,
            metadata NTEXT,
            CONSTRAINT unique_chunk UNIQUE (world, x, z)
        )
        """.formatted(tablePrefix, tablePrefix));

        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_chunks_town') " +
                "CREATE INDEX idx_chunks_town ON %schunks(town_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_chunks_owner') " +
                "CREATE INDEX idx_chunks_owner ON %schunks(owner_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_chunks_location') " +
                "CREATE INDEX idx_chunks_location ON %schunks(world, x, z)".formatted(tablePrefix));

        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%sranks' AND xtype='U')
        CREATE TABLE %sranks (
            id INT IDENTITY(1,1) PRIMARY KEY,
            uuid NVARCHAR(36) NOT NULL,
            name NVARCHAR(32) NOT NULL,
            display_name NVARCHAR(64),
            type NVARCHAR(16) NOT NULL,
            prefix NVARCHAR(16),
            suffix NVARCHAR(16),
            permissions NTEXT,
            priority INT DEFAULT 0,
            is_default BIT DEFAULT 0,
            metadata NTEXT
        )
        """.formatted(tablePrefix, tablePrefix));

        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_ranks_name') " +
                "CREATE INDEX idx_ranks_name ON %sranks(name)".formatted(tablePrefix));

        // Player ranks table - to store which rank each player has
        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%splayer_ranks' AND xtype='U')
        CREATE TABLE %splayer_ranks (
            id BIGINT IDENTITY(1,1) PRIMARY KEY,
            player_uuid NVARCHAR(36) NOT NULL,
            rank_uuid NVARCHAR(36) NOT NULL,
            assigned_date DATETIME2 DEFAULT GETDATE(),
            assigned_by NVARCHAR(36),
            CONSTRAINT unique_player_rank UNIQUE (player_uuid)
        )
        """.formatted(tablePrefix, tablePrefix));

        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_player_ranks_player') " +
                "CREATE INDEX idx_player_ranks_player ON %splayer_ranks(player_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_player_ranks_rank') " +
                "CREATE INDEX idx_player_ranks_rank ON %splayer_ranks(rank_uuid)".formatted(tablePrefix));

        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%stransactions' AND xtype='U')
        CREATE TABLE %stransactions (
            id BIGINT IDENTITY(1,1) PRIMARY KEY,
            from_uuid NVARCHAR(36),
            to_uuid NVARCHAR(36),
            amount DECIMAL(15,2) NOT NULL,
            type NVARCHAR(32) NOT NULL,
            description NTEXT,
            timestamp DATETIME2 DEFAULT GETDATE(),
            metadata NTEXT
        )
        """.formatted(tablePrefix, tablePrefix));

        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_transactions_from') " +
                "CREATE INDEX idx_transactions_from ON %stransactions(from_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_transactions_to') " +
                "CREATE INDEX idx_transactions_to ON %stransactions(to_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_transactions_type') " +
                "CREATE INDEX idx_transactions_type ON %stransactions(type)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_transactions_timestamp') " +
                "CREATE INDEX idx_transactions_timestamp ON %stransactions(timestamp)".formatted(tablePrefix));

        // War system tables
        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%swars' AND xtype='U')
        CREATE TABLE %swars (
            id BIGINT IDENTITY(1,1) PRIMARY KEY,
            declaring_nation_uuid NVARCHAR(36) NOT NULL,
            defending_nation_uuid NVARCHAR(36) NOT NULL,
            war_name NVARCHAR(64) NOT NULL,
            status NVARCHAR(32) NOT NULL,
            declared_date DATETIME2 DEFAULT GETDATE(),
            start_date DATETIME2 NULL,
            end_date DATETIME2 NULL,
            metadata NTEXT
        )
        """.formatted(tablePrefix, tablePrefix));

        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_wars_declaring') " +
                "CREATE INDEX idx_wars_declaring ON %swars(declaring_nation_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_wars_defending') " +
                "CREATE INDEX idx_wars_defending ON %swars(defending_nation_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_wars_status') " +
                "CREATE INDEX idx_wars_status ON %swars(status)".formatted(tablePrefix));

        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%swar_participants' AND xtype='U')
        CREATE TABLE %swar_participants (
            id BIGINT IDENTITY(1,1) PRIMARY KEY,
            war_id BIGINT NOT NULL,
            nation_uuid NVARCHAR(36) NOT NULL,
            side NVARCHAR(16) NOT NULL,
            joined_date DATETIME2 DEFAULT GETDATE(),
            FOREIGN KEY (war_id) REFERENCES %swars(id) ON DELETE CASCADE
        )
        """.formatted(tablePrefix, tablePrefix, tablePrefix));

        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_war_participants_war') " +
                "CREATE INDEX idx_war_participants_war ON %swar_participants(war_id)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_war_participants_nation') " +
                "CREATE INDEX idx_war_participants_nation ON %swar_participants(nation_uuid)".formatted(tablePrefix));

        // War towns table - to track which towns are in which wars
        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%swar_towns' AND xtype='U')
        CREATE TABLE %swar_towns (
            id BIGINT IDENTITY(1,1) PRIMARY KEY,
            war_id BIGINT NOT NULL,
            town_uuid NVARCHAR(36) NOT NULL,
            side NVARCHAR(16) NOT NULL,
            FOREIGN KEY (war_id) REFERENCES %swars(id) ON DELETE CASCADE
        )
        """.formatted(tablePrefix, tablePrefix, tablePrefix));

        // Settings table for server configuration
        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%ssettings' AND xtype='U')
        CREATE TABLE %ssettings (
            id INT IDENTITY(1,1) PRIMARY KEY,
            setting_key NVARCHAR(64) NOT NULL UNIQUE,
            setting_value NVARCHAR(MAX) NOT NULL,
            setting_type NVARCHAR(16) NOT NULL DEFAULT 'STRING',
            description NVARCHAR(256),
            created_date DATETIME2 DEFAULT GETDATE(),
            updated_date DATETIME2 DEFAULT GETDATE()
        )
        """.formatted(tablePrefix, tablePrefix));

        // Insert default settings
        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM %ssettings WHERE setting_key = 'nether_claiming_enabled')
        INSERT INTO %ssettings (setting_key, setting_value, setting_type, description)
        VALUES ('nether_claiming_enabled', 'false', 'BOOLEAN', 'Allow nations to claim chunks in the Nether')
        """.formatted(tablePrefix, tablePrefix));

        // Insert default ranks
        insertDefaultRanks();

        // Create invitation tables
        createInvitationTables();
    }

    private void createInvitationTables() throws SQLException {
        // Town invitations table
        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%stown_invitations' AND xtype='U')
        CREATE TABLE %stown_invitations (
            id BIGINT IDENTITY(1,1) PRIMARY KEY,
            town_uuid NVARCHAR(36) NOT NULL,
            player_uuid NVARCHAR(36) NOT NULL,
            inviter_uuid NVARCHAR(36) NOT NULL,
            invited_at DATETIME2 DEFAULT GETDATE(),
            expires_at DATETIME2 NOT NULL,
            responded_at DATETIME2 NULL,
            status NVARCHAR(16) DEFAULT 'PENDING',
            CONSTRAINT unique_town_invitation UNIQUE (town_uuid, player_uuid, status)
        )
        """.formatted(tablePrefix, tablePrefix));

        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_town_invitations_player') " +
                "CREATE INDEX idx_town_invitations_player ON %stown_invitations(player_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_town_invitations_town') " +
                "CREATE INDEX idx_town_invitations_town ON %stown_invitations(town_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_town_invitations_status') " +
                "CREATE INDEX idx_town_invitations_status ON %stown_invitations(status)".formatted(tablePrefix));

        // Nation invitations table
        executeUpdate("""
        IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='%snation_invitations' AND xtype='U')
        CREATE TABLE %snation_invitations (
            id BIGINT IDENTITY(1,1) PRIMARY KEY,
            nation_uuid NVARCHAR(36) NOT NULL,
            town_uuid NVARCHAR(36) NOT NULL,
            inviter_uuid NVARCHAR(36) NOT NULL,
            invited_at DATETIME2 DEFAULT GETDATE(),
            expires_at DATETIME2 NOT NULL,
            responded_at DATETIME2 NULL,
            status NVARCHAR(16) DEFAULT 'PENDING',
            CONSTRAINT unique_nation_invitation UNIQUE (nation_uuid, town_uuid, status)
        )
        """.formatted(tablePrefix, tablePrefix));

        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_nation_invitations_town') " +
                "CREATE INDEX idx_nation_invitations_town ON %snation_invitations(town_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_nation_invitations_nation') " +
                "CREATE INDEX idx_nation_invitations_nation ON %snation_invitations(nation_uuid)".formatted(tablePrefix));
        executeUpdate("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_nation_invitations_status') " +
                "CREATE INDEX idx_nation_invitations_status ON %snation_invitations(status)".formatted(tablePrefix));
    }

    private void insertDefaultRanks() throws SQLException {
        String checkRank = "SELECT COUNT(*) FROM %sranks WHERE name = ?".formatted(tablePrefix);
        String insertRank = "INSERT INTO %sranks (uuid, name, prefix, permissions, priority, is_default, type, display_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)".formatted(tablePrefix);

        String[][] defaultRanks = {
                {"resident", "[R]", "towny.resident", "1", "1", "TOWN"}, // Changed to lowercase and added missing ranks
                {"manager", "[MG]", "towny.resident,towny.town.manage", "2", "0", "TOWN"},
                {"assistant", "[A]", "towny.resident,towny.town.assist", "3", "0", "TOWN"},
                {"vip", "[VIP]", "towny.resident,towny.vip", "2", "0", "TOWN"},
                {"councillor", "[C]", "towny.resident,towny.councillor", "3", "0", "TOWN"},
                {"mayor", "[M]", "towny.resident,towny.mayor", "4", "0", "TOWN"},
                {"citizen", "[CT]", "towny.nation.citizen", "1", "0", "NATION"},
                {"advisor", "[AD]", "towny.nation.citizen,towny.nation.advisor", "2", "0", "NATION"},
                {"deputy", "[D]", "towny.nation.citizen,towny.nation.deputy,towny.nation.invite,towny.nation.kick", "3", "0", "NATION"},
                {"minister", "[MIN]", "towny.nation.citizen,towny.nation.minister", "4", "0", "NATION"},
                {"king", "[K]", "towny.resident,towny.king", "5", "0", "NATION"}
        };

        for (String[] rank : defaultRanks) {
            if (queryInt(checkRank, rank[0]) == 0) {
                UUID rankUuid = UUID.randomUUID();
                executeUpdate(insertRank,
                    rankUuid.toString(),
                    rank[0], // name
                    rank[1], // prefix
                    rank[2], // permissions
                    Integer.parseInt(rank[3]), // priority
                    Integer.parseInt(rank[4]), // is_default
                    rank[5], // type
                    rank[0] // display_name (same as name)
                );
            }
        }
    }

    public void closeConnection() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public boolean isSQLServer() {
        return "Microsoft SQL Server".equalsIgnoreCase(databaseType);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            stmt.executeUpdate();
        }
    }

    public int executeUpdateWithResult(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            return stmt.executeUpdate();
        }
    }

    public <T> List<T> queryList(String sql, ResultSetMapper<T> mapper, Object... params) throws SQLException {
        List<T> results = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
            }
        }

        return results;
    }

    public <T> T queryObject(String sql, ResultSetMapper<T> mapper, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapper.map(rs);
                }
            }
        }

        return null;
    }

    public String queryString(String sql, Object... params) throws SQLException {
        return queryObject(sql, rs -> rs.getString(1), params);
    }

    public int queryInt(String sql, Object... params) throws SQLException {
        Integer result = queryObject(sql, rs -> rs.getInt(1), params);
        return result != null ? result : 0;
    }

    public boolean queryBoolean(String sql, Object... params) throws SQLException {
        Boolean result = queryObject(sql, rs -> rs.getBoolean(1), params);
        return result != null ? result : false;
    }

    // Async operations
    public CompletableFuture<Void> executeUpdateAsync(String sql, Object... params) {
        return CompletableFuture.runAsync(() -> {
            try {
                executeUpdate(sql, params);
            } catch (SQLException e) {
                plugin.getLogger().severe("Async database update failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public <T> CompletableFuture<List<T>> queryListAsync(String sql, ResultSetMapper<T> mapper, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return queryList(sql, mapper, params);
            } catch (SQLException e) {
                plugin.getLogger().severe("Async database query failed: " + e.getMessage());
                e.printStackTrace();
                return new ArrayList<>();
            }
        });
    }

    public <T> CompletableFuture<T> queryObjectAsync(String sql, ResultSetMapper<T> mapper, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return queryObject(sql, mapper, params);
            } catch (SQLException e) {
                plugin.getLogger().severe("Async database query failed: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    // Batch operations
    public void executeBatch(String sql, List<Object[]> paramsList) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (Object[] params : paramsList) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        }
    }


    @FunctionalInterface
    public interface ResultSetMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

}
