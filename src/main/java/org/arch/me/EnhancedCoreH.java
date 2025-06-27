package org.arch.me;

import org.arch.me.commands.*;
import org.arch.me.config.ConfigManager;
import org.arch.me.database.DatabaseManager;
import org.arch.me.economy.EconomyManager;
import org.arch.me.listeners.*;
import org.arch.me.managers.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class EnhancedCoreH extends JavaPlugin {

    private static EnhancedCoreH instance;
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private EconomyManager economyManager;
    private TownManager townManager;
    private NationManager nationManager;
    private PlayerManager playerManager;
    private ChunkManager chunkManager;
    private RankManager rankManager;



    @Override
    public void onEnable() {
        instance = this;

        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize economy
        this.economyManager = new EconomyManager(this);
        getServer().getScheduler().runTask(this, () -> {
            if (economyManager.setupEconomy()) {
                getLogger().info("Successfully hooked into Vault for economy features.");
            } else {
                // Vault bulunamazsa veya bir ekonomi sağlayıcısı yoksa,
                // EconomyManager zaten dahili veritabanı sistemini kullanmaya devam edecektir.
                getLogger().warning("Vault not found or no economy provider. Using internal economy system.");
            }
        });

        // Initialize managers
        playerManager = new PlayerManager(this);
        rankManager = new RankManager(this);
        chunkManager = new ChunkManager(this);
        townManager = new TownManager(this);
        nationManager = new NationManager(this);

        getServer().getScheduler().runTask(this, this::registerCommands);

        registerListeners();

        getLogger().info("EnhancedCoreH has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }

        // Save all data before shutdown
        if (townManager != null) {
            townManager.saveAll();
        }
        if (playerManager != null) {
            playerManager.saveAll();
        }

        getLogger().info("EnhancedCoreH has been disabled successfully!");
    }

    private void registerCommands() {
        getCommand("town").setExecutor(new TownCommand(this));
        getCommand("nation").setExecutor(new NationCommand(this));
        getCommand("plot").setExecutor(new PlotCommand(this));
        getCommand("resident").setExecutor(new ResidentCommand(this));
        getCommand("towny").setExecutor(new TownyAdminCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new TownListener(this), this);
        getServer().getPluginManager().registerEvents(new EconomyListener(this), this);
    }

    // Getters
    public static EnhancedCoreH getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public TownManager getTownManager() {
        return townManager;
    }

    public NationManager getNationManager() {
        return nationManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }
}
