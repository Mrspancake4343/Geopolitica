package com.geopolitica.core.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public class ConfirmMenu extends Menu {

    public ConfirmMenu(GuiContext ctx, Player viewer, String title, String question, Runnable onConfirm) {
        super(ctx, viewer, 3, title);
        fillBorder(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        setItem(13, new ItemBuilder(Material.PAPER).name("&e" + question).build());
        setItem(11, new ItemBuilder(Material.LIME_WOOL).name("&aConfirm").build(), (player, event) -> {
            player.closeInventory();
            onConfirm.run();
        });
        setItem(15, new ItemBuilder(Material.RED_WOOL).name("&cCancel").build(), (player, event) -> player.closeInventory());
    }
}
