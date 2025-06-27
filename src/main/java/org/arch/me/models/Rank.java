package org.arch.me.models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Rank model
public class Rank {
    private int id;
    private String name;
    private String prefix;
    private String suffix;
    private Set<String> permissions;
    private int priority;
    private boolean isDefault;
    private Map<String, Object> metadata;

    public Rank(int id, String name) {
        this.id = id;
        this.name = name;
        this.permissions = new HashSet<>();
        this.priority = 0;
        this.isDefault = false;
        this.metadata = new HashMap<>();
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Utility methods
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public void addPermission(String permission) {
        permissions.add(permission);
    }

    public void removePermission(String permission) {
        permissions.remove(permission);
    }

    public String getFormattedName() {
        StringBuilder formatted = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            formatted.append(prefix);
        }
        formatted.append(name);
        if (suffix != null && !suffix.isEmpty()) {
            formatted.append(suffix);
        }
        return formatted.toString().replace("&", "§");
    }
}
