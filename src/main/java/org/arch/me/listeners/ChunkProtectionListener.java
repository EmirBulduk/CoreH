package org.arch.me.listeners;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.ClaimedChunk;
import org.arch.me.models.Town;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Arrays;
import java.util.List;

public class ChunkProtectionListener implements Listener {

    private final EnhancedCoreH plugin;

    // List of containers that should be protected
    private final List<Material> CONTAINERS = Arrays.asList(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.SHULKER_BOX,
            Material.ENDER_CHEST, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.BREWING_STAND, Material.HOPPER, Material.DISPENSER, Material.DROPPER,
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL
    );

    // List of interactive blocks
    private final List<Material> INTERACTIVE_BLOCKS = Arrays.asList(
            Material.LEVER, Material.STONE_BUTTON, Material.OAK_BUTTON, Material.SPRUCE_BUTTON,
            Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON,
            Material.CRIMSON_BUTTON, Material.WARPED_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON,
            Material.STONE_PRESSURE_PLATE, Material.OAK_PRESSURE_PLATE, Material.SPRUCE_PRESSURE_PLATE,
            Material.BIRCH_PRESSURE_PLATE, Material.JUNGLE_PRESSURE_PLATE, Material.ACACIA_PRESSURE_PLATE,
            Material.DARK_OAK_PRESSURE_PLATE, Material.CRIMSON_PRESSURE_PLATE, Material.WARPED_PRESSURE_PLATE,
            Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Material.POLISHED_BLACKSTONE_PRESSURE_PLATE, Material.ENCHANTING_TABLE, Material.BEACON,
            Material.CRAFTING_TABLE, Material.CARTOGRAPHY_TABLE, Material.FLETCHING_TABLE,
            Material.SMITHING_TABLE, Material.STONECUTTER, Material.LOOM, Material.COMPOSTER,
            Material.CAULDRON, Material.WATER_CAULDRON, Material.LAVA_CAULDRON, Material.POWDER_SNOW_CAULDRON
    );

    // Explosive materials
    private final List<Material> EXPLOSIVES = Arrays.asList(
            Material.TNT, Material.TNT_MINECART, Material.RESPAWN_ANCHOR
    );

    public ChunkProtectionListener(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    // Block breaking protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!hasPermission(player, block.getLocation(), "destroy")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-destroy"));
        }
    }

    // Block placing protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!hasPermission(player, block.getLocation(), "build")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-build"));
        }
    }

    // Container interaction protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        Material material = block.getType();

        // Check container access
        if (CONTAINERS.contains(material) || block.getState() instanceof InventoryHolder) {
            if (!hasPermission(player, block.getLocation(), "switch")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-interact"));
                return;
            }
        }

        // Check interactive block access
        if (INTERACTIVE_BLOCKS.contains(material)) {
            if (!hasPermission(player, block.getLocation(), "switch")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-interact"));
                return;
            }
        }

        // Check explosive placement
        if (EXPLOSIVES.contains(material)) {
            if (!hasPermission(player, block.getLocation(), "build")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-build"));
            }
        }
    }

    // Item usage protection (flint and steel, etc.)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractWithItem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;

        Player player = event.getPlayer();
        Material item = event.getMaterial();

        // Check for fire-starting items
        if (item == Material.FLINT_AND_STEEL || item == Material.FIRE_CHARGE) {
            Block targetBlock = event.getClickedBlock();
            if (targetBlock != null) {
                if (!hasPermission(player, targetBlock.getLocation(), "itemuse")) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-interact"));
                }
            }
        }

        // Check for spawn eggs and other restricted items
        if (item.name().contains("SPAWN_EGG") || item == Material.BONE_MEAL ||
                item == Material.BUCKET || item == Material.WATER_BUCKET || item == Material.LAVA_BUCKET) {
            Block targetBlock = event.getClickedBlock();
            if (targetBlock != null) {
                if (!hasPermission(player, targetBlock.getLocation(), "itemuse")) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-interact"));
                }
            }
        }
    }

    // PvP protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;

        // Get the attacking player
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null) return;

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(victim.getLocation());
        if (chunk == null) return; // Allow PvP in wilderness

        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        if (town == null) return;

        // Check if PvP is disabled in this town
        if (!town.getFlag("pvp")) {
            event.setCancelled(true);
            attacker.sendMessage("§cPvP is disabled in " + town.getName() + "!");
        }
    }

    // Explosion protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();

        // Remove blocks in protected areas from explosion
        event.blockList().removeIf(block -> {
            ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(block.getLocation());
            if (chunk == null) return false; // Allow explosions in wilderness

            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            if (town == null) return false;

            // Check explosion protection
            return !town.getFlag("explosions");
        });

        // Cancel entire explosion if no blocks remain
        if (event.blockList().isEmpty()) {
            event.setCancelled(true);
        }
    }

    // Block explosion protection (TNT, etc.)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Block source = event.getBlock();

        // Remove blocks in protected areas from explosion
        event.blockList().removeIf(block -> {
            ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(block.getLocation());
            if (chunk == null) return false; // Allow explosions in wilderness

            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            if (town == null) return false;

            // Check explosion protection
            return !town.getFlag("explosions");
        });

        // Cancel entire explosion if no blocks remain
        if (event.blockList().isEmpty()) {
            event.setCancelled(true);
        }
    }

    // Fire spread protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(block.getLocation());

        if (chunk != null) {
            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            if (town != null && !town.getFlag("fire")) {
                event.setCancelled(true);
            }
        }
    }

    // Fire ignition protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(block.getLocation());

        if (chunk != null) {
            Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
            if (town != null && !town.getFlag("fire")) {
                event.setCancelled(true);
            }
        }
    }

    // Mob spawning protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Only prevent natural spawning, allow spawners and eggs
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL ||
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS) {

            ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(event.getLocation());
            if (chunk != null) {
                Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
                if (town != null && !town.getFlag("mobspawning")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // Vehicle protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleBreak(VehicleDestroyEvent event) {
        Entity attacker = event.getAttacker();
        if (!(attacker instanceof Player player)) return;

        if (!hasPermission(player, event.getVehicle().getLocation(), "destroy")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-destroy"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Entity attacker = event.getAttacker();
        if (!(attacker instanceof Player player)) return;

        if (!hasPermission(player, event.getVehicle().getLocation(), "destroy")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-destroy"));
        }
    }

    // Hanging entity protection (item frames, paintings)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        if (!(remover instanceof Player player)) return;

        if (!hasPermission(player, event.getEntity().getLocation(), "destroy")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-destroy"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();

        if (!hasPermission(player, event.getEntity().getLocation(), "build")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-build"));
        }
    }

    // Bucket usage protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!hasPermission(player, block.getLocation(), "build")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-build"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!hasPermission(player, block.getLocation(), "destroy")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-destroy"));
        }
    }

    // Crop trampling protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractWithFarmland(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.FARMLAND) return;

        Player player = event.getPlayer();
        if (!hasPermission(player, block.getLocation(), "destroy")) {
            event.setCancelled(true);
        }
    }

    // Entity interaction protection (animals, villagers, etc.)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // Allow interaction with own pets/tamed animals
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            if (tameable.getOwner() != null && tameable.getOwner().getUniqueId().equals(player.getUniqueId())) {
                return; // Allow interaction with own pets
            }
        }

        if (!hasPermission(player, entity.getLocation(), "switch")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage("protection.cannot-interact"));
        }
    }

    // Utility method to check permissions
    private boolean hasPermission(Player player, org.bukkit.Location location, String permission) {
        // Check if player has admin bypass
        if (player.hasPermission("towny.admin.bypass")) {
            return true;
        }

        ClaimedChunk chunk = plugin.getChunkManager().getClaimedChunk(location);

        // If not claimed, check wilderness permissions
        if (chunk == null) {
            return plugin.getConfig().getBoolean("chunks.wilderness-permissions." + permission, true);
        }

        Town town = plugin.getTownManager().getTown(chunk.getTownUuid());
        if (town == null) {
            return false;
        }

        // Check if player is resident of the town
        if (town.hasResident(player.getUniqueId())) {
            return true; // Residents can do anything in their town
        }

        // Check if player owns the specific plot
        if (chunk.isOwner(player.getUniqueId())) {
            return true; // Plot owners can do anything on their plot
        }

        // Check if player is mayor (mayors can do anything in their town)
        if (town.isMayor(player.getUniqueId())) {
            return true;
        }

        // Check town's public permissions for outsiders
        if (town.isPublic()) {
            return town.getFlag("outsider_" + permission);
        }

        // Check plot-specific permissions for outsiders
        return chunk.getFlag(permission);
    }
}