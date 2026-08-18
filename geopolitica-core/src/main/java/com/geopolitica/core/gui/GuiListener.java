package com.geopolitica.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Single listener dispatching clicks in any currently-open {@link Menu} to that menu's handlers. */
public class GuiListener implements Listener {

    private final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();

    void track(Player player, Menu menu) {
        openMenus.put(player.getUniqueId(), menu);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Menu menu = openMenus.get(player.getUniqueId());
        if (menu == null || event.getInventory() != menu.getInventory()) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != menu.getInventory()) {
            return;
        }
        menu.handleClick(player, event.getSlot(), event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Menu menu = openMenus.get(player.getUniqueId());
        if (menu != null && event.getInventory() == menu.getInventory()) {
            openMenus.remove(player.getUniqueId());
        }
    }
}
