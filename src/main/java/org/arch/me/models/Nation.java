package org.arch.me.models;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

// Nation model
public class Nation {
    private UUID uuid;
    private String name;
    private UUID kingUuid;
    private UUID capitalTownUuid; // Capital town
    private UUID capitalChunkUuid; // Capital chunk for wars
    private Timestamp founded;
    private BigDecimal balance;
    private BigDecimal taxRate;
    private int maxTowns;
    private boolean isOpen;
    private boolean isPublic;
    private String board;
    private Set<String> permissions;
    private Map<String, Boolean> flags;
    private Map<String, Object> metadata;
    private Set<UUID> towns;
    private Set<UUID> deputies; // Nation deputies for town management

    public Nation(UUID uuid, String name, UUID kingUuid, UUID capitalTownUuid, UUID capitalChunkUuid) {
        this.uuid = uuid;
        this.name = name;
        this.kingUuid = kingUuid;
        this.capitalTownUuid = capitalTownUuid;
        this.capitalChunkUuid = capitalChunkUuid;
        this.founded = new Timestamp(System.currentTimeMillis());
        this.balance = BigDecimal.ZERO;
        this.taxRate = BigDecimal.ZERO;
        this.maxTowns = 50;
        this.isOpen = true;
        this.isPublic = false;
        this.permissions = new HashSet<>();
        this.flags = new HashMap<>();
        this.metadata = new HashMap<>();
        this.towns = new HashSet<>();
        this.deputies = new HashSet<>(); // Initialize deputies set

        // Add capital town
        if (capitalTownUuid != null) {
            towns.add(capitalTownUuid);
        }
    }

    // Getters and Setters
    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getKingUuid() { return kingUuid; }
    public void setKingUuid(UUID kingUuid) { this.kingUuid = kingUuid; }

    // Fixed: Consistent method naming
    public UUID getCapitalUuid() { return capitalTownUuid; }
    public void setCapitalUuid(UUID capitalUuid) { this.capitalTownUuid = capitalUuid; }

    public UUID getCapitalTownUuid() { return capitalTownUuid; }
    public void setCapitalTownUuid(UUID capitalTownUuid) {
        this.capitalTownUuid = capitalTownUuid;
        // Ensure capital town is in the towns set
        if (capitalTownUuid != null && !towns.contains(capitalTownUuid)) {
            towns.add(capitalTownUuid);
        }
    }

    public UUID getCapitalChunkUuid() { return capitalChunkUuid; }
    public void setCapitalChunkUuid(UUID capitalChunkUuid) { this.capitalChunkUuid = capitalChunkUuid; }

    public Timestamp getFounded() { return founded; }
    public void setFounded(Timestamp founded) { this.founded = founded; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public int getMaxTowns() { return maxTowns; }
    public void setMaxTowns(int maxTowns) { this.maxTowns = maxTowns; }

    public boolean isOpen() { return isOpen; }
    public void setOpen(boolean open) { isOpen = open; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }

    public String getBoard() { return board; }
    public void setBoard(String board) { this.board = board; }

    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }

    public Map<String, Boolean> getFlags() { return flags; }
    public void setFlags(Map<String, Boolean> flags) { this.flags = flags; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Set<UUID> getTowns() { return towns; }
    public void setTowns(Set<UUID> towns) {
        this.towns = towns;
        // Ensure capital town is included
        if (capitalTownUuid != null && !this.towns.contains(capitalTownUuid)) {
            this.towns.add(capitalTownUuid);
        }
    }

    public Set<UUID> getDeputies() { return deputies; }
    public void setDeputies(Set<UUID> deputies) { this.deputies = deputies; }

    // Utility methods
    public boolean isKing(UUID playerUuid) {
        return kingUuid.equals(playerUuid);
    }

    public boolean hasTown(UUID townUuid) {
        return towns.contains(townUuid);
    }

    public void addTown(UUID townUuid) {
        towns.add(townUuid);
    }

    public void removeTown(UUID townUuid) {
        // Don't remove capital town unless we're setting a new one
        if (!townUuid.equals(capitalTownUuid)) {
            towns.remove(townUuid);
        } else if (towns.size() > 1) {
            // If removing capital and there are other towns, don't remove yet
            // The caller should set a new capital first
            return;
        } else {
            // Last town, can remove
            towns.remove(townUuid);
            capitalTownUuid = null;
        }
    }

    public int getTownCount() {
        return towns.size();
    }

    public boolean canAddTown() {
        return towns.size() < maxTowns;
    }

    public boolean getFlag(String flag) {
        return flags.getOrDefault(flag, false);
    }

    public void setFlag(String flag, boolean value) {
        flags.put(flag, value);
    }

    public boolean hasCapitalChunk() {
        return capitalChunkUuid != null;
    }

    public boolean isCapitalTown(UUID townUuid) {
        return capitalTownUuid != null && capitalTownUuid.equals(townUuid);
    }

    // Additional methods for better integration
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public void addPermission(String permission) {
        permissions.add(permission);
    }

    public void removePermission(String permission) {
        permissions.remove(permission);
    }

    public boolean isEmpty() {
        return towns.isEmpty();
    }

    public List<UUID> getTownsList() {
        return new ArrayList<>(towns);
    }

    // Deputy management methods
    public boolean isDeputy(UUID playerUuid) {
        return deputies.contains(playerUuid);
    }

    public void addDeputy(UUID playerUuid) {
        deputies.add(playerUuid);
    }

    public void removeDeputy(UUID playerUuid) {
        deputies.remove(playerUuid);
    }

    public boolean hasDeputy(UUID playerUuid) {
        return deputies.contains(playerUuid);
    }

    public Set<UUID> getDeputyList() {
        return new HashSet<>(deputies);
    }

    public int getDeputyCount() {
        return deputies.size();
    }

    public boolean canManageNation(UUID playerUuid) {
        return isKing(playerUuid) || isDeputy(playerUuid);
    }

    public boolean canManageTowns(UUID playerUuid) {
        // King and deputies can manage all towns in the nation
        return isKing(playerUuid) || isDeputy(playerUuid);
    }

    @Override
    public String toString() {
        return "Nation{" +
                "name='" + name + '\'' +
                ", king=" + kingUuid +
                ", towns=" + towns.size() +
                ", capital=" + capitalTownUuid +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Nation nation = (Nation) o;
        return Objects.equals(uuid, nation.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
