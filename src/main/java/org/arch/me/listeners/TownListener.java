package org.arch.me.listeners;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.ClaimedChunk;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.arch.me.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class TownListener implements Listener {

    private final EnhancedCoreH plugin;

    public TownListener(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if player moved to a different chunk
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }

        Player player = event.getPlayer();
        showChunkInfo(player, event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        showChunkInfo(player, event.getTo());
    }

    private void showChunkInfo(Player player, org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) return;

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(location);

        if (chunk == null) {
            // Player entered wilderness
            MessageUtil.sendActionBar(player, "§7You entered the §cWilderness");
            return;
        }

        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        if (town == null) return;

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        boolean isResident = townyPlayer != null && town.hasResident(player.getUniqueId());
        boolean isOwner = chunk.isOwner(player.getUniqueId());

        String message = formatChunkMessage(town, chunk, isResident, isOwner);
        MessageUtil.sendActionBar(player, message);

        // Send additional info if configured
        if (plugin.getConfig().getBoolean("town.show-detailed-entry-message", false)) {
            sendDetailedChunkInfo(player, town, chunk, isResident, isOwner);
        }
    }

    private String formatChunkMessage(Town town, ClaimedChunk chunk, boolean isResident, boolean isOwner) {
        StringBuilder message = new StringBuilder();

        if (isOwner) {
            message.append("§a~ Your Plot in ");
        } else if (isResident) {
            message.append("§e~ ");
        } else {
            message.append("§c~ ");
        }

        message.append(town.getName());

        if (chunk.hasOwner() && !isOwner) {
            String ownerName = getOwnerName(chunk);
            message.append(" §7(").append(ownerName).append("'s plot)");
        }

        if (!chunk.getPlotType().equals("residential")) {
            message.append(" §7[").append(chunk.getPlotType().toUpperCase()).append("]");
        }

        return message.toString();
    }

    private void sendDetailedChunkInfo(Player player, Town town, ClaimedChunk chunk, boolean isResident, boolean isOwner) {
        player.sendMessage("§6=== Entering " + town.getName() + " ===");

        if (chunk.hasOwner()) {
            player.sendMessage("§ePlot Owner: §f" + getOwnerName(chunk));
        } else {
            player.sendMessage("§ePlot Owner: §fTown");
        }

        player.sendMessage("§ePlot Type: §f" + chunk.getPlotType());

        if (chunk.isForSale()) {
            player.sendMessage("§ePlot Price: §f" + plugin.getEconomyManager().format(chunk.getPlotPrice()));
        }

        // Show permissions for non-residents
        if (!isResident && !isOwner) {
            showPlotPermissions(player, chunk, town);
        }

        // Show town info
        if (!isResident) {
            showTownInfo(player, town);
        }
    }

    private void showPlotPermissions(Player player, ClaimedChunk chunk, Town town) {
        StringBuilder perms = new StringBuilder("§ePermissions: §f");

        boolean canBuild = chunk.getFlag("build") || town.getFlag("outsider_build");
        boolean canDestroy = chunk.getFlag("destroy") || town.getFlag("outsider_destroy");
        boolean canSwitch = chunk.getFlag("switch") || town.getFlag("outsider_switch");

        perms.append("Build: ").append(canBuild ? "§aYes" : "§cNo").append(" §f");
        perms.append("Break: ").append(canDestroy ? "§aYes" : "§cNo").append(" §f");
        perms.append("Use: ").append(canSwitch ? "§aYes" : "§cNo");

        player.sendMessage(perms.toString());
    }

    private void showTownInfo(Player player, Town town) {
        player.sendMessage("§eMayor: §f" + getMayorName(town));
        player.sendMessage("§eOpen: §f" + (town.isOpen() ? "§aYes" : "§cNo"));

        if (town.getBoard() != null && !town.getBoard().isEmpty()) {
            player.sendMessage("§eBoard: §f" + town.getBoard());
        }
    }

    private String getOwnerName(ClaimedChunk chunk) {
        if (!chunk.hasOwner()) return "Town";

        Player owner = plugin.getServer().getPlayer(chunk.getOwnerUuid());
        if (owner != null) {
            return owner.getName();
        }

        TownyPlayer townyOwner = plugin.getPlayerManager().getPlayer(chunk.getOwnerUuid());
        return townyOwner != null ? townyOwner.getName() : "Unknown";
    }

    private String getMayorName(Town town) {
        Player mayor = plugin.getServer().getPlayer(town.getMayorUuid());
        if (mayor != null) {
            return mayor.getName();
        }

        TownyPlayer townyMayor = plugin.getPlayerManager().getPlayer(town.getMayorUuid());
        return townyMayor != null ? townyMayor.getName() : "Unknown";
    }
}


