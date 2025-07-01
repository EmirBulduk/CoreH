package org.arch.me.commands;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.BufferZone;
import org.arch.me.util.NameValidator;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TownyAdminCommand implements CommandExecutor, TabCompleter {

    private final EnhancedCoreH plugin;

    public TownyAdminCommand(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            case "buffer" -> handleBuffer(sender, args);
            case "reload" -> handleReload(sender, args);
            case "save" -> handleSave(sender, args);
            case "purge" -> handlePurge(sender, args);
            default -> showHelp(sender);
        }

        return true;
    }

    private void handleBuffer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /townyadmin buffer <create|delete|list|info|toggle>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create" -> handleBufferCreate(sender, args);
            case "delete" -> handleBufferDelete(sender, args);
            case "list" -> handleBufferList(sender, args);
            case "info" -> handleBufferInfo(sender, args);
            case "toggle" -> handleBufferToggle(sender, args);
            default -> sender.sendMessage("§cInvalid buffer action. Use: create, delete, list, info, toggle");
        }
    }

    private void handleBufferCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /townyadmin buffer create <name> [reason]");
            sender.sendMessage("§cFirst select two corners with /townyadmin buffer pos1 and pos2");
            return;
        }

        String bufferName = args[2];

        // Validate buffer zone name
        if (!NameValidator.isValidName(bufferName)) {
            player.sendMessage("§cInvalid buffer zone name: " + NameValidator.getValidationError(bufferName));
            return;
        }

        // Check if player has selected positions (we'll use a simple system with metadata)
        Location pos1 = getPlayerPosition(player, "pos1");
        Location pos2 = getPlayerPosition(player, "pos2");

        if (pos1 == null || pos2 == null) {
            player.sendMessage("§cYou must select two corners first!");
            player.sendMessage("§cUse: §e/townyadmin buffer pos1 §cand §e/townyadmin buffer pos2");
            return;
        }

        String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "Admin protected area";

        plugin.getBufferZoneManager().createBufferZone(bufferName, pos1, pos2, player.getUniqueId(), reason)
                .thenAccept(zone -> {
                    if (zone != null) {
                        player.sendMessage("§aBuffer zone '" + bufferName + "' created successfully!");
                        player.sendMessage("§aProtected " + zone.getChunkCount() + " chunks.");
                    } else {
                        player.sendMessage("§cFailed to create buffer zone!");
                    }
                });
    }

    private void handleBufferDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /townyadmin buffer delete <name>");
            return;
        }

        String bufferName = args[2];
        BufferZone zone = plugin.getBufferZoneManager().getBufferZone(bufferName);

        if (zone == null) {
            sender.sendMessage("§cBuffer zone not found: " + bufferName);
            return;
        }

        plugin.getBufferZoneManager().deleteBufferZone(zone.getUuid())
                .thenAccept(success -> {
                    if (success) {
                        sender.sendMessage("§aBuffer zone '" + bufferName + "' deleted successfully!");
                    } else {
                        sender.sendMessage("§cFailed to delete buffer zone!");
                    }
                });
    }

    private void handleBufferList(CommandSender sender, String[] args) {
        var zones = plugin.getBufferZoneManager().getAllBufferZones();

        if (zones.isEmpty()) {
            sender.sendMessage("§cNo buffer zones exist.");
            return;
        }

        sender.sendMessage("§6=== Buffer Zones (" + zones.size() + ") ===");

        int page = 1;
        if (args.length > 2) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        int itemsPerPage = 10;
        List<BufferZone> zoneList = new ArrayList<>(zones);
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, zoneList.size());

        for (int i = startIndex; i < endIndex; i++) {
            BufferZone zone = zoneList.get(i);
            sender.sendMessage("§e" + (i + 1) + ". §f" + zone.getName() +
                    " §7(" + zone.getChunkCount() + " chunks in " + zone.getWorldName() + ")");
        }

        int totalPages = (int) Math.ceil((double) zones.size() / itemsPerPage);
        if (totalPages > 1) {
            sender.sendMessage("§7Page " + page + " of " + totalPages);
        }
    }

    private void handleBufferInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            if (sender instanceof Player player) {
                // Show info for current location
                BufferZone zone = plugin.getBufferZoneManager().getBufferZoneAtLocation(player.getLocation());
                if (zone == null) {
                    player.sendMessage("§cNo buffer zone at your current location.");
                    return;
                }
                displayBufferZoneInfo(sender, zone);
            } else {
                sender.sendMessage("§cUsage: /townyadmin buffer info <name>");
            }
            return;
        }

        String bufferName = args[2];
        BufferZone zone = plugin.getBufferZoneManager().getBufferZone(bufferName);

        if (zone == null) {
            sender.sendMessage("§cBuffer zone not found: " + bufferName);
            return;
        }

        displayBufferZoneInfo(sender, zone);
    }

    private void handleBufferToggle(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /townyadmin buffer toggle <name> <flag>");
            sender.sendMessage("§cFlags: build, destroy, interact, switch, itemuse");
            return;
        }

        String bufferName = args[2];
        String flag = args[3].toLowerCase();

        BufferZone zone = plugin.getBufferZoneManager().getBufferZone(bufferName);
        if (zone == null) {
            sender.sendMessage("§cBuffer zone not found: " + bufferName);
            return;
        }

        switch (flag) {
            case "build", "destroy", "interact", "switch", "itemuse" -> {
                boolean current = zone.getFlag(flag);
                zone.setFlag(flag, !current);
                plugin.getBufferZoneManager().saveBufferZone(zone);
                sender.sendMessage("§a" + flag + " is now " + (!current ? "enabled" : "disabled") + " in buffer zone '" + bufferName + "'.");
            }
            default -> sender.sendMessage("§cInvalid flag. Use: build, destroy, interact, switch, itemuse");
        }
    }

    private void handleReload(CommandSender sender, String[] args) {
        sender.sendMessage("§aReloading EnhancedCoreH configuration...");

        try {
            plugin.getConfigManager().reloadConfig();
            sender.sendMessage("§aConfiguration reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage("§cFailed to reload configuration: " + e.getMessage());
        }
    }

    private void handleSave(CommandSender sender, String[] args) {
        sender.sendMessage("§aSaving all data...");

        try {
            plugin.getTownManager().saveAll();
            plugin.getNationManager().saveAll();
            sender.sendMessage("§aAll data saved successfully!");
        } catch (Exception e) {
            sender.sendMessage("§cFailed to save data: " + e.getMessage());
        }
    }

    private void handlePurge(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /townyadmin purge <towns|nations|players> [days]");
            sender.sendMessage("§cThis will remove inactive data older than specified days (default: 30)");
            return;
        }

        sender.sendMessage("§cPurge functionality not yet implemented.");
        // TODO: Implement purge functionality for inactive towns/nations/players
    }

    private void displayBufferZoneInfo(CommandSender sender, BufferZone zone) {
        sender.sendMessage("§6=== Buffer Zone: " + zone.getName() + " ===");
        sender.sendMessage("§eWorld: §f" + zone.getWorldName());
        sender.sendMessage("§eChunks: §f" + zone.getChunkCount());
        sender.sendMessage("§eArea: §f(" + zone.getX1() + "," + zone.getZ1() + ") to (" + zone.getX2() + "," + zone.getZ2() + ")");
        sender.sendMessage("§eCreated by: §f" + getCreatorName(zone));
        sender.sendMessage("§eReason: §f" + (zone.getReason() != null ? zone.getReason() : "No reason specified"));

        // Show flags
        StringBuilder flags = new StringBuilder();
        zone.getFlags().forEach((flag, value) -> {
            if (flags.length() > 0) flags.append(", ");
            flags.append(flag).append(": ").append(value ? "§aAllowed" : "§cDenied");
        });
        if (flags.length() > 0) {
            sender.sendMessage("§eFlags: §f" + flags);
        }
    }

    private String getCreatorName(BufferZone zone) {
        Player creator = plugin.getServer().getPlayer(zone.getCreatedBy());
        if (creator != null) {
            return creator.getName();
        }
        return "Unknown";
    }

    private Location getPlayerPosition(Player player, String posKey) {
        // Simple implementation using player metadata
        // In a real implementation, you might want to use a more sophisticated storage system
        return player.hasMetadata("towny_" + posKey) ?
               (Location) player.getMetadata("towny_" + posKey).get(0).value() : null;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== TownyAdmin Commands ===");
        sender.sendMessage("§e/townyadmin buffer create <name> [reason] §7- Create buffer zone");
        sender.sendMessage("§e/townyadmin buffer delete <name> §7- Delete buffer zone");
        sender.sendMessage("§e/townyadmin buffer list [page] §7- List buffer zones");
        sender.sendMessage("§e/townyadmin buffer info [name] §7- Buffer zone info");
        sender.sendMessage("§e/townyadmin buffer toggle <name> <flag> §7- Toggle flag");
        sender.sendMessage("§e/townyadmin buffer pos1 §7- Set first corner");
        sender.sendMessage("§e/townyadmin buffer pos2 §7- Set second corner");
        sender.sendMessage("§e/townyadmin reload §7- Reload configuration");
        sender.sendMessage("§e/townyadmin save §7- Save all data");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("buffer", "reload", "save", "purge");
            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("buffer")) {
            List<String> bufferCommands = Arrays.asList("create", "delete", "list", "info", "toggle", "pos1", "pos2");
            for (String cmd : bufferCommands) {
                if (cmd.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("buffer")) {
            String bufferAction = args[1].toLowerCase();
            if (bufferAction.equals("delete") || bufferAction.equals("info") || bufferAction.equals("toggle")) {
                for (BufferZone zone : plugin.getBufferZoneManager().getAllBufferZones()) {
                    if (zone.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(zone.getName());
                    }
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("buffer") && args[1].equalsIgnoreCase("toggle")) {
            List<String> flags = Arrays.asList("build", "destroy", "interact", "switch", "itemuse");
            for (String flag : flags) {
                if (flag.toLowerCase().startsWith(args[3].toLowerCase())) {
                    completions.add(flag);
                }
            }
        }

        return completions;
    }
}
