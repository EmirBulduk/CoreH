package org.arch.me.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.regex.Pattern;

public class ValidationUtil {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_]{2,32}$");
    private static final Pattern VALID_UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * Validate town/nation name
     */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        // Check length
        if (name.length() < 2 || name.length() > 32) {
            return false;
        }

        // Check pattern
        if (!VALID_NAME.matcher(name).matches()) {
            return false;
        }

        // Check for forbidden names
        String lowerName = name.toLowerCase();
        return !isForbiddenName(lowerName);
    }

    /**
     * Check if name is forbidden
     */
    private static boolean isForbiddenName(String name) {
        String[] forbidden = {
                "admin", "server", "console", "owner", "mod", "moderator",
                "wilderness", "wild", "spawn", "warzone", "war", "nation",
                "town", "plot", "chunk", "world"
        };

        for (String forbidden_name : forbidden) {
            if (name.equals(forbidden_name)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validate UUID string
     */
    public static boolean isValidUUID(String uuid) {
        return uuid != null && VALID_UUID.matcher(uuid).matches();
    }

    /**
     * Validate player online status
     */
    public static boolean isPlayerOnline(Player player) {
        return player != null && player.isOnline();
    }

    /**
     * Validate amount (positive number)
     */
    public static boolean isValidAmount(String amount) {
        try {
            double value = Double.parseDouble(amount);
            return value > 0 && value <= Double.MAX_VALUE;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validate percentage (0-100)
     */
    public static boolean isValidPercentage(String percentage) {
        try {
            double value = Double.parseDouble(percentage);
            return value >= 0 && value <= 100;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validate integer within range
     */
    public static boolean isValidInteger(String number, int min, int max) {
        try {
            int value = Integer.parseInt(number);
            return value >= min && value <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validate material name
     */
    public static boolean isValidMaterial(String materialName) {
        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            return material != Material.AIR;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Sanitize input string
     */
    public static String sanitizeInput(String input) {
        if (input == null) return "";

        // Remove dangerous characters
        return input.replaceAll("[<>&\"']", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Validate world name exists
     */
    public static boolean isValidWorld(String worldName) {
        return worldName != null &&
                org.bukkit.Bukkit.getWorld(worldName) != null;
    }

    /**
     * Check if coordinates are within world border
     */
    public static boolean isWithinWorldBorder(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        org.bukkit.WorldBorder border = location.getWorld().getWorldBorder();
        double borderSize = border.getSize() / 2;
        org.bukkit.Location center = border.getCenter();

        double distance = location.distance(center);
        return distance <= borderSize;
    }
}
