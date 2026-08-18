package com.geopolitica.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for Geopolitica's chest-style GUI menus: a bordered inventory
 * of item-icon buttons with click handlers, in the same visual family as
 * common shop/menu plugins (bordered chest, icon + lore per option).
 */
public abstract class Menu {

    protected final GuiContext ctx;
    private final Inventory inventory;
    private final Map<Integer, ClickHandler> handlers = new HashMap<>();

    protected Menu(GuiContext ctx, Player viewer, int rows, String title) {
        this.ctx = ctx;
        this.inventory = Bukkit.createInventory(viewer, rows * 9, title);
    }

    public interface ClickHandler {
        void onClick(Player player, InventoryClickEvent event);
    }

    protected void setItem(int slot, ItemStack item, ClickHandler handler) {
        inventory.setItem(slot, item);
        if (handler != null) {
            handlers.put(slot, handler);
        } else {
            handlers.remove(slot);
        }
    }

    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    protected void clear() {
        inventory.clear();
        handlers.clear();
    }

    /** Fills every still-empty border slot (top/bottom rows, side columns) with the given filler item. */
    protected void fillBorder(ItemStack filler) {
        int size = inventory.getSize();
        int lastRow = (size / 9) - 1;
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            boolean border = row == 0 || row == lastRow || col == 0 || col == 8;
            if (border && inventory.getItem(i) == null) {
                setItem(i, filler);
            }
        }
    }

    public void open(Player player) {
        ctx.guiListener().track(player, this);
        player.openInventory(inventory);
    }

    Inventory getInventory() {
        return inventory;
    }

    void handleClick(Player player, int slot, InventoryClickEvent event) {
        ClickHandler handler = handlers.get(slot);
        if (handler != null) {
            handler.onClick(player, event);
        }
    }
}
