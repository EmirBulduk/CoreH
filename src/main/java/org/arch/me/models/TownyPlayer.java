package org.arch.me.models;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

// Player/Resident model
public class TownyPlayer {
    private UUID uuid;
    private String name;
    private UUID townUuid;
    private UUID nationUuid;
    private int rankId;
    private BigDecimal balance;
    private Timestamp lastOnline;
    private Timestamp joinedTown;
    private Set<String> permissions;
    private Map<String, Object> metadata;

    public TownyPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.permissions = new HashSet<>();
        this.metadata = new HashMap<>();
        this.balance = BigDecimal.ZERO;
    }

    // Getters and setters
    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getTownUuid() { return townUuid; }
    public void setTownUuid(UUID townUuid) { this.townUuid = townUuid; }

    public UUID getNationUuid() { return nationUuid; }
    public void setNationUuid(UUID nationUuid) { this.nationUuid = nationUuid; }

    public int getRankId() { return rankId; }
    public void setRankId(int rankId) { this.rankId = rankId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public Timestamp getLastOnline() { return lastOnline; }
    public void setLastOnline(Timestamp lastOnline) { this.lastOnline = lastOnline; }

    public Timestamp getJoinedTown() { return joinedTown; }
    public void setJoinedTown(Timestamp joinedTown) { this.joinedTown = joinedTown; }

    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    // Utility methods
    public boolean isOnline() {
        return Bukkit.getPlayer(uuid) != null;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean hasTown() {
        return townUuid != null;
    }

    public boolean hasNation() {
        return nationUuid != null;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public void addPermission(String permission) {
        permissions.add(permission);
    }

    public void removePermission(String permission) {
        permissions.remove(permission);
    }
}

