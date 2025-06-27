package org.arch.me.events;

import org.arch.me.models.Town;
import org.bukkit.entity.Player;

/**
 * Event fired before a nation is created (cancellable)
 */
class PreNationCreateEvent extends NationCreateEvent {

    public PreNationCreateEvent(Player king, String nationName, Town capitalTown) {
        super(king, nationName, capitalTown);
    }
}
