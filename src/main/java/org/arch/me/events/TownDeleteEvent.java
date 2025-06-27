package org.arch.me.events;

import org.arch.me.models.Town;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

class TownDeleteEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player deleter;
    private final Town town;
    private final boolean isForced;
    private String cancelReason;

    public TownDeleteEvent(Player deleter, Town town, boolean isForced) {
        this.deleter = deleter;
        this.town = town;
        this.isForced = isForced;
    }

    /**
     * Get the player who is deleting the town (may be null for admin deletion)
     * @return the deleter or null
     */
    public Player getDeleter() {
        return deleter;
    }

    /**
     * Get the town being deleted
     * @return the town
     */
    public Town getTown() {
        return town;
    }

    /**
     * Check if this is a forced deletion (admin)
     * @return true if forced
     */
    public boolean isForced() {
        return isForced;
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

