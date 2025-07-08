package org.arch.me.models;

import org.bukkit.Location;
import org.arch.me.EnhancedCoreH;

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

    // Rank-based permission methods
    public boolean hasPermission(UUID playerUuid, String permission) {
        if (playerUuid == null || permission == null) return false;

        // Mayor has all permissions
        if (isMayor(playerUuid)) return true;

        // Check if player is nation deputy and has nation-wide permissions
        if (hasNation() && isNationDeputy(playerUuid)) {
            return hasNationDeputyPermission(permission);
        }

        // Check player's town rank permissions
        return hasRankPermission(playerUuid, permission);
    }

    private boolean isNationDeputy(UUID playerUuid) {
        if (!hasNation()) return false;

        try {
            EnhancedCoreH plugin = EnhancedCoreH.getInstance();
            if (plugin != null && plugin.getNationManager() != null) {
                var nation = plugin.getNationManager().getNation(nationUuid);
                return nation != null && nation.isDeputy(playerUuid);
            }
        } catch (Exception e) {
            // Handle plugin not available
        }
        return false;
    }

    private boolean hasNationDeputyPermission(String permission) {
        // Nation deputies have manager-level permissions in all towns of their nation
        return isManagerPermission(permission) || isAssistantPermission(permission) || isResidentPermission(permission);
    }

    private boolean hasRankPermission(UUID playerUuid, String permission) {
        try {
            EnhancedCoreH plugin = EnhancedCoreH.getInstance();
            if (plugin != null && plugin.getRankManager() != null) {
                return plugin.getRankManager().playerHasPermission(playerUuid, permission);
            }
        } catch (Exception e) {
            // Handle plugin not available
        }
        return false;
    }

    private boolean isManagerPermission(String permission) {
        return permission.contains("claim") || permission.contains("unclaim") ||
                permission.contains("invite") || permission.contains("kick") ||
                permission.contains("set.flags") || permission.contains("set.spawn") ||
                permission.contains("set.board") || permission.contains("set.taxes");
    }

    private boolean isAssistantPermission(String permission) {
        return isManagerPermission(permission) ||
                permission.contains("toggle") || permission.contains("set.perm");
    }

    private boolean isResidentPermission(String permission) {
        return permission.contains("resident") || permission.contains("plot.claim") ||
                permission.contains("plot.unclaim") || permission.contains("home");
    }

    // Specific permission checks for common actions
    public boolean canClaimChunk(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.claim") && canClaimMoreChunks();
    }

    public boolean canUnclaimChunk(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.unclaim");
    }

    public boolean canInvitePlayer(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.invite") && canAddResident();
    }

    public boolean canKickPlayer(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.kick");
    }

    public boolean canSetFlags(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.set.flags");
    }

    public boolean canSetSpawn(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.set.spawn");
    }

    public boolean canSetBoard(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.set.board");
    }

    public boolean canSetTaxes(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.set.taxes");
    }

    public boolean canToggleFlags(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.toggle");
    }

    public boolean canManagePlots(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.plot.manage");
    }

    public boolean canWithdrawFromBank(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.withdraw");
    }

    public boolean canDepositToBank(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.deposit") || hasResident(playerUuid);
    }

    // Role-based checks
    public boolean isManager(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.manager") || isMayor(playerUuid);
    }

    public boolean isAssistant(UUID playerUuid) {
        return hasPermission(playerUuid, "towny.town.assistant") || isManager(playerUuid);
    }

    public boolean isOfficer(UUID playerUuid) {
        return isAssistant(playerUuid) || isManager(playerUuid) || isMayor(playerUuid);
    }
}
