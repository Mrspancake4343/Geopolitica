package com.geopolitica.core.gui;

import com.geopolitica.core.model.ResidentImpl;
import com.geopolitica.core.model.TownImpl;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class MemberListMenu extends Menu {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public MemberListMenu(GuiContext ctx, Player viewer, TownImpl town) {
        super(ctx, viewer, 5, ChatColor.DARK_GRAY + town.getName() + " Members");
        fillBorder(new ItemBuilder(Material.CYAN_STAINED_GLASS_PANE).name(" ").build());

        int index = 0;
        for (ResidentImpl resident : town.getResidentImpls()) {
            if (index >= SLOTS.length) {
                setItem(40, new ItemBuilder(Material.PAPER).name("&7...and more not shown").build());
                break;
            }
            boolean isOwner = town.getOwner().getUniqueId().equals(resident.getUniqueId());
            String rankName = resident.getRankImpl() != null ? resident.getRankImpl().getName() : "None";
            setItem(SLOTS[index], new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(resident.getOfflinePlayer())
                    .name((isOwner ? "&6" : "&f") + resident.getName())
                    .lore("&7Rank: &f" + rankName, "&7Online: &f" + resident.getOfflinePlayer().isOnline())
                    .build());
            index++;
        }
    }
}
