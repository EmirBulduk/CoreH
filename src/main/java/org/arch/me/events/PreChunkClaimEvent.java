package org.arch.me.events;

import org.arch.me.models.Town;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

/**
 * Event fired before a chunk is claimed (cancellable)
 */
class PreChunkClaimEvent extends ChunkClaimEvent {

    public PreChunkClaimEvent(Player player, Town town, Chunk chunk) {
        super(player, town, chunk);
    }
}
