package org.arch.me.models;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

// Nation model
public class Nation {
    private UUID uuid;
    private String name;
    private UUID kingUuid;
    private UUID capitalUuid;
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

    public Nation(UUID uuid, String name, UUID kingUuid, UUID capitalUuid) {
        this.uuid = uuid;
        this.name = name;
        this.kingUuid = kingUuid;
        this.capitalUuid = capitalUuid;
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

        // Add capital town
        towns.add(capitalUuid);
    }

    // Getters and setters
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getKingUuid() {
        return kingUuid;
    }

    public void setKingUuid(UUID kingUuid) {
        this.kingUuid = kingUuid;
    }

    public UUID getCapitalUuid() {
        return capitalUuid;
    }

    public void setCapitalUuid(UUID capitalUuid) {
        this.capitalUuid = capitalUuid;
    }

    public Timestamp getFounded() {
        return founded;
    }

    public void setFounded(Timestamp founded) {
        this.founded = founded;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public int getMaxTowns() {
        return maxTowns;
    }

    public void setMaxTowns(int maxTowns) {
        this.maxTowns = maxTowns;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        isOpen = open;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public String getBoard() {
        return board;
    }

    public void setBoard(String board) {
        this.board = board;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Map<String, Boolean> getFlags() {
        return flags;
    }

    public void setFlags(Map<String, Boolean> flags) {
        this.flags = flags;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Set<UUID> getTowns() {
        return towns;
    }

    public void setTowns(Set<UUID> towns) {
        this.towns = towns;
    }

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
        towns.remove(townUuid);
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
}
