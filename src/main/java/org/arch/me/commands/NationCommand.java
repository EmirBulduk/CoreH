package org.arch.me.commands;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.ClaimedChunk;
import org.arch.me.models.Nation;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.arch.me.util.NameValidator;
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

public class NationCommand implements CommandExecutor, TabCompleter {

    private final EnhancedCoreH plugin;

    public NationCommand(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("general.player-only"));
            return true;
        }

        if (args.length == 0) {
            showNationInfo(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create", "new" -> handleCreate(player, args);
            case "delete", "disband" -> handleDelete(player, args);
            case "invite" -> {
                if (args.length >= 2 && (args[1].equalsIgnoreCase("accept") || args[1].equalsIgnoreCase("deny"))) {
                    handleInviteResponse(player, args);
                } else {
                    handleInvite(player, args);
                }
            }
            case "kick" -> handleKick(player, args);
            case "set" -> handleSet(player, args);
            case "toggle" -> handleToggle(player, args);
            case "deposit" -> handleDeposit(player, args);
            case "withdraw" -> handleWithdraw(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player, args);
            case "king" -> handleSetKing(player, args);
            case "capital" -> handleSetCapital(player, args);
            case "capitalchunk" -> handleSetCapitalChunk(player, args);
            case "rank" -> handleRank(player, args);
            case "deputy" -> handleDeputy(player, args);
            default -> showHelp(player);
        }

        return true;
    }

    private void showNationInfo(Player player) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        displayNationInfo(player, nation);
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation create <name>");
            return;
        }

        String nationName = args[1];

        // Validate nation name
        if (!NameValidator.isValidName(nationName)) {
            player.sendMessage("§cInvalid nation name: " + NameValidator.getValidationError(nationName));
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage("§cYou must be in a town to create a nation!");
            return;
        }

        Town playerTown = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (playerTown == null || !playerTown.isMayor(player.getUniqueId())) {
            player.sendMessage("§cYou must be the mayor of a town to create a nation!");
            return;
        }

        if (playerTown.hasNation()) {
            player.sendMessage("§cYour town is already part of a nation!");
            return;
        }

        // Check economy requirements
        BigDecimal cost = BigDecimal.valueOf(plugin.getConfigManager().getEconomyValue("nation-creation-cost"));

        // Use EconomyManager methods consistently
        if (!plugin.getEconomyManager().hasPlayerBalance(player.getUniqueId(), cost)) {
            player.sendMessage("§cYou don't have enough money! Required: " +
                    plugin.getEconomyManager().format(cost) +
                    " | You have: " + plugin.getEconomyManager().format(
                    plugin.getEconomyManager().getPlayerBalance(player.getUniqueId())));
            return;
        }

        // Withdraw money first using consistent methods
        var withdrawResponse = plugin.getEconomyManager().withdrawPlayer(player.getUniqueId(), cost);
        if (!withdrawResponse.transactionSuccess()) {
            player.sendMessage("§cFailed to withdraw money: " + withdrawResponse.errorMessage);
            return;
        }

        // Create nation
        plugin.getNationManager().createNation(nationName, player.getUniqueId(), playerTown.getUuid())
                .thenAccept(nation -> {
                    if (nation != null) {
                        // Set default rank for the king
                        plugin.getRankManager().setPlayerRank(player.getUniqueId(),
                            plugin.getRankManager().getRank("minister").getUuid());

                        player.sendMessage(plugin.getConfigManager().getMessage("nation.created", nationName));
                        player.sendMessage("§aDeducted " + plugin.getEconomyManager().format(cost) + " for nation creation.");
                    } else {
                        // Refund money if nation creation failed
                        plugin.getEconomyManager().depositPlayer(player.getUniqueId(), cost);
                        player.sendMessage("§cFailed to create nation. Name might already exist. Money has been refunded.");
                    }
                });
    }

    private void handleDelete(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null || !nation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to delete the nation!");
            return;
        }

        plugin.getNationManager().deleteNation(nation.getUuid(), false)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getConfigManager().getMessage("nation.deleted", nation.getName()));
                    } else {
                        player.sendMessage("§cFailed to delete nation.");
                    }
                });
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation invite <town>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        // Check permissions - king, deputy, or players with nation.invite permission
        if (!hasNationManagementPermission(nation, player.getUniqueId()) &&
            !plugin.getRankManager().playerHasPermission(player.getUniqueId(), "towny.nation.invite")) {
            player.sendMessage("§cYou don't have permission to invite towns to the nation!");
            return;
        }

        String townName = args[1];
        Town town = plugin.getTownManager().getTown(townName);

        if (town == null) {
            player.sendMessage("§cTown not found: " + townName);
            return;
        }

        if (town.hasNation()) {
            player.sendMessage("§cThat town is already part of a nation!");
            return;
        }

        if (!nation.canAddTown()) {
            player.sendMessage("§cNation has reached maximum town limit!");
            return;
        }

        // Check if town already has a pending invitation
        if (plugin.getNationInvitationManager().hasActiveInvitation(town.getUuid())) {
            player.sendMessage("§c" + town.getName() + " already has a pending nation invitation!");
            return;
        }

        plugin.getNationInvitationManager().sendInvitation(nation.getUuid(), player.getUniqueId(), town.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aInvitation sent to " + town.getName() + "!");
                        player.sendMessage("§7The town mayor will receive the invitation and can accept or deny it.");
                    } else {
                        player.sendMessage("§cFailed to send invitation. Town may already have a pending invitation.");
                    }
                });
    }

    private void handleInviteResponse(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation invite <accept|deny>");
            return;
        }

        // First, find any town where this player is the mayor
        Town playerTown = null;
        for (Town town : plugin.getTownManager().getAllTowns()) {
            if (town.isMayor(player.getUniqueId())) {
                playerTown = town;
                break;
            }
        }

        if (playerTown == null) {
            player.sendMessage("§cYou must be the mayor of a town to respond to nation invitations!");
            return;
        }

        if (playerTown.hasNation()) {
            player.sendMessage("§cYour town is already part of a nation!");
            return;
        }

        // Check if town has a pending invitation
        if (!plugin.getNationInvitationManager().hasActiveInvitation(playerTown.getUuid())) {
            player.sendMessage("§cYour town doesn't have any pending nation invitations!");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "accept" -> {
                plugin.getNationInvitationManager().acceptInvitation(playerTown.getUuid())
                        .thenAccept(success -> {
                            if (success) {
                                player.sendMessage("§a✓ Your town has successfully joined the nation!");
                            } else {
                                player.sendMessage("§cFailed to accept the invitation. The invitation may have expired or there was an error.");
                            }
                        });
            }
            case "deny" -> {
                plugin.getNationInvitationManager().denyInvitation(playerTown.getUuid())
                        .thenAccept(success -> {
                            if (success) {
                                player.sendMessage("§c✗ You have declined the nation invitation.");
                            } else {
                                player.sendMessage("§cFailed to decline the invitation. The invitation may have already expired.");
                            }
                        });
            }
            default -> player.sendMessage("§cUsage: /nation invite <accept|deny>");
        }
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation kick <town>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        // Check permissions - king or players with nation.kick permission
        if (!nation.isKing(player.getUniqueId()) &&
            !plugin.getRankManager().playerHasPermission(player.getUniqueId(), "towny.nation.kick")) {
            player.sendMessage("§cYou don't have permission to kick towns from the nation!");
            return;
        }

        String townName = args[1];
        Town town = plugin.getTownManager().getTown(townName);

        if (town == null) {
            player.sendMessage("§cTown not found: " + townName);
            return;
        }

        if (!nation.hasTown(town.getUuid())) {
            player.sendMessage("§cThat town is not part of this nation!");
            return;
        }

        if (nation.isCapitalTown(town.getUuid())) {
            player.sendMessage("§cCannot kick the capital town! Set a new capital first.");
            return;
        }

        plugin.getNationManager().removeTownFromNation(nation.getUuid(), town.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a" + town.getName() + " has been kicked from the nation!");

                        // Notify town mayor
                        Player mayor = plugin.getServer().getPlayer(town.getMayorUuid());
                        if (mayor != null) {
                            mayor.sendMessage("§cYour town " + town.getName() + " has been kicked from the nation " + nation.getName() + "!");
                        }
                    } else {
                        player.sendMessage("§cFailed to kick town from nation.");
                    }
                });
    }

    private void handleRank(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /nation rank set <player> <rank>");
            player.sendMessage("§cAvailable ranks: citizen, advisor, minister");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        // Check permissions - king or players with nation.rank permission
        if (!nation.isKing(player.getUniqueId()) &&
            !plugin.getRankManager().playerHasPermission(player.getUniqueId(), "towny.nation.rank")) {
            player.sendMessage("§cYou don't have permission to manage nation ranks!");
            return;
        }

        String action = args[1].toLowerCase();
        String targetPlayerName = args[2];

        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            player.sendMessage("§cPlayer not found or not online!");
            return;
        }

        TownyPlayer targetTownyPlayer = plugin.getPlayerManager().getPlayer(targetPlayer.getUniqueId());
        if (targetTownyPlayer == null || !targetTownyPlayer.hasNation() ||
            !targetTownyPlayer.getNationUuid().equals(nation.getUuid())) {
            player.sendMessage("§cPlayer must be a citizen of this nation!");
            return;
        }

        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    player.sendMessage("§cUsage: /nation rank set <player> <rank>");
                    return;
                }

                String rankName = args[3];
                var rank = plugin.getRankManager().getRank(rankName);

                if (rank == null || !rank.isNationRank()) {
                    player.sendMessage("§cInvalid nation rank: " + rankName);
                    return;
                }

                plugin.getRankManager().setPlayerRank(targetPlayer.getUniqueId(), rank.getUuid())
                    .thenAccept(success -> {
                        if (success) {
                            player.sendMessage("§aSet " + targetPlayer.getName() + "'s nation rank to " + rank.getDisplayName());
                            targetPlayer.sendMessage("§aYour nation rank has been set to " + rank.getDisplayName());
                        } else {
                            player.sendMessage("§cFailed to set rank!");
                        }
                    });
            }
            default -> player.sendMessage("§cInvalid action. Use: set");
        }
    }

    private void handleSetCapitalChunk(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null || !nation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to set the capital chunk!");
            return;
        }

        // Get current chunk and find corresponding claimed chunk UUID
        ClaimedChunk claimedChunk = plugin.getChunkManager().getClaimedChunk(player.getLocation());
        if (claimedChunk == null) {
            player.sendMessage("§cYou must be standing in a claimed chunk!");
            return;
        }

        // Check if chunk belongs to capital town
        Town capitalTown = plugin.getTownManager().getTown(nation.getCapitalTownUuid());
        if (capitalTown == null || !claimedChunk.getTownUuid().equals(capitalTown.getUuid())) {
            player.sendMessage("§cCapital chunk must be in the capital town!");
            return;
        }

        plugin.getNationManager().setCapitalChunk(nation.getUuid(), player.getUniqueId(), claimedChunk.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aNation capital chunk has been set!");
                    } else {
                        player.sendMessage("§cFailed to set capital chunk. Check nation funds.");
                    }
                });
    }

    private void handleSet(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /nation set <property> <value>");
            player.sendMessage("§cProperties: name, board, tax");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null || !nation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to change nation settings!");
            return;
        }

        String property = args[1].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        switch (property) {
            case "name" -> {
                if (plugin.getNationManager().nationNameExists(value)) {
                    player.sendMessage("§cNation name already exists.");
                    return;
                }
                nation.setName(value);
                player.sendMessage("§aNation name changed to: " + value);
            }
            case "board" -> {
                nation.setBoard(value);
                player.sendMessage("§aNation board updated.");
            }
            case "tax" -> {
                try {
                    BigDecimal tax = new BigDecimal(value);
                    if (tax.compareTo(BigDecimal.ZERO) < 0) {
                        player.sendMessage("§cTax rate cannot be negative.");
                        return;
                    }
                    nation.setTaxRate(tax);
                    player.sendMessage("§aNation tax rate set to: " + tax + "%");
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid tax rate.");
                    return;
                }
            }
            default -> player.sendMessage("§cInvalid property. Use: name, board, tax");
        }

        plugin.getNationManager().saveNation(nation);
    }

    private void handleToggle(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation toggle <flag>");
            player.sendMessage("§cFlags: open, public");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null || !nation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to toggle nation flags!");
            return;
        }

        String flag = args[1].toLowerCase();

        switch (flag) {
            case "open" -> {
                nation.setOpen(!nation.isOpen());
                player.sendMessage("§aNation is now " + (nation.isOpen() ? "open" : "closed") + " to new towns.");
            }
            case "public" -> {
                nation.setPublic(!nation.isPublic());
                player.sendMessage("§aNation is now " + (nation.isPublic() ? "public" : "private") + ".");
            }
            default -> player.sendMessage("§cInvalid flag. Use: open, public");
        }

        plugin.getNationManager().saveNation(nation);
    }

    private void handleDeposit(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation deposit <amount>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(args[1]);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                player.sendMessage(plugin.getConfigManager().getMessage("general.invalid-amount"));
                return;
            }

            if (plugin.getEconomyManager().hasPlayerBalance(player.getUniqueId(), amount)) {
                plugin.getEconomyManager().withdrawPlayer(player.getUniqueId(), amount);
                plugin.getEconomyManager().depositNation(townyPlayer.getNationUuid(), amount);
                player.sendMessage("§aDeposited " + plugin.getEconomyManager().format(amount) + " to nation bank.");
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("general.insufficient-funds"));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.invalid-amount"));
        }
    }

    private void handleWithdraw(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation withdraw <amount>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null || !nation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to withdraw from nation bank!");
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(args[1]);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                player.sendMessage(plugin.getConfigManager().getMessage("general.invalid-amount"));
                return;
            }

            if (plugin.getEconomyManager().hasNationBalance(nation.getUuid(), amount)) {
                plugin.getEconomyManager().withdrawNation(nation.getUuid(), amount);
                plugin.getEconomyManager().depositPlayer(player.getUniqueId(), amount);
                player.sendMessage("§aWithdrew " + plugin.getEconomyManager().format(amount) + " from nation bank.");
            } else {
                player.sendMessage("§cInsufficient nation funds.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.invalid-amount"));
        }
    }

    private void handleInfo(Player player, String[] args) {
        String nationName = null;
        if (args.length > 1) {
            nationName = args[1];
        }

        Nation nation;
        if (nationName != null) {
            nation = plugin.getNationManager().getNation(nationName);
            if (nation == null) {
                player.sendMessage("§cNation not found.");
                return;
            }
        } else {
            TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
            if (townyPlayer == null || !townyPlayer.hasNation()) {
                player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
                return;
            }
            nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        }

        if (nation == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        displayNationInfo(player, nation);
    }

    private void handleList(Player player, String[] args) {
        List<Nation> nations = new ArrayList<>(plugin.getNationManager().getAllNations());

        if (nations.isEmpty()) {
            player.sendMessage("§cNo nations exist.");
            return;
        }

        // Sort by town count
        nations.sort((n1, n2) -> Integer.compare(n2.getTownCount(), n1.getTownCount()));

        player.sendMessage("§6=== Nations (" + nations.size() + ") ===");

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
        int endIndex = Math.min(startIndex + itemsPerPage, nations.size());

        for (int i = startIndex; i < endIndex; i++) {
            Nation nation = nations.get(i);
            String status = nation.isOpen() ? "§aOpen" : "§cClosed";
            player.sendMessage("§e" + (i + 1) + ". §f" + nation.getName() +
                    " §7(" + nation.getTownCount() + " towns) " + status);
        }

        int totalPages = (int) Math.ceil((double) nations.size() / itemsPerPage);
        if (totalPages > 1) {
            player.sendMessage("§7Page " + page + " of " + totalPages +
                    " | Use /nation list <page> to view other pages");
        }
    }

    private void handleSetKing(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation king <player>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null || !nation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to transfer leadership!");
            return;
        }

        Player newKing = plugin.getServer().getPlayer(args[1]);
        if (newKing == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.player-not-found"));
            return;
        }

        plugin.getNationManager().setNationKing(nation.getUuid(), newKing.getUniqueId())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a" + newKing.getName() + " is now the king of " + nation.getName() + "!");
                        newKing.sendMessage("§aYou are now the king of " + nation.getName() + "!");
                    } else {
                        player.sendMessage("§cFailed to transfer leadership. Player must be a mayor in the nation.");
                    }
                });
    }

    private void handleSetCapital(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation capital <town>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null || !nation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to change the capital!");
            return;
        }

        Town newCapital = plugin.getTownManager().getTown(args[1]);
        if (newCapital == null) {
            player.sendMessage("§cTown not found.");
            return;
        }

        plugin.getNationManager().setNationCapital(nation.getUuid(), newCapital.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a" + newCapital.getName() + " is now the capital of " + nation.getName() + "!");
                    } else {
                        player.sendMessage("§cFailed to set capital. Town must be in the nation.");
                    }
                });
    }

    private void displayNationInfo(Player player, Nation nation) {
        // Get real-time balance from EconomyManager
        BigDecimal nationBalance = plugin.getEconomyManager().getNationBalance(nation.getUuid());

        player.sendMessage("§6=== Nation Info: " + nation.getName() + " ===");
        player.sendMessage("§eKing: §f" + getKingName(nation));
        player.sendMessage("§eTowns: §f" + nation.getTownCount() + "/" + nation.getMaxTowns());
        player.sendMessage("§eBalance: §f" + plugin.getEconomyManager().format(nationBalance));
        player.sendMessage("§eOpen: §f" + (nation.isOpen() ? "Yes" : "No"));
        player.sendMessage("§ePublic: §f" + (nation.isPublic() ? "Yes" : "No"));

        if (nation.getCapitalTownUuid() != null) {
            Town capital = plugin.getTownManager().getTown(nation.getCapitalTownUuid());
            player.sendMessage("§eCapital: §f" + (capital != null ? capital.getName() : "Unknown"));
        }

        if (nation.getBoard() != null && !nation.getBoard().isEmpty()) {
            player.sendMessage("§eBoard: §f" + nation.getBoard());
        }
    }

    private String getKingName(Nation nation) {
        Player king = plugin.getServer().getPlayer(nation.getKingUuid());
        if (king != null) {
            return king.getName();
        }

        TownyPlayer townyKing = plugin.getPlayerManager().getPlayer(nation.getKingUuid());
        return townyKing != null ? townyKing.getName() : "Unknown";
    }

    private String getCapitalName(Nation nation) {
        Town capital = plugin.getTownManager().getTown(nation.getCapitalUuid());
        return capital != null ? capital.getName() : "Unknown";
    }

    private String getTownNames(Nation nation) {
        StringBuilder towns = new StringBuilder();
        int count = 0;
        int maxDisplay = 5;

        for (UUID townUuid : nation.getTowns()) {
            if (count >= maxDisplay) {
                towns.append(" and ").append(nation.getTownCount() - maxDisplay).append(" more");
                break;
            }

            if (count > 0) towns.append(", ");

            Town town = plugin.getTownManager().getTown(townUuid);
            towns.append(town != null ? town.getName() : "Unknown");
            count++;
        }

        return towns.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "create", "delete", "invite", "kick", "join", "leave", "rank",
                    "set", "toggle", "deposit", "withdraw", "info", "list",
                    "king", "capital", "capitalchunk", "deputy"
            );

            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "invite", "kick" -> {
                    for (Town town : plugin.getTownManager().getAllTowns()) {
                        if (town.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(town.getName());
                        }
                    }
                }
                case "rank" -> {
                    List<String> rankActions = Arrays.asList("set");
                    for (String action : rankActions) {
                        if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(action);
                        }
                    }
                }
                case "deputy" -> {
                    List<String> deputyActions = Arrays.asList("set", "remove");
                    for (String action : deputyActions) {
                        if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(action);
                        }
                    }
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if ("deputy".equals(subCommand)) {
                // Show players in the nation for deputy management
                if (sender instanceof Player player) {
                    TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
                    if (townyPlayer != null && townyPlayer.hasNation()) {
                        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
                        if (nation != null) {
                            for (UUID townUuid : nation.getTowns()) {
                                Town town = plugin.getTownManager().getTown(townUuid);
                                if (town != null) {
                                    for (UUID residentUuid : town.getResidents()) {
                                        Player resident = plugin.getServer().getPlayer(residentUuid);
                                        if (resident != null && resident.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                            completions.add(resident.getName());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("rank") && args[1].equalsIgnoreCase("set")) {
            // Nation ranks
            List<String> ranks = Arrays.asList("citizen", "advisor", "minister");
            for (String rank : ranks) {
                if (rank.toLowerCase().startsWith(args[3].toLowerCase())) {
                    completions.add(rank);
                }
            }
        }

        return completions;
    }

    private void showHelp(Player player) {
        player.sendMessage("§6=== Nation Commands ===");
        player.sendMessage("§e/nation §7- Show nation info");
        player.sendMessage("§e/nation create <name> §7- Create a new nation");
        player.sendMessage("§e/nation delete §7- Delete your nation");
        player.sendMessage("§e/nation invite <town> §7- Invite a town to join");
        player.sendMessage("§e/nation kick <town> §7- Kick a town from nation");
        player.sendMessage("§e/nation rank set <player> <rank> §7- Set player's nation rank");
        player.sendMessage("§e/nation capitalchunk §7- Set capital chunk (expensive)");
        player.sendMessage("§e/nation info [nation] §7- Show nation information");
        player.sendMessage("§e/nation list [page] §7- List all nations");
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation join <nation>");
            return;
        }

        String nationName = args[1];
        Nation nation = plugin.getNationManager().getNation(nationName);

        if (nation == null) {
            player.sendMessage("§cNation not found: " + nationName);
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage("§cYou must be in a town to join a nation!");
            return;
        }

        Town playerTown = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (playerTown == null || !playerTown.isMayor(player.getUniqueId())) {
            player.sendMessage("§cYou must be the mayor of a town to join a nation!");
            return;
        }

        if (playerTown.hasNation()) {
            player.sendMessage("§cYour town is already part of a nation!");
            return;
        }

        if (!nation.isOpen()) {
            player.sendMessage("§cThis nation is not open for new towns!");
            return;
        }

        if (!nation.canAddTown()) {
            player.sendMessage("§cNation has reached maximum town limit!");
            return;
        }

        // Send confirmation message with clickable buttons
        player.sendMessage("§6=== Nation Join Confirmation ===");
        player.sendMessage("§eYou are about to join the nation: §a" + nation.getName());
        player.sendMessage("§eYour town: §f" + playerTown.getName());
        player.sendMessage("§eNation tax rate: §c" + nation.getTaxRate() + "%");
        player.sendMessage("§7This action cannot be undone easily.");
        player.sendMessage("");

        // Create clickable confirm/cancel buttons
        net.kyori.adventure.text.Component confirmButton = net.kyori.adventure.text.Component.text("§a[CONFIRM]")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/nation confirm_join " + nation.getName()))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        net.kyori.adventure.text.Component.text("§aClick to confirm joining the nation")));

        net.kyori.adventure.text.Component cancelButton = net.kyori.adventure.text.Component.text("§c[CANCEL]")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/nation cancel_join"))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        net.kyori.adventure.text.Component.text("§cClick to cancel")));

        net.kyori.adventure.text.Component message = confirmButton
                .append(net.kyori.adventure.text.Component.text("§7   "))
                .append(cancelButton);

        player.sendMessage(message);
        player.sendMessage("§7Or use: §e/nation confirm_join " + nation.getName() + "§7 or §e/nation cancel_join");
    }

    private void handleLeave(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        if (!townyPlayer.hasTown()) {
            player.sendMessage("§cYou must be in a town to leave a nation!");
            return;
        }

        Town playerTown = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (playerTown == null || !playerTown.isMayor(player.getUniqueId())) {
            player.sendMessage("§cYou must be the mayor of a town to leave a nation!");
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        if (nation.isCapitalTown(playerTown.getUuid())) {
            player.sendMessage("§cThe capital town cannot leave the nation! Transfer capital first or disband the nation.");
            return;
        }

        // Send confirmation message with clickable buttons
        player.sendMessage("§6=== Nation Leave Confirmation ===");
        player.sendMessage("§eYou are about to leave the nation: §c" + nation.getName());
        player.sendMessage("§eYour town: §f" + playerTown.getName());
        player.sendMessage("§c§lWARNING: §cThis will remove your town from the nation!");
        player.sendMessage("§7You will lose nation benefits and protection.");
        player.sendMessage("");

        // Create clickable confirm/cancel buttons
        net.kyori.adventure.text.Component confirmButton = net.kyori.adventure.text.Component.text("§c[CONFIRM LEAVE]")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/nation confirm_leave"))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        net.kyori.adventure.text.Component.text("§cClick to confirm leaving the nation")));

        net.kyori.adventure.text.Component cancelButton = net.kyori.adventure.text.Component.text("§a[CANCEL]")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/nation cancel_leave"))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        net.kyori.adventure.text.Component.text("§aClick to cancel and stay in nation")));

        net.kyori.adventure.text.Component message = confirmButton
                .append(net.kyori.adventure.text.Component.text("§7   "))
                .append(cancelButton);

        player.sendMessage(message);
        player.sendMessage("§7Or use: §e/nation confirm_leave§7 or §e/nation cancel_leave");
    }

    private void handleConfirmJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cInvalid confirmation command.");
            return;
        }

        String nationName = args[1];
        Nation nation = plugin.getNationManager().getNation(nationName);

        if (nation == null) {
            player.sendMessage("§cNation not found: " + nationName);
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage("§cYou must be in a town to join a nation!");
            return;
        }

        Town playerTown = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (playerTown == null || !playerTown.isMayor(player.getUniqueId())) {
            player.sendMessage("§cYou must be the mayor of a town to join a nation!");
            return;
        }

        if (playerTown.hasNation()) {
            player.sendMessage("§cYour town is already part of a nation!");
            return;
        }

        plugin.getNationManager().addTownToNation(nation.getUuid(), playerTown.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a✓ Your town " + playerTown.getName() + " has successfully joined the nation " + nation.getName() + "!");

                        // Notify nation king
                        Player king = plugin.getServer().getPlayer(nation.getKingUuid());
                        if (king != null) {
                            king.sendMessage("§a" + playerTown.getName() + " has joined your nation!");
                        }
                    } else {
                        player.sendMessage("§cFailed to join nation. The nation may be full or there was an error.");
                    }
                });
    }

    private void handleConfirmLeave(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Town playerTown = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (playerTown == null || !playerTown.isMayor(player.getUniqueId())) {
            player.sendMessage("§cYou must be the mayor of a town to leave a nation!");
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        plugin.getNationManager().removeTownFromNation(nation.getUuid(), playerTown.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§c✓ Your town " + playerTown.getName() + " has left the nation " + nation.getName() + ".");

                        // Notify nation king
                        Player king = plugin.getServer().getPlayer(nation.getKingUuid());
                        if (king != null) {
                            king.sendMessage("§c" + playerTown.getName() + " has left your nation.");
                        }
                    } else {
                        player.sendMessage("§cFailed to leave nation.");
                    }
                });
    }

    private void handleCancelJoin(Player player, String[] args) {
        player.sendMessage("§7Nation join cancelled.");
    }

    private void handleCancelLeave(Player player, String[] args) {
        player.sendMessage("§7Nation leave cancelled. You remain in the nation.");
    }

    private void handleDeputy(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /nation deputy set <player>");
            player.sendMessage("§cUsage: /nation deputy remove <player>");
            return;
        }

        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        // Check permissions - king or players with nation.deputy permission
        if (!nation.isKing(player.getUniqueId()) &&
            !plugin.getRankManager().playerHasPermission(player.getUniqueId(), "towny.nation.deputy")) {
            player.sendMessage("§cYou don't have permission to manage nation deputies!");
            return;
        }

        String action = args[1].toLowerCase();
        String targetPlayerName = args[2];

        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            player.sendMessage("§cPlayer not found or not online!");
            return;
        }

        TownyPlayer targetTownyPlayer = plugin.getPlayerManager().getPlayer(targetPlayer.getUniqueId());
        if (targetTownyPlayer == null || !targetTownyPlayer.hasNation() ||
            !targetTownyPlayer.getNationUuid().equals(nation.getUuid())) {
            player.sendMessage("§cPlayer must be a citizen of this nation!");
            return;
        }

        switch (action) {
            case "set" -> {
                plugin.getRankManager().setPlayerRank(targetPlayer.getUniqueId(),
                        plugin.getRankManager().getRank("deputy").getUuid())
                    .thenAccept(success -> {
                        if (success) {
                            player.sendMessage("§a" + targetPlayer.getName() + " has been promoted to deputy.");
                            targetPlayer.sendMessage("§aYou have been promoted to deputy of the nation.");
                        } else {
                            player.sendMessage("§cFailed to set deputy rank.");
                        }
                    });
            }
            case "remove" -> {
                // Check if the player is currently a deputy
                if (!plugin.getRankManager().playerHasPermission(targetPlayer.getUniqueId(), "towny.nation.deputy")) {
                    player.sendMessage("§cThat player is not a deputy.");
                    return;
                }

                // Set player back to default citizen rank instead of removing rank entirely
                var citizenRank = plugin.getRankManager().getRank("citizen");
                if (citizenRank == null) {
                    player.sendMessage("§cFailed to find citizen rank!");
                    return;
                }

                plugin.getRankManager().setPlayerRank(targetPlayer.getUniqueId(), citizenRank.getUuid())
                    .thenAccept(success -> {
                        if (success) {
                            player.sendMessage("§a" + targetPlayer.getName() + " has been removed from deputy and set back to citizen.");
                            targetPlayer.sendMessage("§aYou have been removed from deputy of the nation and are now a citizen.");
                        } else {
                            player.sendMessage("§cFailed to remove deputy rank.");
                        }
                    });
            }
            default -> player.sendMessage("§cInvalid action. Use: set, remove");
        }
    }

    // Helper method to check if player is king or deputy
    private boolean hasNationManagementPermission(Nation nation, UUID playerUuid) {
        return nation.isKing(playerUuid) ||
               plugin.getRankManager().playerHasPermission(playerUuid, "towny.nation.deputy");
    }
}
