package org.arch.me.listeners;

import org.arch.me.EnhancedCoreH;
import org.arch.me.models.Town;
import org.arch.me.models.ClaimedChunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public class PvpListener implements Listener {

    private final EnhancedCoreH plugin;

    public PvpListener(EnhancedCoreH plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        Player attacker = null;
        Player victim = null;

        // Get the victim (must be a player)
        if (!(event.getEntity() instanceof Player)) return;
        victim = (Player) event.getEntity();

        // Get the attacker (can be player or projectile)
        Entity damager = event.getDamager();
        if (damager instanceof Player) {
            attacker = (Player) damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player) {
                attacker = (Player) shooter;
            }
        }

        // If no valid attacker found, ignore
        if (attacker == null) return;

        // Check if PvP should be allowed based on locations
        if (!isPvpAllowed(attacker, victim)) {
            event.setCancelled(true);
            attacker.sendMessage("§cPvP is disabled in this area!");
        }
    }

    private boolean isPvpAllowed(Player attacker, Player victim) {
        // Check attacker's location
        ClaimedChunk attackerChunk = plugin.getChunkManager().getClaimedChunk(attacker.getLocation());
        if (attackerChunk != null) {
            Town attackerTown = plugin.getTownManager().getTown(attackerChunk.getTownUuid());
            if (attackerTown != null && !attackerTown.getFlag("pvp")) {
                // Check if town is at war
                if (!isTownAtWar(attackerTown)) {
                    return false; // PvP disabled in peaceful town
                }
            }
        }

        // Check victim's location
        ClaimedChunk victimChunk = plugin.getChunkManager().getClaimedChunk(victim.getLocation());
        if (victimChunk != null) {
            Town victimTown = plugin.getTownManager().getTown(victimChunk.getTownUuid());
            if (victimTown != null && !victimTown.getFlag("pvp")) {
                // Check if town is at war
                if (!isTownAtWar(victimTown)) {
                    return false; // PvP disabled in peaceful town
                }
            }
        }

        return true; // PvP allowed
    }

    private boolean isTownAtWar(Town town) {
        // Check if town is currently in an active war
        try {
            // This would integrate with your war system
            // For now, we'll check if the town has any active wars
            return plugin.getWarManager().isAtWar(town.getUuid());
        } catch (Exception e) {
            // If war system is not available, assume not at war
            return false;
        }
    }
}
