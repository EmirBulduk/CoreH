package org.arch.me.models;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Rank {
    private UUID uuid;
    private int id; // Database ID
    private String name;
    private String displayName;
    private int priority; // Higher priority = higher rank
    private Set<String> permissions;
    private boolean isDefault;
    private String type; // "TOWN" or "NATION"

    public Rank(UUID uuid, String name, String type) {
        this.uuid = uuid;
        this.name = name;
        this.type = type;
        this.displayName = name;
        this.priority = 0;
        this.permissions = new HashSet<>();
        this.isDefault = false;
        this.id = 0; // Will be set from database
    }

    // Getters and Setters
    public UUID getUuid() { return uuid; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /**
     * Get formatted name with color codes and priority indication
     */
    public String getFormattedName() {
        String color = getColorByPriority();
        return color + displayName + "§r";
    }

    /**
     * Get color code based on rank priority
     */
    private String getColorByPriority() {
        return switch (priority) {
            case 0 -> "§7"; // Gray for default/low priority
            case 1 -> "§f"; // White for basic ranks
            case 2 -> "§a"; // Green for assistant/manager
            case 3 -> "§6"; // Gold for advisor/minister
            case 4 -> "§c"; // Red for mayor/king
            default -> priority > 4 ? "§4" : "§8"; // Dark red for highest, dark gray for negative
        };
    }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }
    public void addPermission(String permission) { this.permissions.add(permission); }
    public void removePermission(String permission) { this.permissions.remove(permission); }
    public boolean hasPermission(String permission) { return permissions.contains(permission); }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isTownRank() { return "TOWN".equals(type); }
    public boolean isNationRank() { return "NATION".equals(type); }
}
