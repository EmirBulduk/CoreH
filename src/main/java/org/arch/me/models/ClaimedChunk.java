package org.arch.me.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

// Claimed chunk model
public class ClaimedChunk {
    private long id;
    private String world;
    private int x;
    private int z;
    private UUID townUuid;
    private String plotType;
    private BigDecimal plotPrice;
    private UUID ownerUuid;
    private Timestamp claimedDate;
    private Set<String> permissions;
    private Map<String, Boolean> flags;
    private Map<String, Object> metadata;
    private UUID uuid;

    public ClaimedChunk(String world, int x, int z, UUID townUuid) {
        this.uuid = UUID.randomUUID();
        this.world = world;
        this.x = x;
        this.z = z;
        this.townUuid = townUuid;
        this.plotType = "residential";
        this.plotPrice = BigDecimal.ZERO;
        this.claimedDate = new Timestamp(System.currentTimeMillis());
        this.permissions = new HashSet<>();
        this.flags = new HashMap<>();
        this.metadata = new HashMap<>();

        // Default flags
        flags.put("build", false);
        flags.put("destroy", false);
        flags.put("switch", false);
        flags.put("itemuse", false);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
    // Getter/setter metodları ekleyin:
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    // getChunkX ve getChunkZ metodları ekleyin:
    public int getChunkX() {
        return x;
    }

    public int getChunkZ() {
        return z;
    }

    public String getWorldName() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public UUID getTownUuid() {
        return townUuid;
    }

    public void setTownUuid(UUID townUuid) {
        this.townUuid = townUuid;
    }

    public String getPlotType() {
        return plotType;
    }

    public void setPlotType(String plotType) {
        this.plotType = plotType;
    }

    public BigDecimal getPlotPrice() {
        return plotPrice;
    }

    public void setPlotPrice(BigDecimal plotPrice) {
        this.plotPrice = plotPrice;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public Timestamp getClaimedDate() {
        return claimedDate;
    }

    public void setClaimedDate(Timestamp claimedDate) {
        this.claimedDate = claimedDate;
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

    // Utility methods
    public boolean hasOwner() {
        return ownerUuid != null;
    }

    public boolean isOwner(UUID playerUuid) {
        return ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    public boolean getFlag(String flag) {
        return flags.getOrDefault(flag, false);
    }

    public void setFlag(String flag, boolean value) {
        flags.put(flag, value);
    }

    public boolean isForSale() {
        return plotPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    public Location getLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld != null) {
            return new Location(bukkitWorld, x * 16, 64, z * 16);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClaimedChunk that = (ClaimedChunk) o;
        return x == that.x && z == that.z && Objects.equals(world, that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, z);
    }
}
