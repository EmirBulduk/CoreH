package org.arch.me.models;

import org.bukkit.Location;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BufferZone {
    private UUID uuid;
    private String name;
    private String worldName;
    private int x1, z1, x2, z2; // Chunk coordinates
    private UUID createdBy;
    private long createdAt;
    private Map<String, Boolean> flags;
    private String reason;

    public BufferZone(UUID uuid, String name, String worldName, int x1, int z1, int x2, int z2, UUID createdBy) {
        this.uuid = uuid;
        this.name = name;
        this.worldName = worldName;
        this.x1 = Math.min(x1, x2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.z2 = Math.max(z1, z2);
        this.createdBy = createdBy;
        this.createdAt = System.currentTimeMillis();
        this.flags = new HashMap<>();
        // Default settings: no building/breaking, but interaction allowed
        this.flags.put("build", false);
        this.flags.put("destroy", false);
        this.flags.put("interact", true);
        this.flags.put("switch", true);
        this.flags.put("itemuse", true);
        // New buffer zone features
        this.flags.put("regeneration", true);
        this.flags.put("no_starve", true);
        this.flags.put("mob_spawning", false);
    }

    public boolean containsChunk(String world, int chunkX, int chunkZ) {
        return worldName.equals(world) &&
               chunkX >= x1 && chunkX <= x2 &&
               chunkZ >= z1 && chunkZ <= z2;
    }

    public boolean containsLocation(Location location) {
        if (!location.getWorld().getName().equals(worldName)) return false;
        int chunkX = location.getChunk().getX();
        int chunkZ = location.getChunk().getZ();
        return containsChunk(worldName, chunkX, chunkZ);
    }

    // Getters and Setters
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWorldName() { return worldName; }
    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }

    public UUID getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }

    public Map<String, Boolean> getFlags() { return flags; }
    public void setFlags(Map<String, Boolean> flags) { this.flags = flags; }
    public boolean getFlag(String flag) { return flags.getOrDefault(flag, false); }
    public void setFlag(String flag, boolean value) { flags.put(flag, value); }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getChunkCount() {
        return (x2 - x1 + 1) * (z2 - z1 + 1);
    }
}
