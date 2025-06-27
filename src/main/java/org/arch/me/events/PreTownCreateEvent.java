package org.arch.me.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Event fired before a town is created (cancellable)
 */
class PreTownCreateEvent extends TownCreateEvent {

    public PreTownCreateEvent(Player mayor, String townName, Location spawn) {
        super(mayor, townName, spawn);
    }
}
