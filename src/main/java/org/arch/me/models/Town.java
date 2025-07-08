package org.arch.me.models;

import org.bukkit.Location;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

// Town model
public class Town {
    private UUID uuid;
    private String name;
    private UUID mayorUuid;
    private UUID nationUuid;
    private Location spawn;
    private Timestamp founded;
    private BigDecimal balance;
    private BigDecimal taxRate;
    private BigDecimal upkeepCost;
    private int maxResidents;
    private int maxChunks; // Added missing field
    private boolean isOpen;
    private boolean isPublic;
    private String board;
    private Set<String> permissions;
    private Map<String, Boolean> flags;
    private Map<String, Object> metadata;
    private Set<UUID> residents;
    private Set<ClaimedChunk> claimedChunks;

    public Town(UUID uuid, String name, UUID mayorUuid) {
        this.uuid = uuid;
        this.name = name;
        this.mayorUuid = mayorUuid;
        this.founded = new Timestamp(System.currentTimeMillis());
        this.balance = BigDecimal.ZERO;
        this.taxRate = BigDecimal.ZERO;
        this.upkeepCost = BigDecimal.ZERO;
        this.maxResidents = 20;
        this.maxChunks = 50; // Start with tier 1 limit
        this.isOpen = true;
        this.isPublic = false;
        this.permissions = new HashSet<>();
        this.flags = new HashMap<>();
        this.metadata = new HashMap<>();
        this.residents = new HashSet<>();
        this.claimedChunks = new HashSet<>();

        // Default flags
        flags.put("pvp", false);
        flags.put("explosions", false);
        flags.put("fire", false);
        flags.put("mobspawning", false);
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

    public UUID getMayorUuid() {
        return mayorUuid;
    }

    public void setMayorUuid(UUID mayorUuid) {
        this.mayorUuid = mayorUuid;
    }

    public UUID getNationUuid() {
        return nationUuid;
    }

    public void setNationUuid(UUID nationUuid) {
        this.nationUuid = nationUuid;
    }

    public Location getSpawn() {
        return spawn;
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn;
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

    public BigDecimal getUpkeepCost() {
        return upkeepCost;
    }

    public void setUpkeepCost(BigDecimal upkeepCost) {
        this.upkeepCost = upkeepCost;
    }

    public int getMaxResidents() {
        return maxResidents;
    }

    public void setMaxResidents(int maxResidents) {
        this.maxResidents = maxResidents;
    }

    public int getMaxChunks() {
        return maxChunks;
    }

    public void setMaxChunks(int maxChunks) {
        this.maxChunks = maxChunks;
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

    public Set<UUID> getResidents() {
        return residents;
    }

    public void setResidents(Set<UUID> residents) {
        this.residents = residents;
    }

    public Set<ClaimedChunk> getClaimedChunks() {
        return claimedChunks;
    }

    public void setClaimedChunks(Set<ClaimedChunk> claimedChunks) {
        this.claimedChunks = claimedChunks;
    }

    // Utility methods
    public boolean hasNation() {
        return nationUuid != null;
    }

    public boolean isMayor(UUID playerUuid) {
        return mayorUuid.equals(playerUuid);
    }

    public boolean hasResident(UUID playerUuid) {
        return residents.contains(playerUuid);
    }

    public void addResident(UUID playerUuid) {
        residents.add(playerUuid);
    }

    public void removeResident(UUID playerUuid) {
        residents.remove(playerUuid);
    }

    public int getResidentCount() {
        return residents.size();
    }

    public boolean canAddResident() {
        return residents.size() < maxResidents;
    }

    public boolean getFlag(String flag) {
        return flags.getOrDefault(flag, false);
    }

    public void setFlag(String flag, boolean value) {
        flags.put(flag, value);
    }

    public void addClaimedChunk(ClaimedChunk chunk) {
        claimedChunks.add(chunk);
    }

    public void removeClaimedChunk(ClaimedChunk chunk) {
        claimedChunks.remove(chunk);
    }

    public int getClaimedChunkCount() {
        return claimedChunks.size();
    }

    public boolean canClaimMoreChunks() {
        return claimedChunks.size() < maxChunks;
    }

    public int getRemainingChunkSlots() {
        return Math.max(0, maxChunks - claimedChunks.size());
    }
}
