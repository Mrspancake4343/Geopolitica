package com.geopolitica.core.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Small fluent wrapper around {@link ItemStack}/{@link ItemMeta} for building GUI buttons concisely. */
public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder name(String name) {
        meta.setDisplayName(color(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(color(line));
        }
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(color(line));
        }
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder skullOwner(OfflinePlayer owner) {
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(owner);
        }
        return this;
    }

    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
