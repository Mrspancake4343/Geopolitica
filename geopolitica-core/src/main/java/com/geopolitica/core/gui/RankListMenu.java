package com.geopolitica.core.gui;

import com.geopolitica.core.model.TownImpl;
import com.geopolitica.core.model.TownRankImpl;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class RankListMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public RankListMenu(GuiContext ctx, Player viewer, TownImpl town) {
        super(ctx, viewer, 4, ChatColor.DARK_GRAY + town.getName() + " Ranks");
        fillBorder(new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());

        int index = 0;
        for (TownRankImpl rank : town.getRankImpls()) {
            if (index >= SLOTS.length) {
                break;
            }
            long members = town.getResidentImpls().stream().filter(r -> r.getRankImpl() == rank).count();
            setItem(SLOTS[index], new ItemBuilder(rank.isOwnerRank() ? Material.NETHER_STAR : Material.NAME_TAG)
                    .name("&b" + rank.getName())
                    .lore("&7Members: &f" + members,
                            "&7Permissions: &f" + (rank.isOwnerRank() ? "all" : rank.getPermissions().size()),
                            "&eClick to view/edit permissions")
                    .build(), (player, event) -> new RankPermissionMenu(ctx, player, town, rank).open(player));
            index++;
        }
    }
}
