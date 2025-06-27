package org.arch.me.config;

import org.arch.me.EnhancedCoreH;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final EnhancedCoreH plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();

    public ConfigManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        // Load main config
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        // Load additional configs
        loadConfig("towns.yml");
        loadConfig("nations.yml");
        loadConfig("permissions.yml");
        loadConfig("economy.yml");
        loadConfig("messages.yml");

        // Set default values
        setDefaults();
    }

    private void loadConfig(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);

        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        configs.put(fileName, config);
    }

    private void setDefaults() {
        FileConfiguration config = plugin.getConfig();

        // Database settings
        config.addDefault("database.host", "192.168.1.111");
        config.addDefault("database.port", 1433);
        config.addDefault("database.database", "towny");
        config.addDefault("database.username", "sa");
        config.addDefault("database.password", "Tureet45");
        config.addDefault("database.table-prefix", "towny_");

        // Economy settings
        config.addDefault("economy.enabled", true);
        config.addDefault("economy.starting-money", 100.0);
        config.addDefault("economy.town-creation-cost", 1000.0);
        config.addDefault("economy.nation-creation-cost", 5000.0);
        config.addDefault("economy.chunk-claim-cost", 50.0);
        config.addDefault("economy.daily-upkeep-town", 10.0);
        config.addDefault("economy.daily-upkeep-nation", 100.0);

        // Town settings
        config.addDefault("towns.max-residents", 20);
        config.addDefault("towns.max-chunks", 100);
        config.addDefault("towns.default-tax-rate", 0.0);
        config.addDefault("towns.allow-public-spawning", true);
        config.addDefault("towns.spawn-cooldown", 30);
        config.addDefault("towns.require-chunks-connected", false);

        // Nation settings
        config.addDefault("nations.max-towns", 50);
        config.addDefault("nations.max-chunks", 1000);
        config.addDefault("nations.default-tax-rate", 0.0);
        config.addDefault("nations.allow-public-spawning", true);
        config.addDefault("nations.spawn-cooldown", 60);

        // Chunk settings
        config.addDefault("chunks.max-claims-per-player", 10);
        config.addDefault("chunks.allow-end-claiming", true);
        config.addDefault("chunks.allow-nether-claiming", false);
        config.addDefault("chunks.wilderness-permissions.build", false);
        config.addDefault("chunks.wilderness-permissions.destroy", false);
        config.addDefault("chunks.wilderness-permissions.switch", false);
        config.addDefault("chunks.wilderness-permissions.item-use", false);

        // Protection settings
        config.addDefault("protection.prevent-pvp-in-towns", true);
        config.addDefault("protection.prevent-mob-spawning-in-towns", true);
        config.addDefault("protection.prevent-explosion-damage", true);
        config.addDefault("protection.prevent-fire-spread", true);
        config.addDefault("protection.protect-vehicles", true);

        // World settings
        config.addDefault("worlds.enabled-worlds", Arrays.asList("world", "world_the_end"));
        config.addDefault("worlds.disable-claiming-worlds", Arrays.asList("world_nether"));

        // Rank settings
        config.addDefault("ranks.enable-prefixes", true);
        config.addDefault("ranks.enable-suffixes", true);
        config.addDefault("ranks.default-rank", "Resident");

        // Messages
        setDefaultMessages();

        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    private void setDefaultMessages() {
        FileConfiguration messages = getConfig("messages.yml");

        // General messages
        messages.addDefault("general.no-permission", "&cYou don't have permission to do that!");
        messages.addDefault("general.player-not-found", "&cPlayer not found!");
        messages.addDefault("general.invalid-amount", "&cInvalid amount!");
        messages.addDefault("general.insufficient-funds", "&cInsufficient funds!");

        // Town messages
        messages.addDefault("town.created", "&aSuccessfully created town '{0}'!");
        messages.addDefault("town.deleted", "&aSuccessfully deleted town '{0}'!");
        messages.addDefault("town.joined", "&aYou have joined the town '{0}'!");
        messages.addDefault("town.left", "&aYou have left the town '{0}'!");
        messages.addDefault("town.not-in-town", "&cYou are not in a town!");
        messages.addDefault("town.already-in-town", "&cYou are already in a town!");
        messages.addDefault("town.not-mayor", "&cYou are not the mayor of this town!");
        messages.addDefault("town.chunk-claimed", "&aChunk claimed for town '{0}'!");
        messages.addDefault("town.chunk-unclaimed", "&aChunk unclaimed from town '{0}'!");
        messages.addDefault("town.max-residents-reached", "&cThis town has reached its maximum resident limit!");
        messages.addDefault("town.max-chunks-reached", "&cThis town has reached its maximum chunk limit!");

        // Nation messages
        messages.addDefault("nation.created", "&aSuccessfully created nation '{0}'!");
        messages.addDefault("nation.deleted", "&aSuccessfully deleted nation '{0}'!");
        messages.addDefault("nation.joined", "&aTown '{0}' has joined the nation '{1}'!");
        messages.addDefault("nation.left", "&aTown '{0}' has left the nation '{1}'!");
        messages.addDefault("nation.not-in-nation", "&cYour town is not in a nation!");
        messages.addDefault("nation.already-in-nation", "&cYour town is already in a nation!");
        messages.addDefault("nation.not-king", "&cYou are not the king of this nation!");

        // Economy messages
        messages.addDefault("economy.paid", "&aYou paid ${0} to {1}!");
        messages.addDefault("economy.received", "&aYou received ${0} from {1}!");
        messages.addDefault("economy.balance", "&aYour balance: ${0}");
        messages.addDefault("economy.town-balance", "&aTown balance: ${0}");
        messages.addDefault("economy.nation-balance", "&aNation balance: ${0}");

        // Protection messages
        messages.addDefault("protection.cannot-build", "&cYou cannot build here!");
        messages.addDefault("protection.cannot-destroy", "&cYou cannot destroy blocks here!");
        messages.addDefault("protection.cannot-interact", "&cYou cannot interact with that!");
        messages.addDefault("protection.wilderness", "&cYou are in the wilderness!");

        saveConfig("messages.yml");
    }

    public FileConfiguration getConfig(String fileName) {
        return configs.getOrDefault(fileName, plugin.getConfig());
    }

    public void saveConfig(String fileName) {
        FileConfiguration config = configs.get(fileName);
        if (config != null) {
            try {
                config.save(new File(plugin.getDataFolder(), fileName));
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save " + fileName + ": " + e.getMessage());
            }
        }
    }

    public void reloadConfig(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (configFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            configs.put(fileName, config);
        }
    }

    public void reloadAllConfigs() {
        plugin.reloadConfig();
        for (String fileName : configs.keySet()) {
            reloadConfig(fileName);
        }
    }

    // Convenience methods for getting config values
    public String getMessage(String key, Object... args) {
        FileConfiguration messages = getConfig("messages.yml");
        String message = messages.getString(key, "&cMessage not found: " + key);

        // Replace placeholders
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }

        return message.replace("&", "§");
    }

    public double getEconomyValue(String key) {
        return plugin.getConfig().getDouble("economy." + key, 0.0);
    }

    public int getTownValue(String key) {
        return plugin.getConfig().getInt("towns." + key, 0);
    }

    public int getNationValue(String key) {
        return plugin.getConfig().getInt("nations." + key, 0);
    }

    public boolean getProtectionValue(String key) {
        return plugin.getConfig().getBoolean("protection." + key, false);
    }

    public List<String> getEnabledWorlds() {
        return plugin.getConfig().getStringList("worlds.enabled-worlds");
    }

    public List<String> getDisabledClaimingWorlds() {
        return plugin.getConfig().getStringList("worlds.disable-claiming-worlds");
    }
}