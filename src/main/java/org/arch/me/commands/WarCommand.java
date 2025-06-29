package org.arch.me.commands;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Nation;
import org.arch.me.models.TownyPlayer;
import org.arch.me.models.War;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WarCommand implements CommandExecutor, TabCompleter {

    private final EnhancedCoreH plugin;

    public WarCommand(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("general.player-only"));
            return true;
        }

        if (args.length == 0) {
            showWarInfo(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "declare" -> handleDeclare(player, args);
            case "join" -> handleJoin(player, args);
            case "surrender" -> handleSurrender(player, args);
            case "peace" -> handlePeace(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player, args);
            case "status" -> handleStatus(player, args);
            default -> showHelp(player);
        }

        return true;
    }

    private void showWarInfo(Player player) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage("§cYou must be in a nation to view war information!");
            return;
        }

        War war = plugin.getWarManager().getActiveWarForNation(townyPlayer.getNationUuid());
        if (war == null) {
            player.sendMessage("§aYour nation is not currently at war.");
            return;
        }

        displayWarInfo(player, war);
    }

    private void handleDeclare(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /war declare <nation> <war_name>");
            return;
        }

        String targetNationName = args[1];
        String warName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage("§cYou must be in a nation to declare war!");
            return;
        }

        Nation playerNation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (playerNation == null || !playerNation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to declare war!");
            return;
        }

        Nation targetNation = plugin.getNationManager().getNation(targetNationName);
        if (targetNation == null) {
            player.sendMessage("§cNation not found: " + targetNationName);
            return;
        }

        if (targetNation.getUuid().equals(playerNation.getUuid())) {
            player.sendMessage("§cYou cannot declare war on your own nation!");
            return;
        }

        // Check if already at war
        if (plugin.getWarManager().areNationsAtWar(playerNation.getUuid(), targetNation.getUuid())) {
            player.sendMessage("§cYour nations are already at war!");
            return;
        }

        // Check if nation is already in a war
        if (plugin.getWarManager().isNationAtWar(playerNation.getUuid())) {
            player.sendMessage("§cYour nation is already at war with another nation!");
            return;
        }

        if (plugin.getWarManager().isNationAtWar(targetNation.getUuid())) {
            player.sendMessage("§cThe target nation is already at war!");
            return;
        }

        plugin.getWarManager().declareWar(playerNation.getUuid(), targetNation.getUuid(), warName)
                .thenAccept(war -> {
                    if (war != null) {
                        player.sendMessage("§c⚔ War declared against " + targetNation.getName() + "!");
                        player.sendMessage("§eWar Name: §f" + warName);
                        player.sendMessage("§ePreparation time: §f" +
                                plugin.getConfig().getInt("war.preparation-time-minutes", 60) + " minutes");
                    } else {
                        player.sendMessage("§cFailed to declare war! Check your nation's funds.");
                    }
                });
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /war join <allied_nation>");
            return;
        }

        String alliedNationName = args[1];

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage("§cYou must be in a nation to join a war!");
            return;
        }

        Nation playerNation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (playerNation == null || !playerNation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to join a war!");
            return;
        }

        Nation alliedNation = plugin.getNationManager().getNation(alliedNationName);
        if (alliedNation == null) {
            player.sendMessage("§cNation not found: " + alliedNationName);
            return;
        }

        // Check if allied nation is at war
        War war = plugin.getWarManager().getActiveWarForNation(alliedNation.getUuid());
        if (war == null) {
            player.sendMessage("§c" + alliedNation.getName() + " is not currently at war!");
            return;
        }

        // Check if player's nation is already in a war
        if (plugin.getWarManager().isNationAtWar(playerNation.getUuid())) {
            player.sendMessage("§cYour nation is already involved in a war!");
            return;
        }

        plugin.getWarManager().joinWarAsAlly(playerNation.getUuid(), alliedNation.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a⚔ Your nation has joined the war as an ally of " + alliedNation.getName() + "!");
                    } else {
                        player.sendMessage("§cFailed to join the war!");
                    }
                });
    }

    private void handleSurrender(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage("§cYou must be in a nation to surrender!");
            return;
        }

        Nation playerNation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (playerNation == null || !playerNation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to surrender!");
            return;
        }

        War war = plugin.getWarManager().getActiveWarForNation(playerNation.getUuid());
        if (war == null) {
            player.sendMessage("§cYour nation is not at war!");
            return;
        }

        plugin.getWarManager().endWar(war.getId(), War.WarStatus.ENDED_SURRENDER, null)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§c⚔ Your nation has surrendered! The war is over.");
                    } else {
                        player.sendMessage("§cFailed to surrender!");
                    }
                });
    }

    private void handlePeace(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage("§cYou must be in a nation to negotiate peace!");
            return;
        }

        Nation playerNation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (playerNation == null || !playerNation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to negotiate peace!");
            return;
        }

        War war = plugin.getWarManager().getActiveWarForNation(playerNation.getUuid());
        if (war == null) {
            player.sendMessage("§cYour nation is not at war!");
            return;
        }

        // TODO: Implement peace negotiation system
        // For now, just end the war as peace
        plugin.getWarManager().endWar(war.getId(), War.WarStatus.ENDED_PEACE, null)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a⚔ Peace has been declared! The war is over.");
                    } else {
                        player.sendMessage("§cFailed to declare peace!");
                    }
                });
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length > 1) {
            // Show specific war info
            try {
                long warId = Long.parseLong(args[1]);
                War war = plugin.getWarManager().getWar(warId);
                if (war != null) {
                    displayWarInfo(player, war);
                } else {
                    player.sendMessage("§cWar not found with ID: " + warId);
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid war ID: " + args[1]);
            }
        } else {
            showWarInfo(player);
        }
    }

    private void handleList(Player player, String[] args) {
        List<War> activeWars = plugin.getWarManager().getAllActiveWars();

        if (activeWars.isEmpty()) {
            player.sendMessage("§aThere are currently no active wars.");
            return;
        }

        player.sendMessage("§6=== Active Wars (" + activeWars.size() + ") ===");

        for (War war : activeWars) {
            Nation declaring = plugin.getNationManager().getNation(war.getDeclaringNationUuid());
            Nation defending = plugin.getNationManager().getNation(war.getDefendingNationUuid());

            String declaringName = declaring != null ? declaring.getName() : "Unknown";
            String defendingName = defending != null ? defending.getName() : "Unknown";

            String statusColor = war.getStatus() == War.WarStatus.ACTIVE ? "§c" : "§e";

            player.sendMessage(String.format("§e%d. %s%s §7- §f%s §7vs §f%s §7(%s)",
                    war.getId(),
                    statusColor,
                    war.getWarName(),
                    declaringName,
                    defendingName,
                    war.getStatus().name()));
        }
    }

    private void handleStatus(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage("§cYou must be in a nation to check war status!");
            return;
        }

        War war = plugin.getWarManager().getActiveWarForNation(townyPlayer.getNationUuid());
        if (war == null) {
            player.sendMessage("§aYour nation is not currently at war.");
            return;
        }

        player.sendMessage("§6=== War Status ===");
        player.sendMessage("§eWar: §f" + war.getWarName());
        player.sendMessage("§eStatus: §f" + war.getStatus().name());

        if (war.isCapitulationInProgress()) {
            player.sendMessage("§c⚔ CAPITULATION IN PROGRESS!");
            player.sendMessage("§cTime Remaining: §f" + war.getFormattedTimeRemaining());
            player.sendMessage("§cEnemies in Capital: §f" + war.getPlayersInCapitalChunk().size());
        }

        // Show sides
        Nation declaring = plugin.getNationManager().getNation(war.getDeclaringNationUuid());
        Nation defending = plugin.getNationManager().getNation(war.getDefendingNationUuid());

        player.sendMessage("§eAttacking Side: §f" + (declaring != null ? declaring.getName() : "Unknown"));
        if (!war.getDeclaringAllies().isEmpty()) {
            player.sendMessage("§eAttacking Allies: §f" + getAllyNames(war.getDeclaringAllies()));
        }

        player.sendMessage("§eDefending Side: §f" + (defending != null ? defending.getName() : "Unknown"));
        if (!war.getDefendingAllies().isEmpty()) {
            player.sendMessage("§eDefending Allies: §f" + getAllyNames(war.getDefendingAllies()));
        }
    }

    private void displayWarInfo(Player player, War war) {
        Nation declaring = plugin.getNationManager().getNation(war.getDeclaringNationUuid());
        Nation defending = plugin.getNationManager().getNation(war.getDefendingNationUuid());

        player.sendMessage("§6=== War Info: " + war.getWarName() + " ===");
        player.sendMessage("§eWar ID: §f" + war.getId());
        player.sendMessage("§eStatus: §f" + war.getStatus().name());
        player.sendMessage("§eDeclared: §f" + war.getDeclaredDate().toString());

        if (war.getStartDate() != null) {
            player.sendMessage("§eStarted: §f" + war.getStartDate().toString());
        }

        player.sendMessage("§eAttacking: §f" + (declaring != null ? declaring.getName() : "Unknown"));
        if (!war.getDeclaringAllies().isEmpty()) {
            player.sendMessage("§eAttacking Allies: §f" + getAllyNames(war.getDeclaringAllies()));
        }

        player.sendMessage("§eDefending: §f" + (defending != null ? defending.getName() : "Unknown"));
        if (!war.getDefendingAllies().isEmpty()) {
            player.sendMessage("§eDefending Allies: §f" + getAllyNames(war.getDefendingAllies()));
        }

        if (war.isCapitulationInProgress()) {
            player.sendMessage("§c⚔ CAPITULATION IN PROGRESS!");
            player.sendMessage("§cTime Remaining: §f" + war.getFormattedTimeRemaining());
        }
    }

    private String getAllyNames(java.util.Set<java.util.UUID> allyUuids) {
        List<String> names = new ArrayList<>();
        for (java.util.UUID uuid : allyUuids) {
            Nation nation = plugin.getNationManager().getNation(uuid);
            names.add(nation != null ? nation.getName() : "Unknown");
        }
        return String.join(", ", names);
    }

    private void showHelp(Player player) {
        player.sendMessage("§6=== War Commands ===");
        player.sendMessage("§e/war §7- Show your nation's war status");
        player.sendMessage("§e/war declare <nation> <war_name> §7- Declare war on a nation");
        player.sendMessage("§e/war join <allied_nation> §7- Join a war as an ally");
        player.sendMessage("§e/war surrender §7- Surrender and end the war");
        player.sendMessage("§e/war peace §7- Declare peace and end the war");
        player.sendMessage("§e/war info [war_id] §7- Show war information");
        player.sendMessage("§e/war list §7- List all active wars");
        player.sendMessage("§e/war status §7- Show detailed war status");
        player.sendMessage("");
        player.sendMessage("§c⚔ Note: Only kings can declare war, join wars, surrender, or make peace!");
        player.sendMessage("§e⚔ Towns cannot join wars independently - only nations can participate!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "declare", "join", "surrender", "peace", "info", "list", "status"
            );

            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "declare", "join" -> {
                    for (Nation nation : plugin.getNationManager().getAllNations()) {
                        if (nation.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(nation.getName());
                        }
                    }
                }
                case "info" -> {
                    for (War war : plugin.getWarManager().getAllActiveWars()) {
                        String warId = String.valueOf(war.getId());
                        if (warId.startsWith(args[1])) {
                            completions.add(warId);
                        }
                    }
                }
            }
        }

        return completions;
    }
}
