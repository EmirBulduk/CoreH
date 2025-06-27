package org.arch.me;

import org.arch.me.commands.*;
import org.arch.me.config.ConfigManager;
import org.arch.me.database.DatabaseManager;
import org.arch.me.economy.EconomyManager;
import org.arch.me.listeners.*;
import org.arch.me.managers.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.List;

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
        if (economyManager.setupEconomy()) {
            getLogger().info("Successfully hooked into Vault for economy features.");
        } else {
            // Vault bulunamazsa veya bir ekonomi sağlayıcısı yoksa,
            // EconomyManager zaten dahili veritabanı sistemini kullanmaya devam edecektir.
            getLogger().warning("Vault not found or no economy provider. Using internal economy system.");
        }

        // Initialize managers
        playerManager = new PlayerManager(this);
        rankManager = new RankManager(this);
        chunkManager = new ChunkManager(this);
        townManager = new TownManager(this);
        nationManager = new NationManager(this);

        registerCommands();

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
        try {
            // Get the command map using reflection
            Field commandMapField = getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(getServer());

            // Register commands directly to the command map
            registerCommand(commandMap, "town", new TownCommand(this), "Manages towns");
            registerCommand(commandMap, "nation", new NationCommand(this), "Manages nations");
            registerCommand(commandMap, "plot", new PlotCommand(this), "Manages plots");
            registerCommand(commandMap, "resident", new ResidentCommand(this), "Manages residents");
            registerCommand(commandMap, "towny", new TownyAdminCommand(this), "Admin commands");

            getLogger().info("Successfully registered all commands!");

        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerCommand(CommandMap commandMap, String name, Object executor, String description) {
        try {
            // Create a custom command that wraps our executor
            CustomCommand customCommand = new CustomCommand(name, description, this, executor);

            // Register the command
            commandMap.register(this.getName().toLowerCase(), customCommand);

            getLogger().info("Registered command: " + name);

        } catch (Exception e) {
            getLogger().warning("Failed to register command " + name + ": " + e.getMessage());
        }
    }

    // Custom Command class that wraps our executors
    private static class CustomCommand extends Command {
        private final JavaPlugin plugin;
        private final Object executor;

        public CustomCommand(String name, String description, JavaPlugin plugin, Object executor) {
            super(name);
            this.setDescription(description);
            this.setUsage("/" + name);
            this.plugin = plugin;
            this.executor = executor;
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            if (executor instanceof CommandExecutor) {
                return ((CommandExecutor) executor).onCommand(sender, this, commandLabel, args);
            }
            return false;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (executor instanceof TabCompleter) {
                return ((TabCompleter) executor).onTabComplete(sender, this, alias, args);
            }
            return super.tabComplete(sender, alias, args);
        }
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
