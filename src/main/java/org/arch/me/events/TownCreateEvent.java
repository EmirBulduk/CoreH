package org.arch.me.events;

import org.arch.me.models.Town;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a town is created
 */
public class TownCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player mayor;
    private final String townName;
    private final Location spawn;
    private Town town;
    private String cancelReason;

    public TownCreateEvent(Player mayor, String townName, Location spawn) {
        this.mayor = mayor;
        this.townName = townName;
        this.spawn = spawn;
    }

    public TownCreateEvent(Player mayor, String townName, Location spawn, Town town) {
        this.mayor = mayor;
        this.townName = townName;
        this.spawn = spawn;
        this.town = town;
    }

    /**
     * Get the player who is creating the town (mayor)
     * @return the mayor
     */
    public Player getMayor() {
        return mayor;
    }

    /**
     * Get the name of the town being created
     * @return the town name
     */
    public String getTownName() {
        return townName;
    }

    /**
     * Get the spawn location of the town
     * @return the spawn location
     */
    public Location getSpawn() {
        return spawn;
    }

    /**
     * Get the town object (may be null in pre-create events)
     * @return the town or null
     */
    public Town getTown() {
        return town;
    }

    /**
     * Set the town object
     * @param town the town
     */
    public void setTown(Town town) {
        this.town = town;
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

/**
 * Event fired when a town is deleted
 */
