package org.arch.me.events;

import org.arch.me.models.Town;
import org.bukkit.entity.Player;

/**
 * Event fired after a town has been successfully deleted
 */
class PostTownDeleteEvent extends TownDeleteEvent {

    public PostTownDeleteEvent(Player deleter, Town town, boolean isForced) {
        super(deleter, town, isForced);
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
