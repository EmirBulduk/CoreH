package org.arch.me.managers;

import org.arch.me.EnhancedCoreH;
import org.arch.me.database.DatabaseManager;
import org.arch.me.models.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class WarManager {

    private final EnhancedCoreH plugin;
    private final Map<Long, War> warCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Long>> nationWars = new ConcurrentHashMap<>();
    private final Set<UUID> playersInCapitalChunks = ConcurrentHashMap.newKeySet();

    // Scheduled tasks
    private BukkitTask warReminderTask;
    private BukkitTask capitulationTask;
    private BukkitTask glowEffectTask;

    public WarManager(EnhancedCoreH plugin) {
        this.plugin = plugin;
        loadAllWars();
        startWarReminderTask();
        startCapitulationCheckTask();
        startGlowEffectTask();
    }

    public void shutdown() {
        if (warReminderTask != null) {
            warReminderTask.cancel();
        }
        if (capitulationTask != null) {
            capitulationTask.cancel();
        }
        if (glowEffectTask != null) {
            glowEffectTask.cancel();
        }
        saveAllWars();
    }

    private void loadAllWars() {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "SELECT * FROM %swars WHERE status NOT IN ('ENDED_VICTORY', 'ENDED_SURRENDER', 'ENDED_PEACE', 'CANCELLED')".formatted(db.getTablePrefix());

            List<War> wars = db.queryList(sql, rs -> {
                War war = new War(
                        UUID.fromString(rs.getString("declaring_nation_uuid")),
                        UUID.fromString(rs.getString("defending_nation_uuid")),
                        rs.getString("war_name")
                );

                war.setId(rs.getLong("id"));
                war.setStatus(War.WarStatus.valueOf(rs.getString("status")));
                war.setDeclaredDate(rs.getTimestamp("declared_date"));
                war.setStartDate(rs.getTimestamp("start_date"));
                war.setEndDate(rs.getTimestamp("end_date"));

                // Load allies
                loadWarAllies(war);

                return war;
            });

            for (War war : wars) {
                warCache.put(war.getId(), war);
                addWarToNationMapping(war);
            }

            plugin.getLogger().info("Loaded " + wars.size() + " active wars from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load wars: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadWarAllies(War war) throws SQLException {
        DatabaseManager db = plugin.getDatabaseManager();
        String sql = "SELECT * FROM %swar_participants WHERE war_id = ?".formatted(db.getTablePrefix());

        db.queryList(sql, rs -> {
            UUID nationUuid = UUID.fromString(rs.getString("nation_uuid"));
            String side = rs.getString("side");

            if ("DECLARING".equals(side)) {
                war.addDeclaringAlly(nationUuid);
            } else if ("DEFENDING".equals(side)) {
                war.addDefendingAlly(nationUuid);
            }

            return null;
        }, war.getId());
    }

    private void addWarToNationMapping(War war) {
        for (UUID nationUuid : war.getAllParticipants()) {
            nationWars.computeIfAbsent(nationUuid, k -> ConcurrentHashMap.newKeySet()).add(war.getId());
        }
    }

    private void removeWarFromNationMapping(War war) {
        for (UUID nationUuid : war.getAllParticipants()) {
            Set<Long> wars = nationWars.get(nationUuid);
            if (wars != null) {
                wars.remove(war.getId());
                if (wars.isEmpty()) {
                    nationWars.remove(nationUuid);
                }
            }
        }
    }

    // War declaration
    public CompletableFuture<War> declareWar(UUID declaringNationUuid, UUID defendingNationUuid, String warName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validation
                Nation declaringNation = plugin.getNationManager().getNation(declaringNationUuid);
                Nation defendingNation = plugin.getNationManager().getNation(defendingNationUuid);

                if (declaringNation == null || defendingNation == null) {
                    return null;
                }

                // Check if nations are already at war
                if (areNationsAtWar(declaringNationUuid, defendingNationUuid)) {
                    return null;
                }

                // Check if declaring nation has enough funds for war
                BigDecimal warCost = BigDecimal.valueOf(plugin.getConfig().getDouble("war.declaration-cost", 10000.0));
                if (!plugin.getEconomyManager().hasNationBalance(declaringNationUuid, warCost)) {
                    return null;
                }

                // Withdraw war cost
                plugin.getEconomyManager().withdrawNation(declaringNationUuid, warCost);

                // Create war
                War war = new War(declaringNationUuid, defendingNationUuid, warName);

                // Save to database
                saveWar(war);

                // Add to cache
                warCache.put(war.getId(), war);
                addWarToNationMapping(war);

                // Start war after preparation time
                long preparationTime = plugin.getConfig().getLong("war.preparation-time-minutes", 60) * 60 * 1000;

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        startWar(war.getId());
                    }
                }.runTaskLater(plugin, preparationTime / 50); // Convert to ticks

                // Broadcast war declaration
                broadcastWarDeclaration(war);

                plugin.getLogger().info("War declared: " + warName + " between " +
                        declaringNation.getName() + " and " + defendingNation.getName());

                return war;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to declare war: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    // Start war after preparation time
    public void startWar(long warId) {
        War war = warCache.get(warId);
        if (war != null && war.getStatus() == War.WarStatus.DECLARED) {
            war.setStatus(War.WarStatus.ACTIVE);
            war.setStartDate(new Timestamp(System.currentTimeMillis()));
            saveWar(war);

            broadcastWarStart(war);
            plugin.getLogger().info("War started: " + war.getWarName());
        }
    }

    // Join war as ally
    public CompletableFuture<Boolean> joinWarAsAlly(UUID joiningNationUuid, UUID alliedNationUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Find war involving the allied nation
                War war = getActiveWarForNation(alliedNationUuid);
                if (war == null) {
                    return false;
                }

                // Check if joining nation is already in a war
                if (isNationAtWar(joiningNationUuid)) {
                    return false;
                }

                // Add to appropriate side
                if (war.isDeclaringSide(alliedNationUuid)) {
                    war.addDeclaringAlly(joiningNationUuid);
                } else {
                    war.addDefendingAlly(joiningNationUuid);
                }

                // Update mappings
                nationWars.computeIfAbsent(joiningNationUuid, k -> ConcurrentHashMap.newKeySet()).add(war.getId());

                // Save war participants
                saveWarParticipant(war.getId(), joiningNationUuid,
                        war.isDeclaringSide(joiningNationUuid) ? "DECLARING" : "DEFENDING");

                // Broadcast
                Nation joiningNation = plugin.getNationManager().getNation(joiningNationUuid);
                broadcastAllyJoined(war, joiningNation);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to join war as ally: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    // End war
    public CompletableFuture<Boolean> endWar(long warId, War.WarStatus endStatus, UUID winnerNationUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                War war = warCache.get(warId);
                if (war == null || war.isEnded()) {
                    return false;
                }

                war.setStatus(endStatus);
                war.setEndDate(new Timestamp(System.currentTimeMillis()));

                // Handle capitulation victory
                if (endStatus == War.WarStatus.ENDED_VICTORY && winnerNationUuid != null) {
                    handleWarVictory(war, winnerNationUuid);
                }

                // Save war
                saveWar(war);

                // Remove from active mappings
                removeWarFromNationMapping(war);
                warCache.remove(warId);

                // Broadcast war end
                broadcastWarEnd(war, endStatus);

                plugin.getLogger().info("War ended: " + war.getWarName() + " with status: " + endStatus);

                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to end war: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    private void handleWarVictory(War war, UUID winnerNationUuid) {
        try {
            // Determine losing side
            Set<UUID> losingNations;
            if (war.isDeclaringSide(winnerNationUuid)) {
                losingNations = war.getAllDefendingSide();
            } else {
                losingNations = war.getAllDeclaringSide();
            }

            // Find town with most balance on winning side
            Town richestTown = findRichestTownOnSide(war, winnerNationUuid);

            if (richestTown != null) {
                // Transfer all chunks from losing nations to richest town
                for (UUID losingNationUuid : losingNations) {
                    transferNationChunks(losingNationUuid, richestTown.getUuid());
                }

                plugin.getLogger().info("War victory: All losing chunks transferred to " + richestTown.getName());
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to handle war victory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateWarParticipants(long warId) {
        try {
            War war = getWar(warId);
            if (war == null) return;

            // Saldıran ulusun güncel şehirlerini al
            Nation attackerNation = plugin.getNationManager().getNation(war.getDeclaringNationUuid());
            if (attackerNation != null) {
                updateWarTowns(warId, attackerNation.getUuid(), "ATTACKER");
            }

            // Savunan ulusun güncel şehirlerini al
            Nation defenderNation = plugin.getNationManager().getNation(war.getDefendingNationUuid());
            if (defenderNation != null) {
                updateWarTowns(warId, defenderNation.getUuid(), "DEFENDER");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Savaş katılımcıları güncellenemedi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateWarTowns(long warId, UUID nationUuid, String side) throws SQLException {
        DatabaseManager db = plugin.getDatabaseManager();

        // Önce eski şehir kayıtlarını temizle
        String deleteSql = "DELETE FROM %swar_towns WHERE war_id = ? AND side = ?".formatted(db.getTablePrefix());
        db.executeUpdate(deleteSql, warId, side);

        // Sonra güncel şehirleri ekle
        Nation nation = plugin.getNationManager().getNation(nationUuid);
        if (nation != null) {
            List<UUID> townUuids = (List<UUID>) nation.getTowns();
            if (!townUuids.isEmpty()) {
                for (UUID townUuid : townUuids) {
                    String insertSql = "INSERT INTO %swar_towns (war_id, town_uuid, side) VALUES (?, ?, ?)".formatted(db.getTablePrefix());
                    db.executeUpdate(insertSql, warId, townUuid.toString(), side);
                }
            }
        }
    }

    public void handleTownNationChange(UUID townUuid, UUID oldNationUuid, UUID newNationUuid) {
        try {
            // Eski ulustan savaş ilişkilerini kaldır
            if (oldNationUuid != null) {
                DatabaseManager db = plugin.getDatabaseManager();
                String sql = "DELETE FROM %swar_towns WHERE town_uuid = ? AND war_id IN (SELECT id FROM %swars WHERE (declaring_nation_uuid = ? OR defending_nation_uuid = ?) AND status NOT IN ('ENDED_VICTORY', 'ENDED_SURRENDER', 'ENDED_PEACE', 'CANCELLED'))".formatted(db.getTablePrefix(), db.getTablePrefix());
                db.executeUpdate(sql, townUuid.toString(), oldNationUuid.toString(), oldNationUuid.toString());
            }

            // Yeni ulus savaştaysa, şehri savaşa ekle
            if (newNationUuid != null && isNationAtWar(newNationUuid)) {
                DatabaseManager db = plugin.getDatabaseManager();
                String warSql = "SELECT id, CASE WHEN declaring_nation_uuid = ? THEN 'DECLARING' ELSE 'DEFENDING' END AS side FROM %swars WHERE (declaring_nation_uuid = ? OR defending_nation_uuid = ?) AND status NOT IN ('ENDED_VICTORY', 'ENDED_SURRENDER', 'ENDED_PEACE', 'CANCELLED')".formatted(db.getTablePrefix());

                List<Object[]> wars = db.queryList(warSql, rs -> new Object[]{rs.getLong("id"), rs.getString("side")}, newNationUuid.toString(), newNationUuid.toString(), newNationUuid.toString());
                for (Object[] warData : wars) {
                    long warId = (long) warData[0];
                    String side = (String) warData[1];

                    String insertSql = "INSERT INTO %swar_towns (war_id, town_uuid, side) VALUES (?, ?, ?)".formatted(db.getTablePrefix());
                    db.executeUpdate(insertSql, warId, townUuid.toString(), side);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Şehir-ulus değişikliği savaş sisteminde güncellenemedi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Town findRichestTownOnSide(War war, UUID sideNationUuid) {
        Set<UUID> sideNations = war.isDeclaringSide(sideNationUuid) ?
                war.getAllDeclaringSide() : war.getAllDefendingSide();

        Town richestTown = null;
        BigDecimal highestBalance = BigDecimal.ZERO;

        for (UUID nationUuid : sideNations) {
            Nation nation = plugin.getNationManager().getNation(nationUuid);
            if (nation != null) {
                for (UUID townUuid : nation.getTowns()) {
                    Town town = plugin.getTownManager().getTown(townUuid);
                    if (town != null && town.getBalance().compareTo(highestBalance) > 0) {
                        highestBalance = town.getBalance();
                        richestTown = town;
                    }
                }
            }
        }

        return richestTown;
    }

    private void transferNationChunks(UUID losingNationUuid, UUID winningTownUuid) {
        try {
            Nation losingNation = plugin.getNationManager().getNation(losingNationUuid);
            if (losingNation == null) return;

            for (UUID townUuid : losingNation.getTowns()) {
                Town town = plugin.getTownManager().getTown(townUuid);
                if (town != null) {
                    // Get all claimed chunks for this town
                    List<ClaimedChunk> chunks = plugin.getChunkManager().getChunksByTown(townUuid);

                    for (ClaimedChunk chunk : chunks) {
                        // Transfer chunk to winning town
                        chunk.setTownUuid(winningTownUuid);
                        chunk.setOwnerUuid(null); // Reset owner
                        plugin.getChunkManager().saveClaimedChunk(chunk);
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to transfer nation chunks: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Capitulation system
    public void handlePlayerEnterCapitalChunk(Player player, ClaimedChunk chunk) {
        if (!isCapitalChunk(chunk)) return;

        UUID playerNationUuid = getPlayerNationUuid(player);
        if (playerNationUuid == null) return;

        War war = getActiveWarForNation(playerNationUuid);
        if (war == null) return;

        UUID chunkNationUuid = getChunkNationUuid(chunk);
        if (chunkNationUuid == null) return;

        // Check if player is enemy of chunk nation
        if (war.areEnemies(playerNationUuid, chunkNationUuid)) {
            war.addPlayerToCapitalChunk(player.getUniqueId());
            playersInCapitalChunks.add(player.getUniqueId());

            // Enhanced capitulation mechanics
            if (!war.isCapitulationInProgress()) {
                war.startCapitulation();
                saveWar(war);

                // Broadcast dramatic start message
                broadcastCapitulationStart(war, chunk);

                // Give attacker bonus effects in capital chunk
                applyCapitalChunkEffects(player, true);
            } else {
                player.sendMessage("§c⚔ CAPITULATION IN PROGRESS! Time remaining: " + war.getFormattedTimeRemaining());
                applyCapitalChunkEffects(player, true);
            }

            saveWar(war);
        }
    }

    public void handlePlayerLeaveCapitalChunk(Player player, ClaimedChunk chunk) {
        if (!isCapitalChunk(chunk)) return;

        UUID playerNationUuid = getPlayerNationUuid(player);
        if (playerNationUuid == null) return;

        War war = getActiveWarForNation(playerNationUuid);
        if (war == null) return;

        war.removePlayerFromCapitalChunk(player.getUniqueId());
        playersInCapitalChunks.remove(player.getUniqueId());

        // Remove effects
        applyCapitalChunkEffects(player, false);

        if (war.getPlayersInCapitalChunk().isEmpty()) {
            war.stopCapitulation();
            player.sendMessage("§a⚔ Capitulation stopped - no enemy players in capital!");
            broadcastCapitulationStop(war);
        }

        saveWar(war);
    }

    /**
     * Enhanced capital chunk detection - checks if chunk is THE capital chunk of a nation
     */
    private boolean isCapitalChunk(ClaimedChunk chunk) {
        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        if (town == null || !town.hasNation()) return false;

        Nation nation = plugin.getNationManager().getNation(town.getNationUuid());
        if (nation == null) return false;

        // Check if this specific chunk is the nation's capital chunk
        return nation.getCapitalChunkUuid() != null &&
               nation.getCapitalChunkUuid().equals(chunk.getUuid());
    }

    /**
     * Apply special effects to players in capital chunks
     */
    private void applyCapitalChunkEffects(Player player, boolean apply) {
        if (apply) {
            // Give attacking players bonus effects in capital chunk
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, false, false));
        } else {
            // Remove effects when leaving
            player.removePotionEffect(PotionEffectType.GLOWING);
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.STRENGTH);
        }
    }

    /**
     * Enhanced capitulation check with nation collapse mechanics
     */
    private void startCapitulationCheckTask() {
        capitulationTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (War war : warCache.values()) {
                    if (war.isCapitulationInProgress()) {
                        if (war.isCapitulationComplete()) {
                            // Determine winner and collapse the defending nation
                            UUID winnerNationUuid = determineCapitulationWinner(war);
                            if (winnerNationUuid != null) {
                                collapseNation(war, winnerNationUuid);
                                endWar(war.getId(), War.WarStatus.ENDED_VICTORY, winnerNationUuid);
                            }
                        } else {
                            // Broadcast time remaining with enhanced effects
                            broadcastCapitulationProgress(war);

                            // Apply continuous effects to players in capital
                            for (UUID playerUuid : war.getPlayersInCapitalChunk()) {
                                Player player = Bukkit.getPlayer(playerUuid);
                                if (player != null && player.isOnline()) {
                                    applyCapitalChunkEffects(player, true);
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20); // Every second
    }

    /**
     * Nation collapse mechanics - transfer all territory to winners
     */
    private void collapseNation(War war, UUID winnerNationUuid) {
        try {
            UUID losingNationUuid = war.isDeclaringSide(winnerNationUuid) ?
                war.getDefendingNationUuid() : war.getDeclaringNationUuid();

            Nation losingNation = plugin.getNationManager().getNation(losingNationUuid);
            Nation winningNation = plugin.getNationManager().getNation(winnerNationUuid);

            if (losingNation == null || winningNation == null) return;

            plugin.getLogger().info("NATION COLLAPSE: " + losingNation.getName() + " has been conquered by " + winningNation.getName());

            // Find the richest town on the winning side to receive territory
            Town richestWinningTown = findRichestTownInNation(winningNation);
            if (richestWinningTown == null) {
                plugin.getLogger().warning("No suitable town found to receive collapsed territory");
                return;
            }

            // Transfer ALL chunks from losing nation's towns to the richest winning town
            int transferredChunks = 0;
            for (UUID townUuid : new HashSet<>(losingNation.getTowns())) {
                Town losingTown = plugin.getTownManager().getTown(townUuid);
                if (losingTown != null) {
                    List<ClaimedChunk> chunks = new ArrayList<>(losingTown.getClaimedChunks());
                    for (ClaimedChunk chunk : chunks) {
                        // Transfer chunk ownership
                        chunk.setTownUuid(richestWinningTown.getUuid());
                        chunk.setOwnerUuid(null); // Reset plot ownership
                        chunk.setPlotPrice(java.math.BigDecimal.ZERO); // Not for sale
                        plugin.getChunkManager().saveClaimedChunk(chunk);

                        // Update town chunk collections
                        losingTown.removeClaimedChunk(chunk);
                        richestWinningTown.addClaimedChunk(chunk);
                        transferredChunks++;
                    }

                    // Delete the losing town
                    plugin.getTownManager().deleteTown(townUuid, true);
                }
            }

            // Save the winning town with new chunks
            plugin.getTownManager().saveTown(richestWinningTown);

            // Delete the losing nation completely
            plugin.getNationManager().deleteNation(losingNationUuid, true);

            // Broadcast the dramatic collapse
            String collapseMessage = String.format(
                "§4§l⚔ NATION COLLAPSED! ⚔§r\n" +
                "§c%s has been completely conquered!\n" +
                "§a%s claims all %d chunks!\n" +
                "§eAll territory now belongs to %s!",
                losingNation.getName(),
                winningNation.getName(),
                transferredChunks,
                richestWinningTown.getName()
            );

            Bukkit.broadcastMessage(collapseMessage);

            plugin.getLogger().info("Nation collapse complete: Transferred " + transferredChunks +
                " chunks from " + losingNation.getName() + " to " + richestWinningTown.getName());

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to collapse nation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Find the richest town in a nation to receive conquered territory
     */
    private Town findRichestTownInNation(Nation nation) {
        Town richestTown = null;
        java.math.BigDecimal highestBalance = java.math.BigDecimal.ZERO;

        for (UUID townUuid : nation.getTowns()) {
            Town town = plugin.getTownManager().getTown(townUuid);
            if (town != null && town.getBalance().compareTo(highestBalance) > 0) {
                highestBalance = town.getBalance();
                richestTown = town;
            }
        }

        // If no town has money, just pick the capital
        if (richestTown == null && nation.getCapitalTownUuid() != null) {
            richestTown = plugin.getTownManager().getTown(nation.getCapitalTownUuid());
        }

        return richestTown;
    }

    // Scheduled tasks
    private void startWarReminderTask() {
        warReminderTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (War war : warCache.values()) {
                    if (war.isActive()) {
                        broadcastWarReminder(war);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 15 * 60 * 20); // Every 15 minutes
    }

    /*private void startCapitulationCheckTask() {
        capitulationTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (War war : warCache.values()) {
                    if (war.isCapitulationInProgress()) {
                        if (war.isCapitulationComplete()) {
                            // Determine winner and collapse the defending nation
                            UUID winnerNationUuid = determineCapitulationWinner(war);
                            if (winnerNationUuid != null) {
                                collapseNation(war, winnerNationUuid);
                                endWar(war.getId(), War.WarStatus.ENDED_VICTORY, winnerNationUuid);
                            }
                        } else {
                            // Broadcast time remaining with enhanced effects
                            broadcastCapitulationProgress(war);

                            // Apply continuous effects to players in capital
                            for (UUID playerUuid : war.getPlayersInCapitalChunk()) {
                                Player player = Bukkit.getPlayer(playerUuid);
                                if (player != null && player.isOnline()) {
                                    applyCapitalChunkEffects(player, true);
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20); // Every second
    }*/

    private void startGlowEffectTask() {
        glowEffectTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID playerUuid : playersInCapitalChunks) {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null && player.isOnline()) {
                        // Apply glowing effect
                        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20); // Every second
    }

    private UUID determineCapitulationWinner(War war) {
        // The side with players in the capital chunk wins
        for (UUID playerUuid : war.getPlayersInCapitalChunk()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                UUID nationUuid = getPlayerNationUuid(player);
                if (nationUuid != null) {
                    return nationUuid;
                }
            }
        }
        return null;
    }

    // Broadcasting methods
    private void broadcastWarDeclaration(War war) {
        Nation declaring = plugin.getNationManager().getNation(war.getDeclaringNationUuid());
        Nation defending = plugin.getNationManager().getNation(war.getDefendingNationUuid());

        String message = String.format("§c⚔ WAR DECLARED! %s has declared war on %s - '%s'",
                declaring != null ? declaring.getName() : "Unknown",
                defending != null ? defending.getName() : "Unknown",
                war.getWarName());

        Bukkit.getServer().broadcast(net.kyori.adventure.text.Component.text(message));
    }

    private void broadcastWarStart(War war) {
        String message = String.format("§c⚔ WAR ACTIVE! The war '%s' has begun! Chunk protections are now disabled between warring factions!",
                war.getWarName());

        Bukkit.getServer().broadcast(net.kyori.adventure.text.Component.text(message));
    }

    private void broadcastWarEnd(War war, War.WarStatus endStatus) {
        String statusMessage = switch (endStatus) {
            case ENDED_VICTORY -> "ended in victory!";
            case ENDED_SURRENDER -> "ended in surrender!";
            case ENDED_PEACE -> "ended in peace!";
            case CANCELLED -> "was cancelled!";
            default -> "has ended!";
        };

        String message = String.format("§a⚔ WAR ENDED! The war '%s' %s", war.getWarName(), statusMessage);
        Bukkit.getServer().broadcast(net.kyori.adventure.text.Component.text(message));
    }

    private void broadcastWarReminder(War war) {
        Nation declaring = plugin.getNationManager().getNation(war.getDeclaringNationUuid());
        Nation defending = plugin.getNationManager().getNation(war.getDefendingNationUuid());

        String message = String.format("§e⚔ WAR REMINDER: %s vs %s - '%s' is still active!",
                declaring != null ? declaring.getName() : "Unknown",
                defending != null ? defending.getName() : "Unknown",
                war.getWarName());

        Bukkit.getServer().broadcast(net.kyori.adventure.text.Component.text(message));
    }

    private void broadcastAllyJoined(War war, Nation ally) {
        String message = String.format("§e⚔ %s has joined the war '%s' as an ally!",
                ally != null ? ally.getName() : "Unknown", war.getWarName());

        Bukkit.getServer().broadcast(net.kyori.adventure.text.Component.text(message));
    }

    private void broadcastCapitulationStart(War war, ClaimedChunk chunk) {
        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        String message = String.format("§c⚔ CAPITULATION STARTED! Enemy forces have entered %s's capital! 2 minutes to victory!",
                town != null ? town.getName() : "Unknown");

        Bukkit.getServer().broadcast(net.kyori.adventure.text.Component.text(message));
    }

    private void broadcastCapitulationStop(War war) {
        String message = "§a⚔ CAPITULATION STOPPED! Enemy forces have left the capital!";
        Bukkit.getServer().broadcast(net.kyori.adventure.text.Component.text(message));
    }

    private void broadcastCapitulationProgress(War war) {
        String message = String.format("§c⚔ CAPITULATION: %s remaining!", war.getFormattedTimeRemaining());

        // Send to war participants only
        for (UUID nationUuid : war.getAllParticipants()) {
            Nation nation = plugin.getNationManager().getNation(nationUuid);
            if (nation != null) {
                for (UUID townUuid : nation.getTowns()) {
                    Town town = plugin.getTownManager().getTown(townUuid);
                    if (town != null) {
                        for (UUID residentUuid : town.getResidents()) {
                            Player player = Bukkit.getPlayer(residentUuid);
                            if (player != null && player.isOnline()) {
                                player.sendMessage(message);
                            }
                        }
                    }
                }
            }
        }
    }

    // Database operations
    private void saveWar(War war) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();

            String sql;
            if (war.getId() == 0) {
                // Insert new war
                if (db.isSQLServer()) {
                    sql = """
                        INSERT INTO %swars (declaring_nation_uuid, defending_nation_uuid, war_name, status, declared_date, start_date, end_date)
                        OUTPUT INSERTED.id
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """.formatted(db.getTablePrefix());
                } else {
                    sql = """
                        INSERT INTO %swars (declaring_nation_uuid, defending_nation_uuid, war_name, status, declared_date, start_date, end_date)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """.formatted(db.getTablePrefix());
                }

                if (db.isSQLServer()) {
                    Long id = db.queryObject(sql, rs -> rs.getLong(1),
                            war.getDeclaringNationUuid().toString(),
                            war.getDefendingNationUuid().toString(),
                            war.getWarName(),
                            war.getStatus().name(),
                            war.getDeclaredDate(),
                            war.getStartDate(),
                            war.getEndDate()
                    );
                    if (id != null) {
                        war.setId(id);
                    }
                } else {
                    db.executeUpdate(sql,
                            war.getDeclaringNationUuid().toString(),
                            war.getDefendingNationUuid().toString(),
                            war.getWarName(),
                            war.getStatus().name(),
                            war.getDeclaredDate(),
                            war.getStartDate(),
                            war.getEndDate()
                    );
                    // For other DBs like MySQL, get last inserted ID
                    if (war.getId() == 0) {
                        Long id = db.queryObject("SELECT LAST_INSERT_ID()", rs -> rs.getLong(1));
                        if (id != null) {
                            war.setId(id);
                        }
                    }
                }
            } else {
                // Update existing war
                sql = """
                    UPDATE %swars SET status = ?, start_date = ?, end_date = ?
                    WHERE id = ?
                    """.formatted(db.getTablePrefix());

                db.executeUpdate(sql,
                        war.getStatus().name(),
                        war.getStartDate(),
                        war.getEndDate(),
                        war.getId()
                );
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save war: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveWarParticipant(long warId, UUID nationUuid, String side) {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            String sql = "INSERT INTO %swar_participants (war_id, nation_uuid, side) VALUES (?, ?, ?)".formatted(db.getTablePrefix());

            db.executeUpdate(sql, warId, nationUuid.toString(), side);

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save war participant: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveAllWars() {
        for (War war : warCache.values()) {
            saveWar(war);
        }
    }

    public boolean isTownInWarringNation(UUID townUuid) {
        Town town = plugin.getTownManager().getTown(townUuid);
        if (town == null || !town.hasNation()) {
            return false;
        }
        return isNationAtWar(town.getNationUuid());
    }


    // Query methods
    public boolean areNationsAtWar(UUID nation1, UUID nation2) {
        for (War war : warCache.values()) {
            if (war.isActive() && war.areEnemies(nation1, nation2)) {
                return true;
            }
        }
        return false;
    }

    public boolean isNationAtWar(UUID nationUuid) {
        Set<Long> wars = nationWars.get(nationUuid);
        return wars != null && !wars.isEmpty();
    }

    public War getActiveWarForNation(UUID nationUuid) {
        Set<Long> wars = nationWars.get(nationUuid);
        if (wars != null) {
            for (Long warId : wars) {
                War war = warCache.get(warId);
                if (war != null && war.isActive()) {
                    return war;
                }
            }
        }
        return null;
    }

    public List<War> getAllActiveWars() {
        return warCache.values().stream()
                .filter(War::isActive)
                .toList();
    }

    public War getWar(long warId) {
        return warCache.get(warId);
    }

    public boolean areChunksProtected(ClaimedChunk chunk1, ClaimedChunk chunk2) {
        UUID nation1 = getChunkNationUuid(chunk1);
        UUID nation2 = getChunkNationUuid(chunk2);

        if (nation1 == null || nation2 == null) {
            return true; // Default to protected if no nation
        }

        return !areNationsAtWar(nation1, nation2);
    }

    /**
     * Get the nation UUID for a player
     */
    private UUID getPlayerNationUuid(Player player) {
        TownyPlayer townyPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (townyPlayer != null && townyPlayer.hasNation()) {
            return townyPlayer.getNationUuid();
        }
        return null;
    }

    /**
     * Get the nation UUID that owns a chunk
     */
    private UUID getChunkNationUuid(ClaimedChunk chunk) {
        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        if (town != null && town.hasNation()) {
            return town.getNationUuid();
        }
        return null;
    }

    public boolean isChunkProtectedFromPlayer(ClaimedChunk chunk, Player player) {
        UUID chunkNationUuid = getChunkNationUuid(chunk);
        UUID playerNationUuid = getPlayerNationUuid(player);

        if (chunkNationUuid == null || playerNationUuid == null) {
            return true; // Default to protected
        }

        return !areNationsAtWar(chunkNationUuid, playerNationUuid);
    }
}

