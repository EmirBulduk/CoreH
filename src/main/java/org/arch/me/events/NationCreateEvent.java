package org.arch.me.events;

import org.arch.me.models.Nation;
import org.arch.me.models.Town;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a nation is created
 */
class NationCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player king;
    private final String nationName;
    private final Town capitalTown;
    private Nation nation;
    private String cancelReason;

    public NationCreateEvent(Player king, String nationName, Town capitalTown) {
        this.king = king;
        this.nationName = nationName;
        this.capitalTown = capitalTown;
    }

    public NationCreateEvent(Player king, String nationName, Town capitalTown, Nation nation) {
        this.king = king;
        this.nationName = nationName;
        this.capitalTown = capitalTown;
        this.nation = nation;
    }

    /**
     * Get the player who is creating the nation (king)
     *
     * @return the king
     */
    public Player getKing() {
        return king;
    }

    /**
     * Get the name of the nation being created
     *
     * @return the nation name
     */
    public String getNationName() {
        return nationName;
    }

    /**
     * Get the capital town of the nation
     *
     * @return the capital town
     */
    public Town getCapitalTown() {
        return capitalTown;
    }

    /**
     * Get the nation object (may be null in pre-create events)
     *
     * @return the nation or null
     */
    public Nation getNation() {
        return nation;
    }

    /**
     * Set the nation object
     *
     * @param nation the nation
     */
    public void setNation(Nation nation) {
        this.nation = nation;
    }

    /**
     * Get the reason for cancellation
     *
     * @return the cancel reason or null
     */
    public String getCancelReason() {
        return cancelReason;
    }

    /**
     * Set the reason for cancellation
     *
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
