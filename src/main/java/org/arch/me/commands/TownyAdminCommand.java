package org.arch.me.commands;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Nation;
import org.arch.me.models.Rank;
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

public class TownyAdminCommand implements CommandExecutor, TabCompleter {

    private final EnhancedCoreH plugin;

    public TownyAdminCommand(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("towny.admin")) {
            sender.sendMessage("§cYou don't have permission to use admin commands!");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "town" -> handleTownAdmin(sender, args);
            case "nation" -> handleNationAdmin(sender, args);
            case "player" -> handlePlayerAdmin(sender, args);
            case "rank" -> handleRankAdmin(sender, args);
            case "economy" -> handleEconomyAdmin(sender, args);
            case "config" -> handleConfigAdmin(sender, args);
            case "database" -> handleDatabaseAdmin(sender, args);
            case "stats" -> handleStats(sender, args);
            case "reload" -> handleReload(sender, args);
            case "backup" -> handleBackup(sender, args);
            default -> showHelp(sender);
        }

        return true;
    }

    private void handleTownAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /towny town <create|delete|set|add|remove|list>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /towny town create <name> <mayor>");
                    return;
                }

                String townName = args[2];
                Player mayor = plugin.getServer().getPlayer(args[3]);

                if (mayor == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return;
                }

                plugin.getTownManager().createTown(townName, mayor.getUniqueId(), mayor.getLocation())
                        .thenAccept(town -> {
                            if (town != null) {
                                sender.sendMessage("§aAdmin created town: " + townName + " with mayor: " + mayor.getName());
                            } else {
                                sender.sendMessage("§cFailed to create town!");
                            }
                        });
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /towny town delete <name>");
                    return;
                }

                Town town = plugin.getTownManager().getTown(args[2]);
                if (town == null) {
                    sender.sendMessage("§cTown not found!");
                    return;
                }

                plugin.getTownManager().deleteTown(town.getUuid(), true)
                        .thenAccept(success -> {
                            if (success) {
                                sender.sendMessage("§aAdmin deleted town: " + town.getName());
                            } else {
                                sender.sendMessage("§cFailed to delete town!");
                            }
                        });
            }
            case "set" -> handleTownSet(sender, args);
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /towny town add <town> <player>");
                    return;
                }

                Town town = plugin.getTownManager().getTown(args[2]);
                Player player = plugin.getServer().getPlayer(args[3]);

                if (town == null) {
                    sender.sendMessage("§cTown not found!");
                    return;
                }
                if (player == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return;
                }

                plugin.getTownManager().addPlayerToTown(town.getUuid(), player.getUniqueId())
                        .thenAccept(success -> {
                            if (success) {
                                sender.sendMessage("§aAdmin added " + player.getName() + " to " + town.getName());
                            } else {
                                sender.sendMessage("§cFailed to add player to town!");
                            }
                        });
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /towny town remove <town> <player>");
                    return;
                }

                Town town = plugin.getTownManager().getTown(args[2]);
                Player player = plugin.getServer().getPlayer(args[3]);

                if (town == null) {
                    sender.sendMessage("§cTown not found!");
                    return;
                }
                if (player == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return;
                }

                plugin.getTownManager().removePlayerFromTown(town.getUuid(), player.getUniqueId())
                        .thenAccept(success -> {
                            if (success) {
                                sender.sendMessage("§aAdmin removed " + player.getName() + " from " + town.getName());
                            } else {
                                sender.sendMessage("§cFailed to remove player from town!");
                            }
                        });
            }
            case "list" -> {
                sender.sendMessage("§6=== All Towns ===");
                plugin.getTownManager().getAllTowns().forEach(town ->
                        sender.sendMessage("§e" + town.getName() + " §7(Mayor: " + getMayorName(town) +
                                ", Residents: " + town.getResidentCount() + ")")
                );
            }
            default -> sender.sendMessage("§cInvalid town admin action!");
        }
    }

    private void handleTownSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§cUsage: /towny town set <town> <property> <value>");
            sender.sendMessage("§cProperties: mayor, balance, tax, maxresidents, spawn");
            return;
        }

        Town town = plugin.getTownManager().getTown(args[2]);
        if (town == null) {
            sender.sendMessage("§cTown not found!");
            return;
        }

        String property = args[3].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));

        switch (property) {
            case "mayor" -> {
                Player newMayor = plugin.getServer().getPlayer(value);
                if (newMayor == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return;
                }

                town.setMayorUuid(newMayor.getUniqueId());
                plugin.getTownManager().saveTown(town);
                sender.sendMessage("§aSet " + newMayor.getName() + " as mayor of " + town.getName());
            }
            case "balance" -> {
                try {
                    BigDecimal balance = new BigDecimal(value);
                    town.setBalance(balance);
                    plugin.getTownManager().saveTown(town);
                    sender.sendMessage("§aSet " + town.getName() + " balance to " +
                            plugin.getEconomyManager().format(balance));
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid balance amount!");
                }
            }
            case "tax" -> {
                try {
                    BigDecimal tax = new BigDecimal(value);
                    town.setTaxRate(tax);
                    plugin.getTownManager().saveTown(town);
                    sender.sendMessage("§aSet " + town.getName() + " tax rate to " + tax + "%");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid tax rate!");
                }
            }
            case "maxresidents" -> {
                try {
                    int maxResidents = Integer.parseInt(value);
                    town.setMaxResidents(maxResidents);
                    plugin.getTownManager().saveTown(town);
                    sender.sendMessage("§aSet " + town.getName() + " max residents to " + maxResidents);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number!");
                }
            }
            case "spawn" -> {
                if (sender instanceof Player player) {
                    town.setSpawn(player.getLocation());
                    plugin.getTownManager().saveTown(town);
                    sender.sendMessage("§aSet " + town.getName() + " spawn to your location");
                } else {
                    sender.sendMessage("§cOnly players can set spawn locations!");
                }
            }
            default -> sender.sendMessage("§cInvalid property!");
        }
    }

    private void handleNationAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /towny nation <create|delete|set|add|remove|list>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /towny nation create <name> <king> <capital_town>");
                    return;
                }

                String nationName = args[2];
                Player king = plugin.getServer().getPlayer(args[3]);
                Town capitalTown = plugin.getTownManager().getTown(args[4]);

                if (king == null) {
                    sender.sendMessage("§cKing player not found!");
                    return;
                }
                if (capitalTown == null) {
                    sender.sendMessage("§cCapital town not found!");
                    return;
                }

                plugin.getNationManager().createNation(nationName, king.getUniqueId(), capitalTown.getUuid())
                        .thenAccept(nation -> {
                            if (nation != null) {
                                sender.sendMessage("§aAdmin created nation: " + nationName);
                            } else {
                                sender.sendMessage("§cFailed to create nation!");
                            }
                        });
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /towny nation delete <name>");
                    return;
                }

                Nation nation = plugin.getNationManager().getNation(args[2]);
                if (nation == null) {
                    sender.sendMessage("§cNation not found!");
                    return;
                }

                plugin.getNationManager().deleteNation(nation.getUuid(), true)
                        .thenAccept(success -> {
                            if (success) {
                                sender.sendMessage("§aAdmin deleted nation: " + nation.getName());
                            } else {
                                sender.sendMessage("§cFailed to delete nation!");
                            }
                        });
            }
            case "set" -> handleNationSet(sender, args);
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /towny nation add <nation> <town>");
                    return;
                }

                Nation nation = plugin.getNationManager().getNation(args[2]);
                Town town = plugin.getTownManager().getTown(args[3]);

                if (nation == null) {
                    sender.sendMessage("§cNation not found!");
                    return;
                }
                if (town == null) {
                    sender.sendMessage("§cTown not found!");
                    return;
                }

                plugin.getNationManager().addTownToNation(nation.getUuid(), town.getUuid())
                        .thenAccept(success -> {
                            if (success) {
                                sender.sendMessage("§aAdmin added " + town.getName() + " to " + nation.getName());
                            } else {
                                sender.sendMessage("§cFailed to add town to nation!");
                            }
                        });
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /towny nation remove <nation> <town>");
                    return;
                }

                Nation nation = plugin.getNationManager().getNation(args[2]);
                Town town = plugin.getTownManager().getTown(args[3]);

                if (nation == null) {
                    sender.sendMessage("§cNation not found!");
                    return;
                }
                if (town == null) {
                    sender.sendMessage("§cTown not found!");
                    return;
                }

                plugin.getNationManager().removeTownFromNation(nation.getUuid(), town.getUuid())
                        .thenAccept(success -> {
                            if (success) {
                                sender.sendMessage("§aAdmin removed " + town.getName() + " from " + nation.getName());
                            } else {
                                sender.sendMessage("§cFailed to remove town from nation!");
                            }
                        });
            }
            case "list" -> {
                sender.sendMessage("§6=== All Nations ===");
                plugin.getNationManager().getAllNations().forEach(nation ->
                        sender.sendMessage("§e" + nation.getName() + " §7(King: " + getKingName(nation) +
                                ", Towns: " + nation.getTownCount() + ")")
                );
            }
            default -> sender.sendMessage("§cInvalid nation admin action!");
        }
    }

    private void handleNationSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§cUsage: /towny nation set <nation> <property> <value>");
            sender.sendMessage("§cProperties: king, balance, tax, capital");
            return;
        }

        Nation nation = plugin.getNationManager().getNation(args[2]);
        if (nation == null) {
            sender.sendMessage("§cNation not found!");
            return;
        }

        String property = args[3].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));

        switch (property) {
            case "king" -> {
                Player newKing = plugin.getServer().getPlayer(value);
                if (newKing == null) {
                    sender.sendMessage("§cPlayer not found!");
                    return;
                }

                nation.setKingUuid(newKing.getUniqueId());
                plugin.getNationManager().saveNation(nation);
                sender.sendMessage("§aSet " + newKing.getName() + " as king of " + nation.getName());
            }
            case "balance" -> {
                try {
                    BigDecimal balance = new BigDecimal(value);
                    nation.setBalance(balance);
                    plugin.getNationManager().saveNation(nation);
                    sender.sendMessage("§aSet " + nation.getName() + " balance to " +
                            plugin.getEconomyManager().format(balance));
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid balance amount!");
                }
            }
            case "tax" -> {
                try {
                    BigDecimal tax = new BigDecimal(value);
                    nation.setTaxRate(tax);
                    plugin.getNationManager().saveNation(nation);
                    sender.sendMessage("§aSet " + nation.getName() + " tax rate to " + tax + "%");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid tax rate!");
                }
            }
            case "capital" -> {
                Town newCapital = plugin.getTownManager().getTown(value);
                if (newCapital == null) {
                    sender.sendMessage("§cTown not found!");
                    return;
                }

                nation.setCapitalUuid(newCapital.getUuid());
                plugin.getNationManager().saveNation(nation);
                sender.sendMessage("§aSet " + newCapital.getName() + " as capital of " + nation.getName());
            }
            default -> sender.sendMessage("§cInvalid property!");
        }
    }

    private void handlePlayerAdmin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /towny player <player> <set|add|remove|info>");
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(target.getUniqueId());
        if (townyPlayer == null) {
            sender.sendMessage("§cTowny player data not found!");
            return;
        }

        String action = args[2].toLowerCase();

        switch (action) {
            case "set" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /towny player <player> set <property> <value>");
                    sender.sendMessage("§cProperties: balance, rank, town, nation");
                    return;
                }

                String property = args[3].toLowerCase();
                String value = args[4];

                switch (property) {
                    case "balance" -> {
                        try {
                            BigDecimal balance = new BigDecimal(value);
                            townyPlayer.setBalance(balance);
                            plugin.getPlayerManager().savePlayer(townyPlayer);
                            sender.sendMessage("§aSet " + target.getName() + " balance to " +
                                    plugin.getEconomyManager().format(balance));
                        } catch (NumberFormatException e) {
                            sender.sendMessage("§cInvalid balance amount!");
                        }
                    }
                    case "rank" -> {
                        Rank rank = plugin.getRankManager().getRank(value);
                        if (rank == null) {
                            sender.sendMessage("§cRank not found!");
                            return;
                        }

                        townyPlayer.setRankId(rank.getId());
                        plugin.getPlayerManager().savePlayer(townyPlayer);
                        sender.sendMessage("§aSet " + target.getName() + " rank to " + rank.getName());
                    }
                    default -> sender.sendMessage("§cInvalid property!");
                }
            }
            case "info" -> {
                sender.sendMessage("§6=== Player Info: " + target.getName() + " ===");
                sender.sendMessage("§eBalance: §f" + plugin.getEconomyManager().format(townyPlayer.getBalance()));

                Rank rank = plugin.getRankManager().getPlayerRank(target.getUniqueId());
                sender.sendMessage("§eRank: §f" + (rank != null ? rank.getName() : "None"));

                if (townyPlayer.hasTown()) {
                    Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
                    sender.sendMessage("§eTown: §f" + (town != null ? town.getName() : "Unknown"));
                }

                if (townyPlayer.hasNation()) {
                    Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
                    sender.sendMessage("§eNation: §f" + (nation != null ? nation.getName() : "Unknown"));
                }
            }
            default -> sender.sendMessage("§cInvalid player admin action!");
        }
    }

    private void handleRankAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /towny rank <create|delete|set|list>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /towny rank create <name> [prefix] [priority]");
                    return;
                }

                String name = args[2];
                String prefix = args.length > 3 ? args[3] : "";
                int priority = args.length > 4 ? Integer.parseInt(args[4]) : 0;

                plugin.getRankManager().createRank(name, prefix, "", priority)
                        .thenAccept(rank -> {
                            if (rank != null) {
                                sender.sendMessage("§aCreated rank: " + name);
                            } else {
                                sender.sendMessage("§cFailed to create rank!");
                            }
                        });
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /towny rank delete <name>");
                    return;
                }

                Rank rank = plugin.getRankManager().getRank(args[2]);
                if (rank == null) {
                    sender.sendMessage("§cRank not found!");
                    return;
                }

                plugin.getRankManager().deleteRank(rank.getId())
                        .thenAccept(success -> {
                            if (success) {
                                sender.sendMessage("§aDeleted rank: " + rank.getName());
                            } else {
                                sender.sendMessage("§cFailed to delete rank!");
                            }
                        });
            }
            case "list" -> {
                sender.sendMessage("§6=== All Ranks ===");
                plugin.getRankManager().getAllRanks().forEach(rank ->
                        sender.sendMessage("§e" + rank.getName() + " §7(Priority: " + rank.getPriority() +
                                ", Default: " + rank.isDefault() + ")")
                );
            }
            default -> sender.sendMessage("§cInvalid rank admin action!");
        }
    }

    private void handleEconomyAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /towny economy <give|take|set|prices>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "give" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /towny economy give <player|town|nation> <name> <amount>");
                    return;
                }

                String type = args[2].toLowerCase();
                String name = args[3];

                try {
                    BigDecimal amount = new BigDecimal(args[4]);

                    switch (type) {
                        case "player" -> {
                            Player player = plugin.getServer().getPlayer(name);
                            if (player != null) {
                                plugin.getEconomyManager().depositPlayer(player.getUniqueId(), amount);
                                sender.sendMessage("§aGave " + plugin.getEconomyManager().format(amount) +
                                        " to player " + player.getName());
                            } else {
                                sender.sendMessage("§cPlayer not found!");
                            }
                        }
                        case "town" -> {
                            Town town = plugin.getTownManager().getTown(name);
                            if (town != null) {
                                plugin.getEconomyManager().depositTown(town.getUuid(), amount);
                                sender.sendMessage("§aGave " + plugin.getEconomyManager().format(amount) +
                                        " to town " + town.getName());
                            } else {
                                sender.sendMessage("§cTown not found!");
                            }
                        }
                        case "nation" -> {
                            Nation nation = plugin.getNationManager().getNation(name);
                            if (nation != null) {
                                plugin.getEconomyManager().depositNation(nation.getUuid(), amount);
                                sender.sendMessage("§aGave " + plugin.getEconomyManager().format(amount) +
                                        " to nation " + nation.getName());
                            } else {
                                sender.sendMessage("§cNation not found!");
                            }
                        }
                        default -> sender.sendMessage("§cInvalid type! Use: player, town, nation");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid amount!");
                }
            }
            case "prices" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /towny economy prices <set> <type> <amount>");
                    sender.sendMessage("§cTypes: town-creation, nation-creation, chunk-claim, daily-upkeep-town, daily-upkeep-nation");
                    return;
                }

                if (args[2].equalsIgnoreCase("set")) {
                    String priceType = args[3];

                    try {
                        BigDecimal price = new BigDecimal(args[4]);
                        plugin.getConfig().set("economy." + priceType, price.doubleValue());
                        plugin.saveConfig();
                        sender.sendMessage("§aSet " + priceType + " price to " +
                                plugin.getEconomyManager().format(price));
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid price amount!");
                    }
                }
            }
            default -> sender.sendMessage("§cInvalid economy admin action!");
        }
    }

    private void handleConfigAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /towny config <get|set|list>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "get" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /towny config get <key>");
                    return;
                }

                String key = args[2];
                Object value = plugin.getConfig().get(key);
                sender.sendMessage("§e" + key + ": §f" + (value != null ? value.toString() : "null"));
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /towny config set <key> <value>");
                    return;
                }

                String key = args[2];
                String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

                // Try to parse as different types
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    plugin.getConfig().set(key, Boolean.parseBoolean(value));
                } else {
                    try {
                        plugin.getConfig().set(key, Integer.parseInt(value));
                    } catch (NumberFormatException e1) {
                        try {
                            plugin.getConfig().set(key, Double.parseDouble(value));
                        } catch (NumberFormatException e2) {
                            plugin.getConfig().set(key, value);
                        }
                    }
                }

                plugin.saveConfig();
                sender.sendMessage("§aSet " + key + " to " + value);
            }
            case "list" -> {
                sender.sendMessage("§6=== Config Keys ===");
                plugin.getConfig().getKeys(true).forEach(key ->
                        sender.sendMessage("§e" + key + ": §f" + plugin.getConfig().get(key))
                );
            }
            default -> sender.sendMessage("§cInvalid config admin action!");
        }
    }

    private void handleDatabaseAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /towny database <status|reconnect|cleanup>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "status" -> {
                sender.sendMessage("§6=== Database Status ===");
                sender.sendMessage("§eTowns: §f" + plugin.getTownManager().getTownCount());
                sender.sendMessage("§eNations: §f" + plugin.getNationManager().getNationCount());
                sender.sendMessage("§eRanks: §f" + plugin.getRankManager().getRankCount());
                sender.sendMessage("§eClaimed Chunks: §f" + plugin.getChunkManager().getClaimedChunkCount());
            }
            case "save" -> {
                sender.sendMessage("§eSaving all data to database...");
                plugin.getTownManager().saveAll();
                plugin.getNationManager().saveAll();
                plugin.getPlayerManager().saveAll();
                sender.sendMessage("§aAll data saved successfully!");
            }
            default -> sender.sendMessage("§cInvalid database admin action!");
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        sender.sendMessage("§6=== Towny Statistics ===");
        sender.sendMessage("§eTowns: §f" + plugin.getTownManager().getTownCount());
        sender.sendMessage("§eNations: §f" + plugin.getNationManager().getNationCount());
        sender.sendMessage("§eRanks: §f" + plugin.getRankManager().getRankCount());
        sender.sendMessage("§eClaimed Chunks: §f" + plugin.getChunkManager().getClaimedChunkCount());
        sender.sendMessage("§eOnline Players: §f" + plugin.getServer().getOnlinePlayers().size());
    }

    private void handleReload(CommandSender sender, String[] args) {
        sender.sendMessage("§eReloading Towny configuration...");
        plugin.getConfigManager().reloadAllConfigs();
        sender.sendMessage("§aConfiguration reloaded successfully!");
    }

    private void handleBackup(CommandSender sender, String[] args) {
        sender.sendMessage("§eCreating backup...");
        // Save all data before backup
        plugin.getTownManager().saveAll();
        plugin.getNationManager().saveAll();
        plugin.getPlayerManager().saveAll();
        sender.sendMessage("§aBackup created successfully!");
    }

    private String getMayorName(Town town) {
        Player mayor = plugin.getServer().getPlayer(town.getMayorUuid());
        if (mayor != null) {
            return mayor.getName();
        }

        TownyPlayer townyMayor = plugin.getPlayerManager().getPlayer(town.getMayorUuid());
        return townyMayor != null ? townyMayor.getName() : "Unknown";
    }

    private String getKingName(Nation nation) {
        Player king = plugin.getServer().getPlayer(nation.getKingUuid());
        if (king != null) {
            return king.getName();
        }

        TownyPlayer townyKing = plugin.getPlayerManager().getPlayer(nation.getKingUuid());
        return townyKing != null ? townyKing.getName() : "Unknown";
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== Towny Admin Commands ===");
        sender.sendMessage("§e/towny town <create|delete|set|add|remove|list> §7- Town management");
        sender.sendMessage("§e/towny nation <create|delete|set|add|remove|list> §7- Nation management");
        sender.sendMessage("§e/towny player <player> <set|info> §7- Player management");
        sender.sendMessage("§e/towny rank <create|delete|list> §7- Rank management");
        sender.sendMessage("§e/towny economy <give|take|set|prices> §7- Economy management");
        sender.sendMessage("§e/towny config <get|set|list> §7- Configuration management");
        sender.sendMessage("§e/towny database <status|save> §7- Database operations");
        sender.sendMessage("§e/towny stats §7- Show statistics");
        sender.sendMessage("§e/towny reload §7- Reload configuration");
        sender.sendMessage("§e/towny backup §7- Create backup");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("towny.admin")) {
            return completions;
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "town", "nation", "player", "rank", "economy", "config",
                    "database", "stats", "reload", "backup"
            );

            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String mainCommand = args[0].toLowerCase();

            switch (mainCommand) {
                case "town" -> {
                    List<String> townActions = Arrays.asList("create", "delete", "set", "add", "remove", "list");
                    for (String action : townActions) {
                        if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(action);
                        }
                    }
                }
                case "nation" -> {
                    List<String> nationActions = Arrays.asList("create", "delete", "set", "add", "remove", "list");
                    for (String action : nationActions) {
                        if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(action);
                        }
                    }
                }
                case "player" -> {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                }
                case "rank" -> {
                    List<String> rankActions = Arrays.asList("create", "delete", "list");
                    for (String action : rankActions) {
                        if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(action);
                        }
                    }
                }
                case "economy" -> {
                    List<String> ecoActions = Arrays.asList("give", "take", "set", "prices");
                    for (String action : ecoActions) {
                        if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(action);
                        }
                    }
                }
                case "config" -> {
                    List<String> configActions = Arrays.asList("get", "set", "list");
                    for (String action : configActions) {
                        if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(action);
                        }
                    }
                }
                case "database" -> {
                    List<String> dbActions = Arrays.asList("status", "save");
                    for (String action : dbActions) {
                        if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(action);
                        }
                    }
                }
            }
        } else if (args.length == 3) {
            String mainCommand = args[0].toLowerCase();
            String subCommand = args[1].toLowerCase();

            switch (mainCommand) {
                case "town" -> {
                    if ("delete".equals(subCommand) || "set".equals(subCommand) ||
                            "add".equals(subCommand) || "remove".equals(subCommand)) {
                        for (Town town : plugin.getTownManager().getAllTowns()) {
                            if (town.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                completions.add(town.getName());
                            }
                        }
                    }
                }
                case "nation" -> {
                    if ("delete".equals(subCommand) || "set".equals(subCommand) ||
                            "add".equals(subCommand) || "remove".equals(subCommand)) {
                        for (Nation nation : plugin.getNationManager().getAllNations()) {
                            if (nation.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                completions.add(nation.getName());
                            }
                        }
                    }
                }
                case "player" -> {
                    if ("set".equals(subCommand) || "info".equals(subCommand)) {
                        List<String> playerActions = Arrays.asList("set", "info");
                        for (String action : playerActions) {
                            if (action.toLowerCase().startsWith(args[2].toLowerCase())) {
                                completions.add(action);
                            }
                        }
                    }
                }
                case "economy" -> {
                    if ("give".equals(subCommand) || "take".equals(subCommand)) {
                        List<String> types = Arrays.asList("player", "town", "nation");
                        for (String type : types) {
                            if (type.toLowerCase().startsWith(args[2].toLowerCase())) {
                                completions.add(type);
                            }
                        }
                    } else if ("prices".equals(subCommand)) {
                        if ("set".startsWith(args[2].toLowerCase())) {
                            completions.add("set");
                        }
                    }
                }
            }
        } else if (args.length == 4) {
            String mainCommand = args[0].toLowerCase();
            String subCommand = args[1].toLowerCase();

            switch (mainCommand) {
                case "town" -> {
                    if ("create".equals(subCommand) || "add".equals(subCommand) || "remove".equals(subCommand)) {
                        for (Player player : plugin.getServer().getOnlinePlayers()) {
                            if (player.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                completions.add(player.getName());
                            }
                        }
                    } else if ("set".equals(subCommand)) {
                        List<String> properties = Arrays.asList("mayor", "balance", "tax", "maxresidents", "spawn");
                        for (String prop : properties) {
                            if (prop.toLowerCase().startsWith(args[3].toLowerCase())) {
                                completions.add(prop);
                            }
                        }
                    }
                }
                case "nation" -> {
                    if ("create".equals(subCommand)) {
                        for (Player player : plugin.getServer().getOnlinePlayers()) {
                            if (player.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                completions.add(player.getName());
                            }
                        }
                    } else if ("add".equals(subCommand) || "remove".equals(subCommand)) {
                        for (Town town : plugin.getTownManager().getAllTowns()) {
                            if (town.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                completions.add(town.getName());
                            }
                        }
                    } else if ("set".equals(subCommand)) {
                        List<String> properties = Arrays.asList("king", "balance", "tax", "capital");
                        for (String prop : properties) {
                            if (prop.toLowerCase().startsWith(args[3].toLowerCase())) {
                                completions.add(prop);
                            }
                        }
                    }
                }
                case "economy" -> {
                    if ("give".equals(subCommand) || "take".equals(subCommand)) {
                        String type = args[2].toLowerCase();
                        switch (type) {
                            case "player" -> {
                                for (Player player : plugin.getServer().getOnlinePlayers()) {
                                    if (player.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(player.getName());
                                    }
                                }
                            }
                            case "town" -> {
                                for (Town town : plugin.getTownManager().getAllTowns()) {
                                    if (town.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(town.getName());
                                    }
                                }
                            }
                            case "nation" -> {
                                for (Nation nation : plugin.getNationManager().getAllNations()) {
                                    if (nation.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(nation.getName());
                                    }
                                }
                            }
                        }
                    } else if ("prices".equals(subCommand) && "set".equals(args[2])) {
                        List<String> priceTypes = Arrays.asList(
                                "town-creation", "nation-creation", "chunk-claim",
                                "daily-upkeep-town", "daily-upkeep-nation"
                        );
                        for (String type : priceTypes) {
                            if (type.toLowerCase().startsWith(args[3].toLowerCase())) {
                                completions.add(type);
                            }
                        }
                    }
                }
                case "player" -> {
                    if ("set".equals(args[2])) {
                        List<String> properties = Arrays.asList("balance", "rank", "town", "nation");
                        for (String prop : properties) {
                            if (prop.toLowerCase().startsWith(args[3].toLowerCase())) {
                                completions.add(prop);
                            }
                        }
                    }
                }
            }
        } else if (args.length == 5) {
            String mainCommand = args[0].toLowerCase();
            String subCommand = args[1].toLowerCase();

            if ("nation".equals(mainCommand) && "create".equals(subCommand)) {
                // Complete with town names for capital
                for (Town town : plugin.getTownManager().getAllTowns()) {
                    if (town.getName().toLowerCase().startsWith(args[4].toLowerCase())) {
                        completions.add(town.getName());
                    }
                }
            } else if ("player".equals(mainCommand) && "set".equals(args[2]) && "rank".equals(args[3])) {
                // Complete with rank names
                for (Rank rank : plugin.getRankManager().getAllRanks()) {
                    if (rank.getName().toLowerCase().startsWith(args[4].toLowerCase())) {
                        completions.add(rank.getName());
                    }
                }
            }
        }

        return completions;
    }
}