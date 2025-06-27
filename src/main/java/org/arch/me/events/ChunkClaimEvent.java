package org.arch.me.events;

import org.arch.me.models.ClaimedChunk;
import org.arch.me.models.Town;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a chunk is claimed for a town
 */
public class ChunkClaimEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final Town town;
    private final Chunk chunk;
    private ClaimedChunk claimedChunk;
    private String cancelReason;

    public ChunkClaimEvent(Player player, Town town, Chunk chunk) {
        this.player = player;
        this.town = town;
        this.chunk = chunk;
    }

    public ChunkClaimEvent(Player player, Town town, Chunk chunk, ClaimedChunk claimedChunk) {
        this.player = player;
        this.town = town;
        this.chunk = chunk;
        this.claimedChunk = claimedChunk;
    }

    /**
     * Get the player who is claiming the chunk
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the town claiming the chunk
     * @return the town
     */
    public Town getTown() {
        return town;
    }

    /**
     * Get the chunk being claimed
     * @return the chunk
     */
    public Chunk getChunk() {
        return chunk;
    }

    /**
     * Get the claimed chunk object (may be null in pre-claim events)
     * @return the claimed chunk or null
     */
    public ClaimedChunk getClaimedChunk() {
        return claimedChunk;
    }

    /**
     * Set the claimed chunk object
     * @param claimedChunk the claimed chunk
     */
    public void setClaimedChunk(ClaimedChunk claimedChunk) {
        this.claimedChunk = claimedChunk;
    }

    /**
     * Get the reason for cancellation
     * @return the cancel reason or null
     */
    public String getCancelReason() {
        return cancelReason;
    }

    /**
     * Set the reason for cancellation
     * @param reason the cancel reason
     */
    public void setCancelReason(String reason) {
        this.cancelReason = reason;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

