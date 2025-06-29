package org.arch.me.commands;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.ClaimedChunk;
import org.arch.me.models.Nation;
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
            case "invite" -> handleInvite(player, args);
            case "kick" -> handleKick(player, args);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player, args);
            case "set" -> handleSet(player, args);
            case "toggle" -> handleToggle(player, args);
            case "deposit" -> handleDeposit(player, args);
            case "withdraw" -> handleWithdraw(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player, args);
            case "king" -> handleSetKing(player, args);
            case "capital" -> handleSetCapital(player, args);
            case "capitalchunk" -> handleSetCapitalChunk(player, args);
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

        // Get current chunk
        org.bukkit.Chunk chunk = player.getLocation().getChunk();

        // Check if chunk is claimed by capital town
        Town capitalTown = plugin.getTownManager().getTown(nation.getCapitalTownUuid());
        if (capitalTown == null) {
            player.sendMessage("§cCapital town not found!");
            return;
        }

        boolean isCapitalChunk = false;
        UUID chunkUuid = null;

        for (ClaimedChunk claimedChunk : capitalTown.getClaimedChunks()) {
            if (claimedChunk.getChunkX() == chunk.getX() &&
                    claimedChunk.getChunkZ() == chunk.getZ() &&
                    claimedChunk.getWorldName().equals(chunk.getWorld().getName())) {
                isCapitalChunk = true;
                chunkUuid = claimedChunk.getUuid();
                break;
            }
        }

        if (!isCapitalChunk) {
            player.sendMessage("§cThis chunk is not claimed by your capital town!");
            return;
        }

        nation.setCapitalChunkUuid(chunkUuid);
        plugin.getNationManager().saveNation(nation);

        player.sendMessage("§aCapital chunk has been set at " + chunk.getX() + ", " + chunk.getZ() + "!");
        player.sendMessage("§eThis chunk will be used for war capitulation mechanics.");
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation create <name>");
            return;
        }

        String nationName = args[1];
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());

        // Check if player has a town
        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage("§cYou must be in a town to create a nation!");
            return;
        }

        Town playerTown = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (playerTown == null) {
            player.sendMessage("§cTown not found!");
            return;
        }

        // Check if player is mayor
        if (!playerTown.isMayor(player.getUniqueId())) {
            player.sendMessage("§cYou must be a mayor to create a nation!");
            return;
        }

        // Check if town is already in a nation
        if (playerTown.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.already-in-nation"));
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

        // Withdraw money first
        plugin.getEconomyManager().withdrawPlayer(player.getUniqueId(), cost);

        // Create nation
        plugin.getNationManager().createNation(nationName, player.getUniqueId(), playerTown.getUuid())
                .thenAccept(nation -> {
                    if (nation != null) {
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

        String townName = args[1];
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());

        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null || !nation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to invite towns!");
            return;
        }

        Town targetTown = plugin.getTownManager().getTown(townName);
        if (targetTown == null) {
            player.sendMessage("§cTown not found.");
            return;
        }

        if (targetTown.hasNation()) {
            player.sendMessage("§cThat town is already in a nation.");
            return;
        }

        plugin.getNationManager().addTownToNation(nation.getUuid(), targetTown.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a" + targetTown.getName() + " has been invited to the nation.");
                        // Notify town mayor
                        Player mayor = plugin.getServer().getPlayer(targetTown.getMayorUuid());
                        if (mayor != null) {
                            mayor.sendMessage("§aYour town has been invited to join " + nation.getName() + "!");
                        }
                    } else {
                        player.sendMessage("§cFailed to invite town.");
                    }
                });
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation kick <town>");
            return;
        }

        String townName = args[1];
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());

        if (townyPlayer == null || !townyPlayer.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(townyPlayer.getNationUuid());
        if (nation == null || !nation.isKing(player.getUniqueId())) {
            player.sendMessage("§cYou must be the king to kick towns!");
            return;
        }

        Town targetTown = plugin.getTownManager().getTown(townName);
        if (targetTown == null) {
            player.sendMessage("§cTown not found.");
            return;
        }

        if (!nation.hasTown(targetTown.getUuid())) {
            player.sendMessage("§cThat town is not in your nation.");
            return;
        }

        if (nation.getCapitalUuid().equals(targetTown.getUuid())) {
            player.sendMessage("§cYou cannot kick the capital town!");
            return;
        }

        plugin.getNationManager().removeTownFromNation(nation.getUuid(), targetTown.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a" + targetTown.getName() + " has been kicked from the nation.");
                        // Notify town mayor
                        Player mayor = plugin.getServer().getPlayer(targetTown.getMayorUuid());
                        if (mayor != null) {
                            mayor.sendMessage("§cYour town has been kicked from " + nation.getName() + "!");
                        }
                    } else {
                        player.sendMessage("§cFailed to kick town.");
                    }
                });
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /nation join <nation>");
            return;
        }

        String nationName = args[1];
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());

        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage("§cYou must be in a town to join a nation!");
            return;
        }

        Town playerTown = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (playerTown == null || !playerTown.isMayor(player.getUniqueId())) {
            player.sendMessage("§cYou must be a mayor to join a nation!");
            return;
        }

        if (playerTown.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.already-in-nation"));
            return;
        }

        Nation targetNation = plugin.getNationManager().getNation(nationName);
        if (targetNation == null) {
            player.sendMessage("§cNation not found.");
            return;
        }

        if (!targetNation.isOpen()) {
            player.sendMessage("§cThat nation is not open for new towns.");
            return;
        }

        plugin.getNationManager().addTownToNation(targetNation.getUuid(), playerTown.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getConfigManager().getMessage("nation.joined", playerTown.getName(), targetNation.getName()));
                    } else {
                        player.sendMessage("§cFailed to join nation.");
                    }
                });
    }

    private void handleLeave(Player player, String[] args) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());

        if (townyPlayer == null || !townyPlayer.hasTown()) {
            player.sendMessage("§cYou must be in a town!");
            return;
        }

        Town playerTown = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (playerTown == null || !playerTown.isMayor(player.getUniqueId())) {
            player.sendMessage("§cYou must be a mayor to leave a nation!");
            return;
        }

        if (!playerTown.hasNation()) {
            player.sendMessage(plugin.getConfigManager().getMessage("nation.not-in-nation"));
            return;
        }

        Nation nation = plugin.getNationManager().getNation(playerTown.getNationUuid());
        if (nation == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("general.error"));
            return;
        }

        plugin.getNationManager().removeTownFromNation(nation.getUuid(), playerTown.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getConfigManager().getMessage("nation.left", playerTown.getName(), nation.getName()));
                    } else {
                        player.sendMessage("§cFailed to leave nation.");
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
        player.sendMessage("§6=== " + nation.getName() + " ===");
        player.sendMessage("§eKing: §f" + getKingName(nation));
        player.sendMessage("§eCapital: §f" + getCapitalName(nation));
        player.sendMessage("§eTowns (" + nation.getTownCount() + "): §f" + getTownNames(nation));
        player.sendMessage("§eBalance: §f" + plugin.getEconomyManager().format(nation.getBalance()));
        player.sendMessage("§eTax Rate: §f" + nation.getTaxRate() + "%");
        player.sendMessage("§eOpen: §f" + (nation.isOpen() ? "Yes" : "No"));
        player.sendMessage("§ePublic: §f" + (nation.isPublic() ? "Yes" : "No"));

        if (nation.getBoard() != null && !nation.getBoard().isEmpty()) {
            player.sendMessage("§eBoard: §f" + nation.getBoard());
        }

        // Show war status if at war
        if (plugin.getWarManager().isNationAtWar(nation.getUuid())) {
            player.sendMessage("§c⚔ AT WAR! Use /war status for details.");
        }
    }

    private void showHelp(Player player) {
        player.sendMessage("§6=== Nation Commands ===");
        player.sendMessage("§e/nation §7- Show nation info");
        player.sendMessage("§e/nation create <name> §7- Create a new nation");
        player.sendMessage("§e/nation delete §7- Delete your nation");
        player.sendMessage("§e/nation invite <town> §7- Invite a town");
        player.sendMessage("§e/nation kick <town> §7- Kick a town");
        player.sendMessage("§e/nation join <nation> §7- Join a nation");
        player.sendMessage("§e/nation leave §7- Leave your nation");
        player.sendMessage("§e/nation deposit <amount> §7- Deposit to nation bank");
        player.sendMessage("§e/nation withdraw <amount> §7- Withdraw from nation bank");
        player.sendMessage("§e/nation set <property> <value> §7- Set nation property");
        player.sendMessage("§e/nation toggle <flag> §7- Toggle nation flag");
        player.sendMessage("§e/nation info [nation] §7- Show nation information");
        player.sendMessage("§e/nation list [page] §7- List all nations");
        player.sendMessage("§e/nation king <player> §7- Transfer leadership");
        player.sendMessage("§e/nation capital <town> §7- Set capital town");
        player.sendMessage("§e/nation capitalchunk §7- Set capital chunk (for wars)");
    }

    // Utility methods
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
                    "create", "delete", "invite", "kick", "join", "leave",
                    "deposit", "withdraw", "set", "toggle", "info", "list",
                    "king", "capital"
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
                case "join", "info" -> {
                    for (Nation nation : plugin.getNationManager().getAllNations()) {
                        if (nation.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(nation.getName());
                        }
                    }
                }
                case "king" -> {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                }
                case "capital" -> {
                    for (Town town : plugin.getTownManager().getAllTowns()) {
                        if (town.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(town.getName());
                        }
                    }
                }
                case "set" -> {
                    List<String> properties = Arrays.asList("name", "board", "tax");
                    for (String prop : properties) {
                        if (prop.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(prop);
                        }
                    }
                }
                case "toggle" -> {
                    List<String> flags = Arrays.asList("open", "public");
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

