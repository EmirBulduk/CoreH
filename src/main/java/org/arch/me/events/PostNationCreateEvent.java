package org.arch.me.events;

import org.arch.me.models.Nation;
import org.arch.me.models.Town;
import org.bukkit.entity.Player;

/**
 * Event fired after a nation has been successfully created
 */
class PostNationCreateEvent extends NationCreateEvent {

    public PostNationCreateEvent(Player king, String nationName, Town capitalTown, Nation nation) {
        super(king, nationName, capitalTown, nation);
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
