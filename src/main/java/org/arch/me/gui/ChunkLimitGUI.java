package org.arch.me.gui;

import org.arch.me.EnhancedCoreH;
import org.arch.me.managers.ChunkManager;
import org.arch.me.models.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChunkLimitGUI implements Listener {

    private final EnhancedCoreH plugin;
    private final ChunkManager chunkManager;

    public ChunkLimitGUI(EnhancedCoreH plugin) {
        this.plugin = plugin;
        this.chunkManager = plugin.getChunkManager();
    }

    public void openChunkLimitGUI(Player player, UUID townUuid) {
        Town town = plugin.getTownManager().getTown(townUuid);
        if (town == null) {
            player.sendMessage("§cTown not found!");
            return;
        }

        // Check if player is mayor
        if (!town.isMayor(player.getUniqueId())) {
            player.sendMessage("§cOnly the mayor can access chunk limit settings!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, "§6Chunk Limit Management");

        // Current tier info
        ItemStack currentTierItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta currentTierMeta = currentTierItem.getItemMeta();
        currentTierMeta.setDisplayName("§aCurrent Chunk Limit");
        List<String> currentTierLore = new ArrayList<>();
        currentTierLore.add("§7Current Limit: §e" + town.getMaxChunks() + " chunks");
        currentTierLore.add("§7Used Chunks: §e" + town.getClaimedChunkCount() + " chunks");
        currentTierLore.add("§7Remaining: §e" + town.getRemainingChunkSlots() + " chunks");
        currentTierMeta.setLore(currentTierLore);
        currentTierItem.setItemMeta(currentTierMeta);
        gui.setItem(11, currentTierItem);

        // Next tier info and purchase button
        if (chunkManager.canExpandChunkLimit(townUuid)) {
            int nextTierMaxChunks = chunkManager.getNextChunkLimitTierMaxChunks(townUuid);
            BigDecimal expansionCost = chunkManager.getChunkLimitExpansionCost(townUuid);
            BigDecimal townBalance = plugin.getEconomyManager().getTownBalance(townUuid);

            ItemStack nextTierItem = new ItemStack(Material.DIAMOND_BLOCK);
            ItemMeta nextTierMeta = nextTierItem.getItemMeta();
            nextTierMeta.setDisplayName("§bNext Chunk Limit Tier");
            List<String> nextTierLore = new ArrayList<>();
            nextTierLore.add("§7Next Limit: §e" + nextTierMaxChunks + " chunks");
            nextTierLore.add("§7Cost: §6$" + expansionCost);
            nextTierLore.add("§7Town Balance: §6$" + townBalance);
            nextTierLore.add("");

            if (townBalance.compareTo(expansionCost) >= 0) {
                nextTierLore.add("§aClick to purchase!");
                nextTierItem.setType(Material.DIAMOND_BLOCK);
            } else {
                nextTierLore.add("§cInsufficient funds!");
                nextTierItem.setType(Material.REDSTONE_BLOCK);
            }

            nextTierMeta.setLore(nextTierLore);
            nextTierItem.setItemMeta(nextTierMeta);
            gui.setItem(15, nextTierItem);
        } else {
            ItemStack maxTierItem = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta maxTierMeta = maxTierItem.getItemMeta();
            maxTierMeta.setDisplayName("§6Maximum Tier Reached");
            List<String> maxTierLore = new ArrayList<>();
            maxTierLore.add("§7You have reached the maximum");
            maxTierLore.add("§7chunk limit tier!");
            maxTierMeta.setLore(maxTierLore);
            maxTierItem.setItemMeta(maxTierMeta);
            gui.setItem(15, maxTierItem);
        }

        // All tier progression display
        addTierProgressionDisplay(gui, town);

        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§cClose");
        closeItem.setItemMeta(closeMeta);
        gui.setItem(26, closeItem);

        player.openInventory(gui);
    }

    private void addTierProgressionDisplay(Inventory gui, Town town) {
        // Display all tiers with their status
        Material[] tierMaterials = {
            Material.COAL_BLOCK,     // Tier 1
            Material.IRON_BLOCK,     // Tier 2
            Material.GOLD_BLOCK,     // Tier 3
            Material.DIAMOND_BLOCK,  // Tier 4
            Material.NETHERITE_BLOCK // Tier 5
        };

        String[] tierNames = {"Tier 1", "Tier 2", "Tier 3", "Tier 4", "Tier 5"};
        int[] tierChunks = {10, 20, 30, 40, 50};
        double[] tierCosts = {0, 5000, 15000, 30000, 50000};

        for (int i = 0; i < 5; i++) {
            ItemStack tierItem = new ItemStack(tierMaterials[i]);
            ItemMeta tierMeta = tierItem.getItemMeta();

            boolean isCurrentTier = town.getMaxChunks() == tierChunks[i];
            boolean isUnlocked = town.getMaxChunks() >= tierChunks[i];

            if (isCurrentTier) {
                tierMeta.setDisplayName("§a" + tierNames[i] + " §7(Current)");
            } else if (isUnlocked) {
                tierMeta.setDisplayName("§2" + tierNames[i] + " §7(Unlocked)");
            } else {
                tierMeta.setDisplayName("§7" + tierNames[i] + " §7(Locked)");
            }

            List<String> tierLore = new ArrayList<>();
            tierLore.add("§7Chunks: §e" + tierChunks[i]);
            if (tierCosts[i] > 0) {
                tierLore.add("§7Cost: §6$" + tierCosts[i]);
            } else {
                tierLore.add("§7Cost: §aFree");
            }

            if (isCurrentTier) {
                tierLore.add("§aCurrently active!");
            } else if (isUnlocked) {
                tierLore.add("§2Already unlocked!");
            } else {
                tierLore.add("§7Purchase higher tiers to unlock");
            }

            tierMeta.setLore(tierLore);
            tierItem.setItemMeta(tierMeta);
            gui.setItem(i + 1, tierItem);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals("§6Chunk Limit Management")) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        if (event.getSlot() == 26) { // Close button
            player.closeInventory();
            return;
        }

        if (event.getSlot() == 15) { // Next tier purchase button
            // Get player's town
            Town town = plugin.getTownManager().getTownByPlayer(player.getUniqueId());
            if (town == null) {
                player.sendMessage("§cYou are not in a town!");
                return;
            }

            // Check if player is mayor
            if (!town.isMayor(player.getUniqueId())) {
                player.sendMessage("§cOnly the mayor can purchase chunk limit expansions!");
                return;
            }

            // Check if expansion is possible
            if (!chunkManager.canExpandChunkLimit(town.getUuid())) {
                player.sendMessage("§cYou have already reached the maximum chunk limit tier!");
                return;
            }

            // Check if town has enough funds
            BigDecimal cost = chunkManager.getChunkLimitExpansionCost(town.getUuid());
            if (!plugin.getEconomyManager().hasTownBalance(town.getUuid(), cost)) {
                player.sendMessage("§cYour town doesn't have enough funds! Required: §6$" + cost);
                return;
            }

            // Confirm purchase
            player.closeInventory();

            chunkManager.expandChunkLimit(town.getUuid(), player.getUniqueId()).thenAccept(success -> {
                if (success) {
                    int newMaxChunks = town.getMaxChunks();
                    player.sendMessage("§aChunk limit successfully expanded to §e" + newMaxChunks + " §achunks for §6$" + cost + "§a!");

                    // Notify all town members
                    for (UUID residentUuid : town.getResidents()) {
                        Player resident = Bukkit.getPlayer(residentUuid);
                        if (resident != null && !resident.equals(player)) {
                            resident.sendMessage("§a" + player.getName() + " §ahas expanded the town's chunk limit to §e" + newMaxChunks + " §achunks!");
                        }
                    }
                } else {
                    player.sendMessage("§cFailed to expand chunk limit! Please try again.");
                }
            });
        }
    }
}
