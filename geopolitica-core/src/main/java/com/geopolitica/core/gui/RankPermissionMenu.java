package com.geopolitica.core.gui;

import com.geopolitica.api.town.TownPermission;
import com.geopolitica.core.model.TownImpl;
import com.geopolitica.core.model.TownRankImpl;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class RankPermissionMenu extends Menu {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final TownImpl town;
    private final TownRankImpl rank;

    public RankPermissionMenu(GuiContext ctx, Player viewer, TownImpl town, TownRankImpl rank) {
        super(ctx, viewer, 5, ChatColor.DARK_GRAY + rank.getName() + " Permissions");
        this.town = town;
        this.rank = rank;
        render(viewer);
    }

    private void render(Player viewer) {
        clear();
        fillBorder(new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());

        if (rank.isOwnerRank()) {
            setItem(13, new ItemBuilder(Material.NETHER_STAR)
                    .name("&6The owner rank always has every permission.")
                    .build());
            return;
        }

        boolean canEdit = town.getOwner().getUniqueId().equals(viewer.getUniqueId())
                || ctx.townService().getResidentImpl(viewer.getUniqueId()).hasPermission(TownPermission.MANAGE_RANKS)
                || viewer.hasPermission("geopolitica.admin");

        TownPermission[] permissions = TownPermission.values();
        for (int i = 0; i < permissions.length && i < SLOTS.length; i++) {
            TownPermission permission = permissions[i];
            boolean granted = rank.hasPermission(permission);
            setItem(SLOTS[i], new ItemBuilder(granted ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name((granted ? "&a" : "&7") + permission.name())
                    .lore(granted ? "&7Granted - click to revoke" : "&7Not granted - click to grant")
                    .build(), (player, event) -> {
                if (!canEdit) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to edit ranks.");
                    return;
                }
                rank.setPermission(permission, !rank.hasPermission(permission));
                ctx.townService().saveTown(town);
                render(player);
            });
        }
    }
}
