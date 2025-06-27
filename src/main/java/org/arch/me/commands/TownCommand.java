package org.arch.me.commands;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TownCommand implements CommandExecutor, TabCompleter {

    private final EnhancedCoreH plugin;

    public TownCommand(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("general.player-only"));
            return true;
        }

        if (args.length == 0) {
            showTownInfo(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create", "new" -> handleCreate(player, args);
            case "delete", "disband" -> handleDelete(player, args);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player, args);
            case "invite" -> handleInvite(player, args);
            case "kick" -> handleKick(player, args);
            case "claim" -> handleClaim(player, args);
            case "unclaim" -> handleUnclaim(player, args);
            case "spawn" -> handleSpawn(player, args);
            case "setspawn" -> handleSetSpawn(player, args);
            case "deposit" -> handleDeposit(player, args);
            case "withdraw" -> handleWithdraw(player, args);
            case "set" -> handleSet(player, args);
            case "toggle" -> handleToggle(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player, args);
            default -> showHelp(player);
        }

        return true;
    }

    private void showTownInfo(Player player) {
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

        player.sendMessage("§6=== Town Info: " + town.getName() + " ===");
        player.sendMessage("§eMayor: §f" + getMayorName(town));
        player.sendMessage("§eResidents: §f" + town.getResidentCount() + "/" + town.getMaxResidents());
        player.sendMessage("§eChunks: §f" + town.getClaimedChunkCount());
        player.sendMessage("§eBalance: §f" + plugin.getEconomyManager().format(town.getBalance()));
        player.sendMessage("§eOpen: §f" + (town.isOpen() ? "Yes" : "No"));
        player.sendMessage("§ePublic: §f" + (town.isPublic() ? "Yes" : "No"));
        if (town.hasNation()) {
            player.sendMessage("§eNation: §f" + getNationName(town));
        }
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /town create <name>");
            return;
        }

        String townName = args[1];
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());

        if (townyPlayer != null && townyPlayer.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.already-in-town"));
            return;
        }

        BigDecimal cost = BigDecimal.valueOf(plugin.getConfigManager().getEconomyValue("town-creation-cost"));
        if (!plugin.getEconomyManager().hasPlayerBalance(player.getUniqueId(), cost)) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.insufficient-funds"));
            return;
        }

        plugin.getTownManager().createTown(townName, player.getUniqueId(), player.getLocation())
                .thenAccept(town -> {
                    if (town != null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("town.created", townName));
                    } else {
                        player.sendMessage("§cFailed to create town. Name might already exist.");
                    }
                });
    }

    private void handleDelete(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
            return;
        }

        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (town == null || !town.isMayor(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-mayor"));
            return;
        }

        plugin.getTownManager().deleteTown(town.getUuid(), false)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getConfigManager().getMessage("town.deleted", town.getName()));
                    } else {
                        player.sendMessage("§cFailed to delete town.");
                    }
                });
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /town join <townname>");
            return;
        }

        String townName = args[1];
        Town town = plugin.getTownManager().getTown(townName);

        if (town == null) {
            player.sendMessage("§cTown not found.");
            return;
        }

        if (!town.isOpen()) {
            player.sendMessage("§cThis town is not open for new residents.");
            return;
        }

        plugin.getTownManager().addPlayerToTown(town.getUuid(), player.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getConfigManager().getMessage("town.joined", town.getName()));
                    } else {
                        player.sendMessage("§cFailed to join town.");
                    }
                });
    }

    private void handleLeave(Player player, String[] args) {
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

        plugin.getTownManager().removePlayerFromTown(town.getUuid(), player.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getConfigManager().getMessage("town.left", town.getName()));
                    } else {
                        player.sendMessage("§cFailed to leave town.");
                    }
                });
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

        plugin.getChunkManager().claimChunk(town.getUuid(), player.getLocation(), player.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getConfigManager().getMessage("town.chunk-claimed", town.getName()));
                    } else {
                        player.sendMessage("§cFailed to claim chunk.");
                    }
                });
    }

    private void handleUnclaim(Player player, String[] args) {
        plugin.getChunkManager().unclaimChunk(player.getLocation(), player.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aChunk unclaimed successfully.");
                    } else {
                        player.sendMessage("§cFailed to unclaim chunk.");
                    }
                });
    }

    private void handleSpawn(Player player, String[] args) {
        String townName = null;
        if (args.length > 1) {
            townName = args[1];
        }

        Town town;
        if (townName != null) {
            town = plugin.getTownManager().getTown(townName);
            if (town == null) {
                player.sendMessage("§cTown not found.");
                return;
            }

            if (!town.isPublic() && !town.hasResident(player.getUniqueId())) {
                player.sendMessage("§cYou cannot teleport to this town's spawn.");
                return;
            }
        } else {
            TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
            if (townyPlayer == null || !townyPlayer.hasTown()) {
                player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
                return;
            }
            town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        }

        if (town == null || town.getSpawn() == null) {
            player.sendMessage("§cTown spawn not set.");
            return;
        }

        player.teleport(town.getSpawn());
        player.sendMessage("§aTeleported to " + town.getName() + " spawn.");
    }

    private void handleSetSpawn(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
            return;
        }

        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (town == null || !town.isMayor(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-mayor"));
            return;
        }

        town.setSpawn(player.getLocation());
        plugin.getTownManager().saveTown(town);
        player.sendMessage("§aTown spawn set to your current location.");
    }

    private void handleDeposit(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /town deposit <amount>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(args[1]);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                player.sendMessage(plugin.getConfigManager().getMessage("general.invalid-amount"));
                return;
            }

            if (plugin.getEconomyManager().transferPlayerToTown(player.getUniqueId(), townyPlayer.getTownUuid(), amount)) {
                player.sendMessage("§aDeposited " + plugin.getEconomyManager().format(amount) + " to town bank.");
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("general.insufficient-funds"));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.invalid-amount"));
        }
    }

    private void handleWithdraw(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /town withdraw <amount>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
            return;
        }

        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (town == null || !town.isMayor(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-mayor"));
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(args[1]);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                player.sendMessage(plugin.getConfigManager().getMessage("general.invalid-amount"));
                return;
            }

            if (plugin.getEconomyManager().transferTownToPlayer(townyPlayer.getTownUuid(), player.getUniqueId(), amount)) {
                player.sendMessage("§aWithdrew " + plugin.getEconomyManager().format(amount) + " from town bank.");
            } else {
                player.sendMessage("§cInsufficient town funds.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.invalid-amount"));
        }
    }

    private void handleSet(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /town set <property> <value>");
            player.sendMessage("§cProperties: name, board, tax, spawn, mayor");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
            return;
        }

        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (town == null || !town.isMayor(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-mayor"));
            return;
        }

        String property = args[1].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        switch (property) {
            case "name" -> {
                if (plugin.getTownManager().townNameExists(value)) {
                    player.sendMessage("§cTown name already exists.");
                    return;
                }
                town.setName(value);
                player.sendMessage("§aTown name changed to: " + value);
            }
            case "board" -> {
                town.setBoard(value);
                player.sendMessage("§aTown board updated.");
            }
            case "tax" -> {
                try {
                    BigDecimal tax = new BigDecimal(value);
                    if (tax.compareTo(BigDecimal.ZERO) < 0) {
                        player.sendMessage("§cTax rate cannot be negative.");
                        return;
                    }
                    town.setTaxRate(tax);
                    player.sendMessage("§aTown tax rate set to: " + tax + "%");
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid tax rate.");
                    return;
                }
            }
            case "mayor" -> {
                Player newMayor = plugin.getServer().getPlayer(value);
                if (newMayor == null) {
                    player.sendMessage("§cPlayer not found.");
                    return;
                }

                if (!town.hasResident(newMayor.getUniqueId())) {
                    player.sendMessage("§cPlayer must be a resident of the town.");
                    return;
                }

                town.setMayorUuid(newMayor.getUniqueId());
                player.sendMessage("§a" + newMayor.getName() + " is now the mayor.");
                newMayor.sendMessage("§aYou are now the mayor of " + town.getName() + "!");
            }
            default -> player.sendMessage("§cInvalid property. Use: name, board, tax, mayor");
        }

        plugin.getTownManager().saveTown(town);
    }

    private void handleToggle(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /town toggle <flag>");
            player.sendMessage("§cFlags: open, public, pvp, explosions, fire, mobspawning");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
            return;
        }

        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (town == null || !town.isMayor(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-mayor"));
            return;
        }

        String flag = args[1].toLowerCase();

        switch (flag) {
            case "open" -> {
                town.setOpen(!town.isOpen());
                player.sendMessage("§aTown is now " + (town.isOpen() ? "open" : "closed") + " to new residents.");
            }
            case "public" -> {
                town.setPublic(!town.isPublic());
                player.sendMessage("§aTown spawn is now " + (town.isPublic() ? "public" : "private") + ".");
            }
            case "pvp", "explosions", "fire", "mobspawning" -> {
                boolean current = town.getFlag(flag);
                town.setFlag(flag, !current);
                player.sendMessage("§a" + flag + " is now " + (!current ? "enabled" : "disabled") + ".");
            }
            default -> player.sendMessage("§cInvalid flag. Use: open, public, pvp, explosions, fire, mobspawning");
        }

        plugin.getTownManager().saveTown(town);
    }

    private void handleInfo(Player player, String[] args) {
        String townName = null;
        if (args.length > 1) {
            townName = args[1];
        }

        Town town;
        if (townName != null) {
            town = plugin.getTownManager().getTown(townName);
            if (town == null) {
                player.sendMessage("§cTown not found.");
                return;
            }
        } else {
            TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
            if (townyPlayer == null || !townyPlayer.hasTown()) {
                player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
                return;
            }
            town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        }

        if (town == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        player.sendMessage("§6=== " + town.getName() + " ===");
        player.sendMessage("§eMayor: §f" + getMayorName(town));
        player.sendMessage("§eResidents (" + town.getResidentCount() + "): §f" + getResidentNames(town));
        player.sendMessage("§eChunks: §f" + town.getClaimedChunkCount());
        player.sendMessage("§eBalance: §f" + plugin.getEconomyManager().format(town.getBalance()));
        player.sendMessage("§eTax Rate: §f" + town.getTaxRate() + "%");
        player.sendMessage("§eOpen: §f" + (town.isOpen() ? "Yes" : "No"));
        player.sendMessage("§ePublic Spawn: §f" + (town.isPublic() ? "Yes" : "No"));

        if (town.hasNation()) {
            player.sendMessage("§eNation: §f" + getNationName(town));
        }

        if (town.getBoard() != null && !town.getBoard().isEmpty()) {
            player.sendMessage("§eBoard: §f" + town.getBoard());
        }

        // Show flags
        StringBuilder flags = new StringBuilder();
        town.getFlags().forEach((flag, value) -> {
            if (flags.length() > 0) flags.append(", ");
            flags.append(flag).append(": ").append(value ? "§aOn" : "§cOff");
        });
        if (flags.length() > 0) {
            player.sendMessage("§eFlags: §f" + flags);
        }
    }

    private void handleList(Player player, String[] args) {
        List<Town> towns = new ArrayList<>(plugin.getTownManager().getAllTowns());

        if (towns.isEmpty()) {
            player.sendMessage("§cNo towns exist.");
            return;
        }

        // Sort by resident count
        towns.sort((t1, t2) -> Integer.compare(t2.getResidentCount(), t1.getResidentCount()));

        player.sendMessage("§6=== Towns (" + towns.size() + ") ===");

        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        int itemsPerPage = 10;
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, towns.size());

        for (int i = startIndex; i < endIndex; i++) {
            Town town = towns.get(i);
            String status = town.isOpen() ? "§aOpen" : "§cClosed";
            player.sendMessage("§e" + (i + 1) + ". §f" + town.getName() +
                    " §7(" + town.getResidentCount() + " residents) " + status);
        }

        int totalPages = (int) Math.ceil((double) towns.size() / itemsPerPage);
        if (totalPages > 1) {
            player.sendMessage("§7Page " + page + " of " + totalPages +
                    " | Use /town list <page> to view other pages");
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /town invite <player>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
            return;
        }

        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (town == null || !town.isMayor(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-mayor"));
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        plugin.getTownManager().addPlayerToTown(town.getUuid(), target.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a" + target.getName() + " has been invited to the town.");
                        target.sendMessage("§aYou have been invited to join " + town.getName() + "!");
                    } else {
                        player.sendMessage("§cFailed to invite player.");
                    }
                });
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /town kick <player>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-in-town"));
            return;
        }

        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (town == null || !town.isMayor(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("town.not-mayor"));
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cYou cannot kick yourself. Use /town leave instead.");
            return;
        }

        plugin.getTownManager().removePlayerFromTown(town.getUuid(), target.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a" + target.getName() + " has been kicked from the town.");
                        target.sendMessage("§cYou have been kicked from " + town.getName() + "!");
                    } else {
                        player.sendMessage("§cFailed to kick player.");
                    }
                });
    }

    private void showHelp(Player player) {
        player.sendMessage("§6=== Town Commands ===");
        player.sendMessage("§e/town §7- Show town info");
        player.sendMessage("§e/town create <name> §7- Create a new town");
        player.sendMessage("§e/town delete §7- Delete your town");
        player.sendMessage("§e/town join <town> §7- Join a town");
        player.sendMessage("§e/town leave §7- Leave your town");
        player.sendMessage("§e/town invite <player> §7- Invite a player");
        player.sendMessage("§e/town kick <player> §7- Kick a player");
        player.sendMessage("§e/town claim §7- Claim current chunk");
        player.sendMessage("§e/town unclaim §7- Unclaim current chunk");
        player.sendMessage("§e/town spawn [town] §7- Teleport to town spawn");
        player.sendMessage("§e/town setspawn §7- Set town spawn");
        player.sendMessage("§e/town deposit <amount> §7- Deposit to town bank");
        player.sendMessage("§e/town withdraw <amount> §7- Withdraw from town bank");
        player.sendMessage("§e/town set <property> <value> §7- Set town property");
        player.sendMessage("§e/town toggle <flag> §7- Toggle town flag");
        player.sendMessage("§e/town info [town] §7- Show town information");
        player.sendMessage("§e/town list [page] §7- List all towns");
    }

    // Utility methods
    private String getMayorName(Town town) {
        Player mayor = plugin.getServer().getPlayer(town.getMayorUuid());
        if (mayor != null) {
            return mayor.getName();
        }

        TownyPlayer townyMayor = plugin.getPlayerManager().getPlayer(town.getMayorUuid());
        return townyMayor != null ? townyMayor.getName() : "Unknown";
    }

    private String getNationName(Town town) {
        if (!town.hasNation()) {
            return "None";
        }

        var nation = plugin.getNationManager().getNation(town.getNationUuid());
        return nation != null ? nation.getName() : "Unknown";
    }

    private String getResidentNames(Town town) {
        StringBuilder residents = new StringBuilder();
        int count = 0;
        int maxDisplay = 5;

        for (UUID residentUuid : town.getResidents()) {
            if (count >= maxDisplay) {
                residents.append(" and ").append(town.getResidentCount() - maxDisplay).append(" more");
                break;
            }

            if (count > 0) residents.append(", ");

            Player player = plugin.getServer().getPlayer(residentUuid);
            if (player != null) {
                residents.append(player.getName());
            } else {
                TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(residentUuid);
                residents.append(townyPlayer != null ? townyPlayer.getName() : "Unknown");
            }
            count++;
        }

        return residents.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "create", "delete", "join", "leave", "invite", "kick",
                    "claim", "unclaim", "spawn", "setspawn", "deposit", "withdraw",
                    "set", "toggle", "info", "list"
            );

            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "join", "info", "spawn" -> {
                    for (Town town : plugin.getTownManager().getAllTowns()) {
                        if (town.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(town.getName());
                        }
                    }
                }
                case "invite", "kick" -> {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                }
                case "set" -> {
                    List<String> properties = Arrays.asList("name", "board", "tax", "mayor");
                    for (String prop : properties) {
                        if (prop.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(prop);
                        }
                    }
                }
                case "toggle" -> {
                    List<String> flags = Arrays.asList("open", "public", "pvp", "explosions", "fire", "mobspawning");
                    for (String flag : flags) {
                        if (flag.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(flag);
                        }
                    }
                }
            }
        }

        return completions;
    }
}