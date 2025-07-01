package org.arch.me.models;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Rank {
    private UUID uuid;
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
    }

    // Getters and Setters
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

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
