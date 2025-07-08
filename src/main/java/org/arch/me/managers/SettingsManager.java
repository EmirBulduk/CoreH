package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.database.DatabaseManager;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class SettingsManager {

    private final EnhancedCoreH plugin;
    private final Map<String, String> settingsCache = new ConcurrentHashMap<>();

    public SettingsManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        loadSettings();
    }

    private void loadSettings() {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT setting_key, setting_value FROM %ssettings".formatted(db.getTablePrefix());

            var settings = db.queryList(sql, rs -> {
                String key = rs.getString("setting_key");
                String value = rs.getString("setting_value");
                return new String[]{key, value};
            });

            for (String[] setting : settings) {
                settingsCache.put(setting[0], setting[1]);
            }

            plugin.getLogger().info("Loaded " + settings.size() + " server settings from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load settings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getSetting(String key) {
        return settingsCache.get(key);
    }

    public String getSetting(String key, String defaultValue) {
        return settingsCache.getOrDefault(key, defaultValue);
    }

    public boolean getBooleanSetting(String key) {
        String value = settingsCache.get(key);
        return value != null && "true".equalsIgnoreCase(value);
    }

    public boolean getBooleanSetting(String key, boolean defaultValue) {
        String value = settingsCache.get(key);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value);
    }

    public int getIntSetting(String key, int defaultValue) {
        String value = settingsCache.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDoubleSetting(String key, double defaultValue) {
        String value = settingsCache.get(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public CompletableFuture<Boolean> setSetting(String key, String value) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DatabaseManager db = plugin.getDatabaseManager();

                String sql;
                if (db.isSQLServer()) {
                    sql = """
                        MERGE INTO %ssettings AS target
                        USING (VALUES (?, ?, 'STRING', GETDATE())) AS source (setting_key, setting_value, setting_type, updated_date)
                        ON target.setting_key = source.setting_key
                        WHEN MATCHED THEN
                            UPDATE SET setting_value = source.setting_value, updated_date = source.updated_date
                        WHEN NOT MATCHED THEN
                            INSERT (setting_key, setting_value, setting_type, updated_date)
                            VALUES (source.setting_key, source.setting_value, source.setting_type, source.updated_date);
                        """.formatted(db.getTablePrefix());
                } else {
                    sql = """
                        INSERT INTO %ssettings (setting_key, setting_value, setting_type, updated_date) 
                        VALUES (?, ?, 'STRING', NOW())
                        ON DUPLICATE KEY UPDATE 
                        setting_value = VALUES(setting_value), 
                        updated_date = VALUES(updated_date)
                        """.formatted(db.getTablePrefix());
                }

                db.executeUpdate(sql, key, value);
                settingsCache.put(key, value);

                plugin.getLogger().info("Updated setting: " + key + " = " + value);
                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update setting " + key + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> setBooleanSetting(String key, boolean value) {
        return setSetting(key, String.valueOf(value));
    }

    public CompletableFuture<Boolean> setIntSetting(String key, int value) {
        return setSetting(key, String.valueOf(value));
    }

    public CompletableFuture<Boolean> setDoubleSetting(String key, double value) {
        return setSetting(key, String.valueOf(value));
    }

    public boolean isNetherClaimingEnabled() {
        return getBooleanSetting("nether_claiming_enabled", false);
    }

    public CompletableFuture<Boolean> setNetherClaimingEnabled(boolean enabled) {
        return setBooleanSetting("nether_claiming_enabled", enabled);
    }

    public void reloadSettings() {
        settingsCache.clear();
        loadSettings();
    }
}
