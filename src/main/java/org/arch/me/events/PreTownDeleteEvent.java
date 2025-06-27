package org.arch.me.events;

import org.arch.me.models.Town;
import org.bukkit.entity.Player;

/**
 * Event fired before a town is deleted (cancellable)
 */
class PreTownDeleteEvent extends TownDeleteEvent {

    public PreTownDeleteEvent(Player deleter, Town town, boolean isForced) {
        super(deleter, town, isForced);
    }
}
