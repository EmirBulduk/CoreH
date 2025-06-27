package org.arch.me.events;

import org.arch.me.models.ClaimedChunk;
import org.arch.me.models.Town;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

/**
 * Event fired after a chunk has been successfully claimed
 */
class PostChunkClaimEvent extends ChunkClaimEvent {

    public PostChunkClaimEvent(Player player, Town town, Chunk chunk, ClaimedChunk claimedChunk) {
        super(player, town, chunk, claimedChunk);
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
