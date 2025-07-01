package org.arch.me.util;

import java.util.regex.Pattern;

public class NameValidator {
    // Only allow Latin letters, numbers, underscores, and hyphens
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 16;

    public static boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        name = name.trim();

        // Check length
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            return false;
        }

        // Check pattern - only Latin characters, numbers, underscores, and hyphens
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            return false;
        }

        // Cannot start or end with underscore or hyphen
        if (name.startsWith("_") || name.startsWith("-") ||
            name.endsWith("_") || name.endsWith("-")) {
            return false;
        }

        // Cannot have consecutive special characters
        if (name.contains("__") || name.contains("--") ||
            name.contains("_-") || name.contains("-_")) {
            return false;
        }

        return true;
    }

    public static String getValidationError(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Name cannot be empty";
        }

        name = name.trim();

        if (name.length() < MIN_NAME_LENGTH) {
            return "Name must be at least " + MIN_NAME_LENGTH + " characters long";
        }

        if (name.length() > MAX_NAME_LENGTH) {
            return "Name must be at most " + MAX_NAME_LENGTH + " characters long";
        }

        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            return "Name can only contain Latin letters (a-z, A-Z), numbers (0-9), underscores (_), and hyphens (-)";
        }

        if (name.startsWith("_") || name.startsWith("-") ||
            name.endsWith("_") || name.endsWith("-")) {
            return "Name cannot start or end with underscore or hyphen";
        }

        if (name.contains("__") || name.contains("--") ||
            name.contains("_-") || name.contains("-_")) {
            return "Name cannot contain consecutive special characters";
        }

        return "Unknown validation error";
    }
}
