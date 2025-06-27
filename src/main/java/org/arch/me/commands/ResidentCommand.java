package org.arch.me.commands;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Nation;
import org.arch.me.models.Rank;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.arch.me.util.MessageUtil;
import org.arch.me.util.TeleportationUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class ResidentCommand implements CommandExecutor, TabCompleter {

    private final EnhancedCoreH plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");

    public ResidentCommand(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                showResidentInfo(player, player);
            } else {
                MessageUtil.sendError(sender, "Console must specify a player name!");
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender, args);
            case "friend" -> handleFriend(sender, args);
            case "spawn" -> handleSpawn(sender, args);
            case "set" -> handleSet(sender, args);
            case "toggle" -> handleToggle(sender, args);
            case "tax" -> handleTax(sender, args);
            case "plots" -> handlePlots(sender, args);
            case "jail" -> handleJail(sender, args);
            case "unjail" -> handleUnjail(sender, args);
            default -> {
                // Try to show info for specified player
                Player target = plugin.getServer().getPlayer(subCommand);
                OfflinePlayer offlineTarget = plugin.getServer().getOfflinePlayer(subCommand);

                if (target != null) {
                    showResidentInfo(sender, target);
                } else if (offlineTarget.hasPlayedBefore()) {
                    showOfflineResidentInfo(sender, offlineTarget);
                } else {
                    showHelp(sender);
                }
            }
        }

        return true;
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player player) {
                showResidentInfo(sender, player);
            } else {
                MessageUtil.sendError(sender, "Console must specify a player name!");
            }
            return;
        }

        String playerName = args[1];
        Player target = plugin.getServer().getPlayer(playerName);
        OfflinePlayer offlineTarget = plugin.getServer().getOfflinePlayer(playerName);

        if (target != null) {
            showResidentInfo(sender, target);
        } else if (offlineTarget.hasPlayedBefore()) {
            showOfflineResidentInfo(sender, offlineTarget);
        } else {
            MessageUtil.sendError(sender, "Player not found!");
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        String filter = args.length > 1 ? args[1].toLowerCase() : "all";
        int page = 1;

        if (args.length > 2) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        List<TownyPlayer> residents = new ArrayList<>();

        switch (filter) {
            case "online" -> {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    TownyPlayer resident = plugin.getPlayerManager().getPlayer(player.getUniqueId());
                    if (resident != null) {
                        residents.add(resident);
                    }
                }
                MessageUtil.sendMessage(sender, "§6=== Online Residents (" + residents.size() + ") ===");
            }
            case "mayors" -> {
                for (Town town : plugin.getTownManager().getAllTowns()) {
                    TownyPlayer mayor = plugin.getPlayerManager().getPlayer(town.getMayorUuid());
                    if (mayor != null) {
                        residents.add(mayor);
                    }
                }
                MessageUtil.sendMessage(sender, "§6=== All Mayors (" + residents.size() + ") ===");
            }
            case "kings" -> {
                for (Nation nation : plugin.getNationManager().getAllNations()) {
                    TownyPlayer king = plugin.getPlayerManager().getPlayer(nation.getKingUuid());
                    if (king != null) {
                        residents.add(king);
                    }
                }
                MessageUtil.sendMessage(sender, "§6=== All Kings (" + residents.size() + ") ===");
            }
            default -> {
                residents.addAll(plugin.getPlayerManager().getAllPlayers());
                MessageUtil.sendMessage(sender, "§6=== All Residents (" + residents.size() + ") ===");
            }
        }

        if (residents.isEmpty()) {
            MessageUtil.sendMessage(sender, "§cNo residents found!");
            return;
        }

        // Sort by name
        residents.sort((r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));

        // Pagination
        int itemsPerPage = 10;
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, residents.size());

        for (int i = startIndex; i < endIndex; i++) {
            TownyPlayer resident = residents.get(i);
            String status = resident.isOnline() ? "§aOnline" : "§7Offline";
            String townInfo = getTownInfo(resident);

            MessageUtil.sendMessage(sender, "§e" + (i + 1) + ". §f" + resident.getName() +
                    " " + status + " " + townInfo);
        }

        int totalPages = (int) Math.ceil((double) residents.size() / itemsPerPage);
        if (totalPages > 1) {
            MessageUtil.sendMessage(sender, "§7Page " + page + " of " + totalPages +
                    " | Use /resident list " + filter + " <page> to view other pages");
        }
    }

    private void handleFriend(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendError(sender, "Only players can use friend commands!");
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendError(sender, "Usage: /resident friend <add|remove|list> [player]");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "add" -> {
                String friendName = args[2];
                Player friend = plugin.getServer().getPlayer(friendName);

                if (friend == null) {
                    MessageUtil.sendError(player, "Player not found or not online!");
                    return;
                }

                if (friend.equals(player)) {
                    MessageUtil.sendError(player, "You cannot add yourself as a friend!");
                    return;
                }

                TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
                if (townyPlayer.getPermissions().contains("friend." + friend.getUniqueId())) {
                    MessageUtil.sendError(player, friend.getName() + " is already your friend!");
                    return;
                }

                townyPlayer.addPermission("friend." + friend.getUniqueId());
                plugin.getPlayerManager().savePlayer(townyPlayer);

                MessageUtil.sendSuccess(player, "Added " + friend.getName() + " as a friend!");
                MessageUtil.sendMessage(friend, "§a" + player.getName() + " added you as a friend!");
            }
            case "remove" -> {
                String friendName = args[2];
                OfflinePlayer friend = plugin.getServer().getOfflinePlayer(friendName);

                TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
                if (!townyPlayer.getPermissions().contains("friend." + friend.getUniqueId())) {
                    MessageUtil.sendError(player, friend.getName() + " is not your friend!");
                    return;
                }

                townyPlayer.removePermission("friend." + friend.getUniqueId());
                plugin.getPlayerManager().savePlayer(townyPlayer);

                MessageUtil.sendSuccess(player, "Removed " + friend.getName() + " from your friends!");
            }
            case "list" -> {
                TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
                List<String> friends = new ArrayList<>();

                for (String permission : townyPlayer.getPermissions()) {
                    if (permission.startsWith("friend.")) {
                        String friendUuid = permission.substring(7);
                        try {
                            OfflinePlayer friend = plugin.getServer().getOfflinePlayer(java.util.UUID.fromString(friendUuid));
                            friends.add(friend.getName());
                        } catch (IllegalArgumentException ignored) {}
                    }
                }

                if (friends.isEmpty()) {
                    MessageUtil.sendMessage(player, "§eYou have no friends added.");
                } else {
                    MessageUtil.sendMessage(player, "§6=== Your Friends (" + friends.size() + ") ===");
                    for (String friend : friends) {
                        Player onlineFriend = plugin.getServer().getPlayer(friend);
                        String status = onlineFriend != null ? "§aOnline" : "§7Offline";
                        MessageUtil.sendMessage(player, "§e• §f" + friend + " " + status);
                    }
                }
            }
            default -> MessageUtil.sendError(player, "Usage: /resident friend <add|remove|list> [player]");
        }
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendError(sender, "Only players can teleport!");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            MessageUtil.sendError(player, "You must be in a town to use town spawn!");
            return;
        }

        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (town == null || town.getSpawn() == null) {
            MessageUtil.sendError(player, "Your town doesn't have a spawn set!");
            return;
        }

        TeleportationUtil.teleportToTownSpawn(player, town.getSpawn(), plugin);
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendError(sender, "Only players can set resident properties!");
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendError(player, "Usage: /resident set <property> <value>");
            MessageUtil.sendMessage(player, "Properties: surname, title");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        String property = args[1].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        switch (property) {
            case "surname" -> {
                townyPlayer.getMetadata().put("surname", value);
                plugin.getPlayerManager().savePlayer(townyPlayer);
                MessageUtil.sendSuccess(player, "Surname set to: " + value);
            }
            case "title" -> {
                // Check if player has permission to set custom title
                if (!player.hasPermission("towny.resident.title")) {
                    MessageUtil.sendError(player, "You don't have permission to set a custom title!");
                    return;
                }

                townyPlayer.getMetadata().put("title", value);
                plugin.getPlayerManager().savePlayer(townyPlayer);
                MessageUtil.sendSuccess(player, "Title set to: " + value);
            }
            default -> MessageUtil.sendError(player, "Invalid property! Use: surname, title");
        }
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendError(sender, "Only players can toggle settings!");
            return;
        }

        if (args.length < 2) {
            MessageUtil.sendError(player, "Usage: /resident toggle <setting>");
            MessageUtil.sendMessage(player, "Settings: map, chatspy, plotborder");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        String setting = args[1].toLowerCase();

        switch (setting) {
            case "map" -> {
                boolean current = (Boolean) townyPlayer.getMetadata().getOrDefault("map", false);
                townyPlayer.getMetadata().put("map", !current);
                plugin.getPlayerManager().savePlayer(townyPlayer);
                MessageUtil.sendSuccess(player, "Map display " + (!current ? "enabled" : "disabled"));
            }
            case "chatspy" -> {
                if (!player.hasPermission("towny.admin.spy")) {
                    MessageUtil.sendError(player, "You don't have permission to use chat spy!");
                    return;
                }

                boolean current = (Boolean) townyPlayer.getMetadata().getOrDefault("chatspy", false);
                townyPlayer.getMetadata().put("chatspy", !current);
                plugin.getPlayerManager().savePlayer(townyPlayer);
                MessageUtil.sendSuccess(player, "Chat spy " + (!current ? "enabled" : "disabled"));
            }
            case "plotborder" -> {
                boolean current = (Boolean) townyPlayer.getMetadata().getOrDefault("plotborder", false);
                townyPlayer.getMetadata().put("plotborder", !current);
                plugin.getPlayerManager().savePlayer(townyPlayer);
                MessageUtil.sendSuccess(player, "Plot border display " + (!current ? "enabled" : "disabled"));
            }
            default -> MessageUtil.sendError(player, "Invalid setting! Use: map, chatspy, plotborder");
        }
    }

    private void handleTax(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendError(sender, "Only players can view tax information!");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null) {
            MessageUtil.sendError(player, "Player data not found!");
            return;
        }

        MessageUtil.sendMessage(player, "§6=== Tax Information ===");

        BigDecimal balance = plugin.getEconomyManager().getPlayerBalance(player.getUniqueId());
        MessageUtil.sendMessage(player, "§eYour Balance: §f" + plugin.getEconomyManager().format(balance));

        if (townyPlayer.hasTown()) {
            Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
            if (town != null) {
                BigDecimal townTaxRate = town.getTaxRate();
                BigDecimal townTax = balance.multiply(townTaxRate.divide(BigDecimal.valueOf(100)));

                MessageUtil.sendMessage(player, "§eTown: §f" + town.getName());
                MessageUtil.sendMessage(player, "§eTown Tax Rate: §f" + townTaxRate + "%");
                MessageUtil.sendMessage(player, "§eTown Tax Amount: §f" + plugin.getEconomyManager().format(townTax));

                if (town.hasNation()) {
                    Nation nation = plugin.getNationManager().getNation(town.getNationUuid());
                    if (nation != null) {
                        BigDecimal nationTaxRate = nation.getTaxRate();
                        BigDecimal nationTax = balance.multiply(nationTaxRate.divide(BigDecimal.valueOf(100)));

                        MessageUtil.sendMessage(player, "§eNation: §f" + nation.getName());
                        MessageUtil.sendMessage(player, "§eNation Tax Rate: §f" + nationTaxRate + "%");
                        MessageUtil.sendMessage(player, "§eNation Tax Amount: §f" + plugin.getEconomyManager().format(nationTax));

                        BigDecimal totalTax = townTax.add(nationTax);
                        MessageUtil.sendMessage(player, "§eTotal Tax: §f" + plugin.getEconomyManager().format(totalTax));
                    }
                }
            }
        } else {
            MessageUtil.sendMessage(player, "§eYou are not in a town - no taxes!");
        }
    }

    private void handlePlots(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendError(sender, "Only players can view plot information!");
            return;
        }

        var ownedPlots = plugin.getChunkManager().getChunksByOwner(player.getUniqueId());

        if (ownedPlots.isEmpty()) {
            MessageUtil.sendMessage(player, "§eYou don't own any plots.");
            return;
        }

        MessageUtil.sendMessage(player, "§6=== Your Plots (" + ownedPlots.size() + ") ===");

        for (int i = 0; i < ownedPlots.size() && i < 15; i++) {
            var chunk = ownedPlots.get(i);
            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            String townName = town != null ? town.getName() : "Unknown";
            String plotName = (String) chunk.getMetadata().getOrDefault("name", "Unnamed Plot");

            String forSale = chunk.isForSale() ? " §c(For Sale: " + plugin.getEconomyManager().format(chunk.getPlotPrice()) + ")" : "";

            MessageUtil.sendMessage(player, "§e" + (i + 1) + ". §f" + plotName + " §7in " + townName +
                    " §7[" + chunk.getPlotType() + "]" + forSale);
        }

        if (ownedPlots.size() > 15) {
            MessageUtil.sendMessage(player, "§7... and " + (ownedPlots.size() - 15) + " more plots");
        }
    }

    private void handleJail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendError(sender, "Only players can use jail commands!");
            return;
        }

        if (!player.hasPermission("towny.admin.jail")) {
            MessageUtil.sendError(player, "You don't have permission to jail players!");
            return;
        }

        if (args.length < 2) {
            MessageUtil.sendError(player, "Usage: /resident jail <player> [reason]");
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendError(player, "Player not found!");
            return;
        }

        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "No reason specified";

        TownyPlayer townyTarget = plugin.getPlayerManager().getPlayer(target.getUniqueId());
        townyTarget.getMetadata().put("jailed", true);
        townyTarget.getMetadata().put("jail_reason", reason);
        townyTarget.getMetadata().put("jailed_by", player.getName());
        townyTarget.getMetadata().put("jail_time", System.currentTimeMillis());
        plugin.getPlayerManager().savePlayer(townyTarget);

        MessageUtil.sendSuccess(player, "Jailed " + target.getName() + " for: " + reason);
        MessageUtil.sendMessage(target, "§cYou have been jailed by " + player.getName() + " for: " + reason);
    }

    private void handleUnjail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendError(sender, "Only players can use unjail commands!");
            return;
        }

        if (!player.hasPermission("towny.admin.jail")) {
            MessageUtil.sendError(player, "You don't have permission to unjail players!");
            return;
        }

        if (args.length < 2) {
            MessageUtil.sendError(player, "Usage: /resident unjail <player>");
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendError(player, "Player not found!");
            return;
        }

        TownyPlayer townyTarget = plugin.getPlayerManager().getPlayer(target.getUniqueId());
        if (!(Boolean) townyTarget.getMetadata().getOrDefault("jailed", false)) {
            MessageUtil.sendError(player, target.getName() + " is not jailed!");
            return;
        }

        townyTarget.getMetadata().remove("jailed");
        townyTarget.getMetadata().remove("jail_reason");
        townyTarget.getMetadata().remove("jailed_by");
        townyTarget.getMetadata().remove("jail_time");
        plugin.getPlayerManager().savePlayer(townyTarget);

        MessageUtil.sendSuccess(player, "Unjailed " + target.getName());
        MessageUtil.sendMessage(target, "§aYou have been released from jail by " + player.getName());
    }

    private void showResidentInfo(CommandSender sender, Player target) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(target.getUniqueId());
        if (townyPlayer == null) {
            MessageUtil.sendError(sender, "Player data not found!");
            return;
        }

        MessageUtil.sendMessage(sender, "§6=== Resident Info: " + target.getName() + " ===");
        MessageUtil.sendMessage(sender, "§eStatus: §aOnline");

        // Show balance if viewing own info or has permission
        if (sender.equals(target) || sender.hasPermission("towny.admin")) {
            BigDecimal balance = plugin.getEconomyManager().getPlayerBalance(target.getUniqueId());
            MessageUtil.sendMessage(sender, "§eBalance: §f" + plugin.getEconomyManager().format(balance));
        }

        // Show rank
        Rank rank = plugin.getRankManager().getPlayerRank(target.getUniqueId());
        MessageUtil.sendMessage(sender, "§eRank: §f" + (rank != null ? rank.getFormattedName() : "None"));

        // Show town info
        if (townyPlayer.hasTown()) {
            Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
            if (town != null) {
                MessageUtil.sendMessage(sender, "§eTown: §f" + town.getName());
                MessageUtil.sendMessage(sender, "§eJoined Town: §f" +
                        (townyPlayer.getJoinedTown() != null ? dateFormat.format(new Date(townyPlayer.getJoinedTown().getTime())) : "Unknown"));

                if (town.isMayor(target.getUniqueId())) {
                    MessageUtil.sendMessage(sender, "§eRole: §6Mayor");
                } else {
                    MessageUtil.sendMessage(sender, "§eRole: §fResident");
                }
            }
        } else {
            MessageUtil.sendMessage(sender, "§eTown: §7None");
        }

        // Show nation info
        if (townyPlayer.hasNation()) {
            Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
            if (nation != null) {
                MessageUtil.sendMessage(sender, "§eNation: §f" + nation.getName());

                if (nation.isKing(target.getUniqueId())) {
                    MessageUtil.sendMessage(sender, "§eNation Role: §6King");
                }
            }
        }

        // Show plots owned
        int plotCount = plugin.getChunkManager().getChunksByOwner(target.getUniqueId()).size();
        MessageUtil.sendMessage(sender, "§ePlots Owned: §f" + plotCount);

        // Show custom title/surname if set
        String title = (String) townyPlayer.getMetadata().get("title");
        String surname = (String) townyPlayer.getMetadata().get("surname");

        if (title != null) {
            MessageUtil.sendMessage(sender, "§eTitle: §f" + title);
        }
        if (surname != null) {
            MessageUtil.sendMessage(sender, "§eSurname: §f" + surname);
        }

        // Show jail status if jailed
        if ((Boolean) townyPlayer.getMetadata().getOrDefault("jailed", false)) {
            String jailReason = (String) townyPlayer.getMetadata().get("jail_reason");
            String jailedBy = (String) townyPlayer.getMetadata().get("jailed_by");
            MessageUtil.sendMessage(sender, "§cJailed by: §f" + jailedBy + " §7(" + jailReason + ")");
        }

        // Show last online
        MessageUtil.sendMessage(sender, "§eLast Online: §f" +
                (townyPlayer.getLastOnline() != null ? dateFormat.format(new Date(townyPlayer.getLastOnline().getTime())) : "Unknown"));
    }

    private void showOfflineResidentInfo(CommandSender sender, OfflinePlayer target) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(target.getUniqueId());
        if (townyPlayer == null) {
            MessageUtil.sendError(sender, "Player data not found!");
            return;
        }

        MessageUtil.sendMessage(sender, "§6=== Resident Info: " + target.getName() + " ===");
        MessageUtil.sendMessage(sender, "§eStatus: §7Offline");

        // Show basic info for offline players
        Rank rank = plugin.getRankManager().getPlayerRank(target.getUniqueId());
        MessageUtil.sendMessage(sender, "§eRank: §f" + (rank != null ? rank.getFormattedName() : "None"));

        if (townyPlayer.hasTown()) {
            Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
            if (town != null) {
                MessageUtil.sendMessage(sender, "§eTown: §f" + town.getName());
                if (town.isMayor(target.getUniqueId())) {
                    MessageUtil.sendMessage(sender, "§eRole: §6Mayor");
                }
            }
        }

        MessageUtil.sendMessage(sender, "§eLast Online: §f" +
                (townyPlayer.getLastOnline() != null ? dateFormat.format(new Date(townyPlayer.getLastOnline().getTime())) : "Unknown"));
    }

    private String getTownInfo(TownyPlayer resident) {
        if (!resident.hasTown()) {
            return "§7[No Town]";
        }

        Town town = plugin.getTownManager().getTown(resident.getTownUuid());
        if (town == null) {
            return "§7[Unknown Town]";
        }

        String role = town.isMayor(resident.getUuid()) ? "§6[Mayor]" : "§f[Resident]";
        return "§7[" + town.getName() + "] " + role;
    }

    private void showHelp(CommandSender sender) {
        MessageUtil.sendMessage(sender, "§6=== Resident Commands ===");
        MessageUtil.sendMessage(sender, "§e/resident §7- Show your info");
        MessageUtil.sendMessage(sender, "§e/resident <player> §7- Show player info");
        MessageUtil.sendMessage(sender, "§e/resident info <player> §7- Show detailed player info");
        MessageUtil.sendMessage(sender, "§e/resident list [filter] [page] §7- List residents");
        MessageUtil.sendMessage(sender, "§e/resident friend <add|remove|list> [player] §7- Manage friends");
        MessageUtil.sendMessage(sender, "§e/resident spawn §7- Teleport to town spawn");
        MessageUtil.sendMessage(sender, "§e/resident set <property> <value> §7- Set resident properties");
        MessageUtil.sendMessage(sender, "§e/resident toggle <setting> §7- Toggle resident settings");
        MessageUtil.sendMessage(sender, "§e/resident tax §7- View tax information");
        MessageUtil.sendMessage(sender, "§e/resident plots §7- List your owned plots");

        if (sender.hasPermission("towny.admin.jail")) {
            MessageUtil.sendMessage(sender, "§e/resident jail <player> [reason] §7- Jail a player");
            MessageUtil.sendMessage(sender, "§e/resident unjail <player> §7- Unjail a player");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "info", "list", "friend", "spawn", "set", "toggle", "tax", "plots"
            );

            if (sender.hasPermission("towny.admin.jail")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.addAll(Arrays.asList("jail", "unjail"));
            }

            // Add online player names
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                subCommands.add(player.getName());
            }

            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "info", "jail", "unjail" -> {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                }
                case "list" -> {
                    List<String> filters = Arrays.asList("all", "online", "mayors", "kings");
                    for (String filter : filters) {
                        if (filter.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(filter);
                        }
                    }
                }
                case "friend" -> {
                    List<String> friendActions = Arrays.asList("add", "remove", "list");
                    for (String action : friendActions) {
                        if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(action);
                        }
                    }
                }
                case "set" -> {
                    List<String> properties = Arrays.asList("surname", "title");
                    for (String prop : properties) {
                        if (prop.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(prop);
                        }
                    }
                }
                case "toggle" -> {
                    List<String> settings = Arrays.asList("map", "chatspy", "plotborder");
                    for (String setting : settings) {
                        if (setting.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(setting);
                        }
                    }
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String action = args[1].toLowerCase();

            if ("friend".equals(subCommand) && ("add".equals(action) || "remove".equals(action))) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if ("list".equals(subCommand)) {
                // Page numbers for list command
                for (int i = 1; i <= 10; i++) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        return completions;
    }
}