package org.arch.me.listeners;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Nation;
import org.arch.me.models.Town;
import org.arch.me.models.TownyPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyListener implements Listener {

    private final EnhancedCoreH plugin;
    private final Map<UUID, LocalDateTime> lastTaxCollection = new HashMap<>();
    private final Map<UUID, LocalDateTime> lastUpkeepCollection = new HashMap<>();

    // Track when servers started to prevent immediate tax collection
    private final LocalDateTime serverStartTime;

    public EconomyListener(EnhancedCoreH plugin) {
        this.plugin = plugin;
        this.serverStartTime = LocalDateTime.now();

        // Start automatic tax and upkeep collection
        startTaxCollectionTask();
        startUpkeepCollectionTask();
        startDailyTaskScheduler();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());

        if (townyPlayer == null || !townyPlayer.hasTown()) {
            return;
        }

        // Check for pending taxes when player joins
        checkPendingTaxes(player, townyPlayer);
    }

    private void checkPendingTaxes(Player player, TownyPlayer townyPlayer) {
        Town town = plugin.getTownManager().getTown(townyPlayer.getTownUuid());
        if (town == null) return;

        UUID playerId = player.getUniqueId();
        LocalDateTime lastCollection = lastTaxCollection.get(playerId);
        LocalDateTime now = LocalDateTime.now();

        // If no previous collection or more than 24 hours passed
        if (lastCollection == null || ChronoUnit.HOURS.between(lastCollection, now) >= 24) {
            collectPlayerTax(player, townyPlayer, town);
        }
    }

    private void collectPlayerTax(Player player, TownyPlayer townyPlayer, Town town) {
        BigDecimal townTaxRate = town.getTaxRate();
        if (townTaxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return; // No town tax
        }

        BigDecimal playerBalance = plugin.getEconomyManager().getPlayerBalance(townyPlayer.getUuid());
        BigDecimal townTaxAmount = playerBalance.multiply(townTaxRate.divide(BigDecimal.valueOf(100)));

        // Minimum tax amount from config
        BigDecimal minTax = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.minimum-town-tax", 1.0));
        townTaxAmount = townTaxAmount.max(minTax);

        if (plugin.getEconomyManager().hasPlayerBalance(townyPlayer.getUuid(), townTaxAmount)) {
            // Collect town tax
            plugin.getEconomyManager().transferPlayerToTown(townyPlayer.getUuid(), town.getUuid(), townTaxAmount);

            if (player.isOnline()) {
                player.sendMessage("§6Town Tax: §f" + plugin.getEconomyManager().format(townTaxAmount) +
                        " collected for " + town.getName());
            }

            // Collect nation tax if applicable
            if (town.hasNation()) {
                collectNationTax(player, townyPlayer, town);
            }

            lastTaxCollection.put(townyPlayer.getUuid(), LocalDateTime.now());
        } else {
            // Player cannot afford tax - warn them
            if (player.isOnline()) {
                player.sendMessage("§cWarning: You cannot afford the town tax of " +
                        plugin.getEconomyManager().format(townTaxAmount) + "!");
                player.sendMessage("§cYou have 7 days to pay or you will be removed from the town!");
            }

            // Schedule removal if configured
            schedulePlayerRemovalForUnpaidTax(townyPlayer, town, townTaxAmount);
        }
    }

    private void collectNationTax(Player player, TownyPlayer townyPlayer, Town town) {
        Nation nation = plugin.getNationManager().getNation(town.getNationUuid());
        if (nation == null) return;

        BigDecimal nationTaxRate = nation.getTaxRate();
        if (nationTaxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return; // No nation tax
        }

        BigDecimal playerBalance = plugin.getEconomyManager().getPlayerBalance(townyPlayer.getUuid());
        BigDecimal nationTaxAmount = playerBalance.multiply(nationTaxRate.divide(BigDecimal.valueOf(100)));

        // Minimum tax amount from config
        BigDecimal minTax = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.minimum-nation-tax", 0.5));
        nationTaxAmount = nationTaxAmount.max(minTax);

        if (plugin.getEconomyManager().hasPlayerBalance(townyPlayer.getUuid(), nationTaxAmount)) {
            // Collect nation tax (through town first, then to nation)
            plugin.getEconomyManager().transferPlayerToTown(townyPlayer.getUuid(), town.getUuid(), nationTaxAmount);
            plugin.getEconomyManager().transferTownToNation(town.getUuid(), nation.getUuid(), nationTaxAmount);

            if (player.isOnline()) {
                player.sendMessage("§6Nation Tax: §f" + plugin.getEconomyManager().format(nationTaxAmount) +
                        " collected for " + nation.getName());
            }
        } else {
            // Player cannot afford nation tax
            if (player.isOnline()) {
                player.sendMessage("§cWarning: You cannot afford the nation tax of " +
                        plugin.getEconomyManager().format(nationTaxAmount) + "!");
            }
        }
    }

    private void schedulePlayerRemovalForUnpaidTax(TownyPlayer townyPlayer, Town town, BigDecimal taxAmount) {
        // Schedule removal after 7 days (configurable)
        int removalDays = plugin.getConfig().getInt("economy.tax-removal-days", 7);

        new BukkitRunnable() {
            @Override
            public void run() {
                // Check if player still owes tax
                if (plugin.getEconomyManager().hasPlayerBalance(townyPlayer.getUuid(), taxAmount)) {
                    // Player can now afford tax, don't remove
                    return;
                }

                // Remove player from town
                plugin.getTownManager().removePlayerFromTown(town.getUuid(), townyPlayer.getUuid());

                Player player = Bukkit.getPlayer(townyPlayer.getUuid());
                if (player != null) {
                    player.sendMessage("§cYou have been removed from " + town.getName() +
                            " for unpaid taxes!");
                }

                // Notify town
                Player mayor = Bukkit.getPlayer(town.getMayorUuid());
                if (mayor != null) {
                    mayor.sendMessage("§c" + townyPlayer.getName() +
                            " has been removed from the town for unpaid taxes.");
                }
            }
        }.runTaskLater(plugin, 20L * 60L * 60L * 24L * removalDays); // Convert to ticks
    }

    private void startTaxCollectionTask() {
        // Run tax collection every hour for online players
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
                    if (townyPlayer != null && townyPlayer.hasTown()) {
                        checkPendingTaxes(player, townyPlayer);
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60L * 60L, 20L * 60L * 60L); // Every hour
    }

    private void startUpkeepCollectionTask() {
        // Run upkeep collection every 2 hours
        new BukkitRunnable() {
            @Override
            public void run() {
                collectTownUpkeep();
                collectNationUpkeep();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60L * 60L * 2L, 20L * 60L * 60L * 2L); // Every 2 hours
    }

    private void collectTownUpkeep() {
        for (Town town : plugin.getTownManager().getAllTowns()) {
            UUID townId = town.getUuid();
            LocalDateTime lastCollection = lastUpkeepCollection.get(townId);
            LocalDateTime now = LocalDateTime.now();

            // Check if 24 hours have passed since last upkeep
            if (lastCollection == null || ChronoUnit.HOURS.between(lastCollection, now) >= 24) {
                processTownUpkeep(town);
                lastUpkeepCollection.put(townId, now);
            }
        }
    }

    private void processTownUpkeep(Town town) {
        BigDecimal upkeepCost = calculateTownUpkeep(town);

        if (upkeepCost.compareTo(BigDecimal.ZERO) <= 0) {
            return; // No upkeep required
        }

        if (plugin.getEconomyManager().hasTownBalance(town.getUuid(), upkeepCost)) {
            // Deduct upkeep from town bank
            plugin.getEconomyManager().withdrawTown(town.getUuid(), upkeepCost);

            // Notify mayor
            Player mayor = Bukkit.getPlayer(town.getMayorUuid());
            if (mayor != null) {
                mayor.sendMessage("§6Daily Upkeep: §f" + plugin.getEconomyManager().format(upkeepCost) +
                        " deducted from " + town.getName() + " treasury.");
            }
        } else {
            // Town cannot afford upkeep
            handleTownUpkeepFailure(town, upkeepCost);
        }
    }

    private BigDecimal calculateTownUpkeep(Town town) {
        // Base upkeep cost
        BigDecimal baseUpkeep = BigDecimal.valueOf(plugin.getConfigManager().getEconomyValue("daily-upkeep-town"));

        // Additional cost per resident
        BigDecimal perResidentCost = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.upkeep-per-resident", 1.0));
        BigDecimal residentCost = perResidentCost.multiply(BigDecimal.valueOf(town.getResidentCount()));

        // Additional cost per claimed chunk
        BigDecimal perChunkCost = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.upkeep-per-chunk", 0.5));
        BigDecimal chunkCost = perChunkCost.multiply(BigDecimal.valueOf(town.getClaimedChunkCount()));

        return baseUpkeep.add(residentCost).add(chunkCost);
    }

    private void handleTownUpkeepFailure(Town town, BigDecimal upkeepCost) {
        // Notify mayor
        Player mayor = Bukkit.getPlayer(town.getMayorUuid());
        if (mayor != null) {
            mayor.sendMessage("§cWarning: " + town.getName() + " cannot afford daily upkeep of " +
                    plugin.getEconomyManager().format(upkeepCost) + "!");
            mayor.sendMessage("§cThe town has 3 days to pay or it will be disbanded!");
        }

        // Schedule town dissolution if configured
        scheduleTownDisbandForUpkeep(town, upkeepCost);
    }

    private void scheduleTownDisbandForUpkeep(Town town, BigDecimal upkeepCost) {
        int disbandDays = plugin.getConfig().getInt("economy.upkeep-disband-days", 3);

        new BukkitRunnable() {
            @Override
            public void run() {
                // Check if town can now afford upkeep
                if (plugin.getEconomyManager().hasTownBalance(town.getUuid(), upkeepCost)) {
                    return; // Town can afford upkeep now
                }

                // Disband the town
                plugin.getTownManager().deleteTown(town.getUuid(), true);

                // Notify all residents
                for (UUID residentId : town.getResidents()) {
                    Player resident = Bukkit.getPlayer(residentId);
                    if (resident != null) {
                        resident.sendMessage("§c" + town.getName() +
                                " has been disbanded due to unpaid upkeep costs!");
                    }
                }
            }
        }.runTaskLater(plugin, 20L * 60L * 60L * 24L * disbandDays);
    }

    private void collectNationUpkeep() {
        for (Nation nation : plugin.getNationManager().getAllNations()) {
            UUID nationId = nation.getUuid();
            LocalDateTime lastCollection = lastUpkeepCollection.get(nationId);
            LocalDateTime now = LocalDateTime.now();

            // Check if 24 hours have passed since last upkeep
            if (lastCollection == null || ChronoUnit.HOURS.between(lastCollection, now) >= 24) {
                processNationUpkeep(nation);
                lastUpkeepCollection.put(nationId, now);
            }
        }
    }

    private void processNationUpkeep(Nation nation) {
        BigDecimal upkeepCost = calculateNationUpkeep(nation);

        if (upkeepCost.compareTo(BigDecimal.ZERO) <= 0) {
            return; // No upkeep required
        }

        if (plugin.getEconomyManager().hasNationBalance(nation.getUuid(), upkeepCost)) {
            // Deduct upkeep from nation bank
            plugin.getEconomyManager().withdrawNation(nation.getUuid(), upkeepCost);

            // Notify king
            Player king = Bukkit.getPlayer(nation.getKingUuid());
            if (king != null) {
                king.sendMessage("§6Daily Upkeep: §f" + plugin.getEconomyManager().format(upkeepCost) +
                        " deducted from " + nation.getName() + " treasury.");
            }
        } else {
            // Nation cannot afford upkeep
            handleNationUpkeepFailure(nation, upkeepCost);
        }
    }

    private BigDecimal calculateNationUpkeep(Nation nation) {
        // Base upkeep cost
        BigDecimal baseUpkeep = BigDecimal.valueOf(plugin.getConfigManager().getEconomyValue("daily-upkeep-nation"));

        // Additional cost per town
        BigDecimal perTownCost = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.upkeep-per-town", 5.0));
        BigDecimal townCost = perTownCost.multiply(BigDecimal.valueOf(nation.getTownCount()));

        return baseUpkeep.add(townCost);
    }

    private void handleNationUpkeepFailure(Nation nation, BigDecimal upkeepCost) {
        // Notify king
        Player king = Bukkit.getPlayer(nation.getKingUuid());
        if (king != null) {
            king.sendMessage("§cWarning: " + nation.getName() + " cannot afford daily upkeep of " +
                    plugin.getEconomyManager().format(upkeepCost) + "!");
            king.sendMessage("§cThe nation has 5 days to pay or it will be disbanded!");
        }

        // Schedule nation dissolution if configured
        scheduleNationDisbandForUpkeep(nation, upkeepCost);
    }

    private void scheduleNationDisbandForUpkeep(Nation nation, BigDecimal upkeepCost) {
        int disbandDays = plugin.getConfig().getInt("economy.nation-upkeep-disband-days", 5);

        new BukkitRunnable() {
            @Override
            public void run() {
                // Check if nation can now afford upkeep
                if (plugin.getEconomyManager().hasNationBalance(nation.getUuid(), upkeepCost)) {
                    return; // Nation can afford upkeep now
                }

                // Disband the nation
                plugin.getNationManager().deleteNation(nation.getUuid(), true);

                // Notify all towns
                for (UUID townId : nation.getTowns()) {
                    Town town = plugin.getTownManager().getTown(townId);
                    if (town != null) {
                        Player mayor = Bukkit.getPlayer(town.getMayorUuid());
                        if (mayor != null) {
                            mayor.sendMessage("§c" + nation.getName() +
                                    " has been disbanded due to unpaid upkeep costs!");
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 20L * 60L * 60L * 24L * disbandDays);
    }

    private void startDailyTaskScheduler() {
        // Run daily tasks at server midnight (or configured time)
        new BukkitRunnable() {
            @Override
            public void run() {
                // Clear old tax collection records
                cleanupOldRecords();

                // Generate daily reports for mayors/kings
                generateDailyReports();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60L * 60L * 24L, 20L * 60L * 60L * 24L); // Every 24 hours
    }

    private void cleanupOldRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minus(7, ChronoUnit.DAYS);

        lastTaxCollection.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        lastUpkeepCollection.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private void generateDailyReports() {
        // Generate financial reports for towns and nations
        for (Town town : plugin.getTownManager().getAllTowns()) {
            generateTownReport(town);
        }

        for (Nation nation : plugin.getNationManager().getAllNations()) {
            generateNationReport(nation);
        }
    }

    private void generateTownReport(Town town) {
        Player mayor = Bukkit.getPlayer(town.getMayorUuid());
        if (mayor == null) return;

        BigDecimal balance = plugin.getEconomyManager().getTownBalance(town.getUuid());
        BigDecimal dailyUpkeep = calculateTownUpkeep(town);
        int daysRemaining = balance.divide(dailyUpkeep, BigDecimal.ROUND_DOWN).intValue();

        mayor.sendMessage("§6=== Daily Town Report: " + town.getName() + " ===");
        mayor.sendMessage("§eBalance: §f" + plugin.getEconomyManager().format(balance));
        mayor.sendMessage("§eDaily Upkeep: §f" + plugin.getEconomyManager().format(dailyUpkeep));
        mayor.sendMessage("§eDays Remaining: §f" + (daysRemaining > 999 ? "999+" : daysRemaining));
        mayor.sendMessage("§eResidents: §f" + town.getResidentCount() + "/" + town.getMaxResidents());
        mayor.sendMessage("§eClaimed Chunks: §f" + town.getClaimedChunkCount());
    }

    private void generateNationReport(Nation nation) {
        Player king = Bukkit.getPlayer(nation.getKingUuid());
        if (king == null) return;

        BigDecimal balance = plugin.getEconomyManager().getNationBalance(nation.getUuid());
        BigDecimal dailyUpkeep = calculateNationUpkeep(nation);
        int daysRemaining = balance.divide(dailyUpkeep, BigDecimal.ROUND_DOWN).intValue();

        king.sendMessage("§6=== Daily Nation Report: " + nation.getName() + " ===");
        king.sendMessage("§eBalance: §f" + plugin.getEconomyManager().format(balance));
        king.sendMessage("§eDaily Upkeep: §f" + plugin.getEconomyManager().format(dailyUpkeep));
        king.sendMessage("§eDays Remaining: §f" + (daysRemaining > 999 ? "999+" : daysRemaining));
        king.sendMessage("§eTowns: §f" + nation.getTownCount() + "/" + nation.getMaxTowns());
    }
}