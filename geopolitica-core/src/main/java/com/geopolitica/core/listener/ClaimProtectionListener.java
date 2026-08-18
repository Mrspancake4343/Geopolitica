package com.geopolitica.core.listener;

import com.geopolitica.api.claim.ClaimPermission;
import com.geopolitica.core.service.ClaimServiceImpl;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

public class ClaimProtectionListener implements Listener {

    private final ClaimServiceImpl claimService;

    public ClaimProtectionListener(ClaimServiceImpl claimService) {
        this.claimService = claimService;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!claimService.hasPermission(event.getPlayer(), event.getBlock().getLocation(), ClaimPermission.DESTROY)) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!claimService.hasPermission(event.getPlayer(), event.getBlock().getLocation(), ClaimPermission.BUILD)) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (!claimService.hasPermission(event.getPlayer(), block.getLocation(), ClaimPermission.INTERACT)) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof org.bukkit.block.BlockState blockState)) {
            return;
        }
        if (!claimService.hasPermission(player, blockState.getLocation(), ClaimPermission.CONTAINER)) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        if (!claimService.hasPermission(attacker, victim.getLocation(), ClaimPermission.PVP)) {
            event.setCancelled(true);
            deny(attacker);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void deny(Player player) {
        player.sendMessage(ChatColor.RED + "You do not have permission to do that here.");
    }
}
