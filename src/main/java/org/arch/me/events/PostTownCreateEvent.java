package org.arch.me.events;

import org.arch.me.models.Town;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Event fired after a town has been successfully created
 */
class PostTownCreateEvent extends TownCreateEvent {

    public PostTownCreateEvent(Player mayor, String townName, Location spawn, Town town) {
        super(mayor, townName, spawn, town);
    }

    @Override
    public void setCancelled(boolean cancelled) {
        // Post events cannot be cancelled
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
