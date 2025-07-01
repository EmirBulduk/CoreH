package org.arch.me.commands;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.ClaimedChunk;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PlotCommand implements CommandExecutor, TabCompleter {

    private final EnhancedCoreH plugin;

    public PlotCommand(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("general.player-only"));
            return true;
        }

        if (args.length == 0) {
            showPlotInfo(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "claim" -> handleClaim(player, args);
            case "unclaim" -> handleUnclaim(player, args);
            case "buy" -> handleBuy(player, args);
            case "sell" -> handleSell(player, args);
            case "forsale" -> handleForSale(player, args);
            case "notforsale" -> handleNotForSale(player, args);
            case "set" -> handleSet(player, args);
            case "toggle" -> handleToggle(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player, args);
            case "perm" -> handlePermission(player, args);
            default -> showHelp(player);
        }

        return true;
    }

    private void showPlotInfo(Player player) {
        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());

        if (chunk == null) {
            player.sendMessage("§cYou are standing in the wilderness.");
            return;
        }

        displayPlotInfo(player, chunk);
    }

    private void handleClaim(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
            return;
        }

        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (town == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        // Check if player has claim permission
        if (!town.isMayor(player.getUniqueId()) && !hasClaimPermission(town, player.getUniqueId())) {
            player.sendMessage("§cYou don't have permission to claim chunks for this town!");
            return;
        }

        // Check if location is in buffer zone
        if (plugin.getBufferZoneManager().isInBufferZone(player.getLocation())) {
            player.sendMessage("§cYou cannot claim chunks in a buffer zone!");
            return;
        }

        // Check if chunk is already claimed
        if (plugin.getChunkManager().isChunkClaimed(player.getLocation())) {
            player.sendMessage("§cThis chunk is already claimed!");
            return;
        }

        // Check if neighboring chunks belong to other towns
        if (hasNeighboringTownChunks(player, town)) {
            player.sendMessage("§cCannot claim chunk adjacent to another town's territory!");
            return;
        }

        // Check buffer zone (configurable distance from other towns)
        int bufferZone = plugin.getConfig().getInt("chunks.buffer-zone", 1);
        if (isWithinBufferZone(player, town, bufferZone)) {
            player.sendMessage("§cCannot claim chunk within " + bufferZone + " chunks of another town!");
            return;
        }

        plugin.getChunkManager().claimChunk(town.getUuid(), player.getLocation(), player.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getConfigManager().getMessage("town.chunk-claimed", town.getName()));
                    } else {
                        player.sendMessage("§cFailed to claim chunk. Check town funds and chunk limits.");
                    }
                });
    }

    private void handleUnclaim(Player player, String[] args) {
        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());
        if (chunk == null) {
            player.sendMessage("§cThis chunk is not claimed.");
            return;
        }

        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        if (town == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        // Check unclaim permissions
        if (!town.isMayor(player.getUniqueId()) && !chunk.isOwner(player.getUniqueId())) {
            player.sendMessage("§cYou don't have permission to unclaim this chunk!");
            return;
        }

        plugin.getChunkManager().unclaimChunk(player.getLocation(), player.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aChunk unclaimed successfully.");
                    } else {
                        player.sendMessage("§cFailed to unclaim chunk.");
                    }
                });
    }

    private void handleBuy(Player player, String[] args) {
        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());
        if (chunk == null) {
            player.sendMessage("§cThis chunk is not claimed by any town.");
            return;
        }

        if (!chunk.isForSale()) {
            player.sendMessage("§cThis plot is not for sale.");
            return;
        }

        if (chunk.hasOwner()) {
            player.sendMessage("§cThis plot is already owned.");
            return;
        }

        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        if (town == null || !town.hasResident(player.getUniqueId())) {
            player.sendMessage("§cYou must be a resident of " + (town != null ? town.getName() : "this town") + " to buy plots!");
            return;
        }

        plugin.getChunkManager().buyPlot(player.getLocation(), player.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aYou have successfully purchased this plot for " +
                                plugin.getEconomyManager().format(chunk.getPlotPrice()) + "!");
                    } else {
                        player.sendMessage("§cFailed to purchase plot. Check your balance.");
                    }
                });
    }

    private void handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /plot sell <price>");
            return;
        }

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());
        if (chunk == null) {
            player.sendMessage("§cThis chunk is not claimed.");
            return;
        }

        if (!chunk.isOwner(player.getUniqueId())) {
            player.sendMessage("§cYou don't own this plot!");
            return;
        }

        try {
            BigDecimal price = new BigDecimal(args[1]);
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                player.sendMessage("§cPrice must be greater than 0!");
                return;
            }

            plugin.getChunkManager().sellPlot(player.getLocation(), player.getUniqueId(), price)
                    .thenAccept(success -> {
                        if (success) {
                            player.sendMessage("§aPlot put up for sale for " + plugin.getEconomyManager().format(price) + "!");
                        } else {
                            player.sendMessage("§cFailed to put plot for sale.");
                        }
                    });
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid price format!");
        }
    }

    private void handleForSale(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /plot forsale <price>");
            return;
        }

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());
        if (chunk == null) {
            player.sendMessage("§cThis chunk is not claimed.");
            return;
        }

        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        if (town == null || !town.isMayor(player.getUniqueId())) {
            player.sendMessage("§cYou must be the mayor to set plots for sale!");
            return;
        }

        if (chunk.hasOwner()) {
            player.sendMessage("§cThis plot is already owned! Use /plot sell instead.");
            return;
        }

        try {
            BigDecimal price = new BigDecimal(args[1]);
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                player.sendMessage("§cPrice must be greater than 0!");
                return;
            }

            chunk.setPlotPrice(price);
            plugin.getChunkManager().saveClaimedChunk(chunk);

            player.sendMessage("§aPlot set for sale at " + plugin.getEconomyManager().format(price) + "!");
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid price format!");
        }
    }

    private void handleNotForSale(Player player, String[] args) {
        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());
        if (chunk == null) {
            player.sendMessage("§cThis chunk is not claimed.");
            return;
        }

        // Check permissions
        if (!chunk.isOwner(player.getUniqueId())) {
            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            if (town == null || !town.isMayor(player.getUniqueId())) {
                player.sendMessage("§cYou don't have permission to modify this plot!");
                return;
            }
        }

        chunk.setPlotPrice(BigDecimal.ZERO);
        plugin.getChunkManager().saveClaimedChunk(chunk);

        player.sendMessage("§aPlot removed from sale!");
    }

    private void handleSet(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /plot set <property> <value>");
            player.sendMessage("§cProperties: type, name");
            return;
        }

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());
        if (chunk == null) {
            player.sendMessage("§cThis chunk is not claimed.");
            return;
        }

        if (!chunk.isOwner(player.getUniqueId())) {
            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            if (town == null || !town.isMayor(player.getUniqueId())) {
                player.sendMessage("§cYou don't have permission to modify this plot!");
                return;
            }
        }

        String property = args[1].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        switch (property) {
            case "type" -> {
                if (isValidPlotType(value)) {
                    chunk.setPlotType(value.toLowerCase());
                    plugin.getChunkManager().saveClaimedChunk(chunk);
                    player.sendMessage("§aPlot type set to: " + value);
                } else {
                    player.sendMessage("§cInvalid plot type! Valid types: residential, commercial, industrial, embassy");
                }
            }
            case "name" -> {
                // Store plot name in metadata
                chunk.getMetadata().put("name", value);
                plugin.getChunkManager().saveClaimedChunk(chunk);
                player.sendMessage("§aPlot name set to: " + value);
            }
            default -> player.sendMessage("§cInvalid property. Use: type, name");
        }
    }

    private void handleToggle(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /plot toggle <flag>");
            player.sendMessage("§cFlags: build, destroy, switch, itemuse");
            return;
        }

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());
        if (chunk == null) {
            player.sendMessage("§cThis chunk is not claimed.");
            return;
        }

        if (!chunk.isOwner(player.getUniqueId())) {
            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            if (town == null || !town.isMayor(player.getUniqueId())) {
                player.sendMessage("§cYou don't have permission to modify this plot!");
                return;
            }
        }

        String flag = args[1].toLowerCase();

        switch (flag) {
            case "build", "destroy", "switch", "itemuse" -> {
                boolean current = chunk.getFlag(flag);
                chunk.setFlag(flag, !current);
                plugin.getChunkManager().saveClaimedChunk(chunk);
                player.sendMessage("§a" + flag + " is now " + (!current ? "enabled" : "disabled") + " for this plot.");
            }
            default -> player.sendMessage("§cInvalid flag. Use: build, destroy, switch, itemuse");
        }
    }

    private void handleInfo(Player player, String[] args) {
        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());

        if (chunk == null) {
            player.sendMessage("§cYou are standing in the wilderness.");
            displayWildernessInfo(player);
            return;
        }

        displayPlotInfo(player, chunk);
    }

    private void handleList(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        List<ClaimedChunk> ownedPlots = plugin.getChunkManager().getChunksByOwner(player.getUniqueId());

        if (ownedPlots.isEmpty()) {
            player.sendMessage("§cYou don't own any plots.");
            return;
        }

        player.sendMessage("§6=== Your Plots (" + ownedPlots.size() + ") ===");

        for (int i = 0; i < ownedPlots.size() && i < 10; i++) {
            ClaimedChunk chunk = ownedPlots.get(i);
            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            String townName = town != null ? town.getName() : "Unknown";
            String plotName = (String) chunk.getMetadata().getOrDefault("name", "Unnamed");

            player.sendMessage("§e" + (i + 1) + ". §f" + plotName + " §7(" + chunk.getPlotType() +
                    ") in " + townName + " at " + chunk.getWorldName() + " " + chunk.getX() + "," + chunk.getZ());
        }

        if (ownedPlots.size() > 10) {
            player.sendMessage("§7... and " + (ownedPlots.size() - 10) + " more plots");
        }
    }

    private void handlePermission(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /plot perm <add|remove> <permission>");
            return;
        }

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());
        if (chunk == null) {
            player.sendMessage("§cThis chunk is not claimed.");
            return;
        }

        if (!chunk.isOwner(player.getUniqueId())) {
            player.sendMessage("§cYou don't own this plot!");
            return;
        }

        String action = args[1].toLowerCase();
        String permission = args[2];

        switch (action) {
            case "add" -> {
                chunk.getPermissions().add(permission);
                plugin.getChunkManager().saveClaimedChunk(chunk);
                player.sendMessage("§aAdded permission: " + permission);
            }
            case "remove" -> {
                chunk.getPermissions().remove(permission);
                plugin.getChunkManager().saveClaimedChunk(chunk);
                player.sendMessage("§aRemoved permission: " + permission);
            }
            default -> player.sendMessage("§cUse 'add' or 'remove'");
        }
    }

    // Utility methods
    private boolean hasNeighboringTownChunks(Player player, Town playerTown) {
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        // Check all 8 adjacent chunks
        int[][] directions = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

        for (int[] dir : directions) {
            int adjX = chunkX + dir[0];
            int adjZ = chunkZ + dir[1];

            ClaimedChunk adjChunk = plugin.getChunkManager().getClaimedChunk(world, adjX, adjZ);
            if (adjChunk != null && !adjChunk.getTownUuid().equals(playerTown.getUuid())) {
                return true; // Found neighboring chunk from different town
            }
        }

        return false;
    }

    private boolean isWithinBufferZone(Player player, Town playerTown, int bufferZone) {
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        String world = player.getWorld().getName();

        // Check all chunks within buffer zone radius
        for (int x = chunkX - bufferZone; x <= chunkX + bufferZone; x++) {
            for (int z = chunkZ - bufferZone; z <= chunkZ + bufferZone; z++) {
                if (x == chunkX && z == chunkZ) continue; // Skip the chunk being claimed

                ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(world, x, z);
                if (chunk != null && !chunk.getTownUuid().equals(playerTown.getUuid())) {
                    return true; // Found chunk from different town within buffer zone
                }
            }
        }

        return false;
    }

    private boolean hasClaimPermission(Town town, @NotNull UUID playerUuid) {
        // Check if player has claim permission through rank system
        return plugin.getRankManager().playerHasPermission(playerUuid, "towny.claim") ||
                town.isMayor(playerUuid);
    }

    private boolean isValidPlotType(String type) {
        List<String> validTypes = Arrays.asList("residential", "commercial", "industrial", "embassy");
        return validTypes.contains(type.toLowerCase());
    }

    private void displayPlotInfo(Player player, ClaimedChunk chunk) {
        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        String townName = town != null ? town.getName() : "Unknown";

        player.sendMessage("§6=== Plot Info ===");
        player.sendMessage("§eTown: §f" + townName);
        player.sendMessage("§eType: §f" + chunk.getPlotType());
        player.sendMessage("§eOwner: §f" + (chunk.hasOwner() ? getOwnerName(chunk) : "Town"));
        player.sendMessage("§eLocation: §f" + chunk.getWorldName() + " " + chunk.getX() + "," + chunk.getZ());

        if (chunk.isForSale()) {
            player.sendMessage("§ePrice: §f" + plugin.getEconomyManager().format(chunk.getPlotPrice()));
        }

        String plotName = (String) chunk.getMetadata().getOrDefault("name", null);
        if (plotName != null) {
            player.sendMessage("§eName: §f" + plotName);
        }

        // Show permissions
        StringBuilder flags = new StringBuilder();
        chunk.getFlags().forEach((flag, value) -> {
            if (flags.length() > 0) flags.append(", ");
            flags.append(flag).append(": ").append(value ? "§aOn" : "§cOff");
        });
        if (flags.length() > 0) {
            player.sendMessage("§eFlags: §f" + flags);
        }
    }

    private void displayWildernessInfo(Player player) {
        player.sendMessage("§6=== Wilderness ===");
        player.sendMessage("§eProtection: §cNone");
        player.sendMessage("§eBuild: §f" + (plugin.getConfig().getBoolean("chunks.wilderness-permissions.build") ? "§aAllowed" : "§cDenied"));
        player.sendMessage("§eDestroy: §f" + (plugin.getConfig().getBoolean("chunks.wilderness-permissions.destroy") ? "§aAllowed" : "§cDenied"));
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

    private void showHelp(Player player) {
        player.sendMessage("§6=== Plot Commands ===");
        player.sendMessage("§e/plot §7- Show current plot info");
        player.sendMessage("§e/plot claim §7- Claim current chunk for town");
        player.sendMessage("§e/plot unclaim §7- Unclaim current chunk");
        player.sendMessage("§e/plot buy §7- Buy current plot");
        player.sendMessage("§e/plot sell <price> §7- Sell your plot");
        player.sendMessage("§e/plot forsale <price> §7- Set town plot for sale");
        player.sendMessage("§e/plot notforsale §7- Remove plot from sale");
        player.sendMessage("§e/plot set <property> <value> §7- Set plot property");
        player.sendMessage("§e/plot toggle <flag> §7- Toggle plot flag");
        player.sendMessage("§e/plot info §7- Show detailed plot info");
        player.sendMessage("§e/plot list §7- List your owned plots");
        player.sendMessage("§e/plot perm <add|remove> <perm> §7- Manage plot permissions");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "claim", "unclaim", "buy", "sell", "forsale", "notforsale",
                    "set", "toggle", "info", "list", "perm"
            );

            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "set" -> {
                    List<String> properties = Arrays.asList("type", "name");
                    for (String prop : properties) {
                        if (prop.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(prop);
                        }
                    }
                }
                case "toggle" -> {
                    List<String> flags = Arrays.asList("build", "destroy", "switch", "itemuse");
                    for (String flag : flags) {
                        if (flag.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(flag);
                        }
                    }
                }
                case "perm" -> {
                    List<String> actions = Arrays.asList("add", "remove");
                    for (String action : actions) {
                        if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(action);
                        }
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("type")) {
            List<String> types = Arrays.asList("residential", "commercial", "industrial", "embassy");
            for (String type : types) {
                if (type.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(type);
                }
            }
        }

        return completions;
    }
}
