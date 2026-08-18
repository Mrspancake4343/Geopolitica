package com.geopolitica.core.gui;

import com.geopolitica.api.town.Town;
import com.geopolitica.core.model.NationImpl;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class NationMenu extends Menu {

    private final NationImpl nation;

    public NationMenu(GuiContext ctx, Player viewer, NationImpl nation) {
        super(ctx, viewer, 3, ChatColor.DARK_GRAY + nation.getName());
        this.nation = nation;
        render(viewer);
    }

    private void render(Player viewer) {
        clear();
        fillBorder(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());

        String towns = nation.getAllTowns().stream().map(Town::getName).collect(Collectors.joining(", "));
        setItem(10, new ItemBuilder(Material.BEACON)
                .name("&6" + nation.getName())
                .lore("&7Leader: &f" + nation.getLeader().getName(),
                        "&7Capital: &f" + nation.getCapital().getName(),
                        "&7States: &f" + nation.getStates().size(),
                        "&7Towns: &f" + nation.getAllTowns().size(),
                        "&7" + (towns.isEmpty() ? "" : towns))
                .build());

        setItem(12, new ItemBuilder(Material.GOLD_INGOT)
                .name("&6Bank: " + ctx.economyHook().format(nation.getBankBalance()))
                .lore("&7Use &f/nation deposit <amount>", "&7or &f/nation withdraw <amount>")
                .build());

        setItem(14, new ItemBuilder(Material.CROSSBOW)
                .name("&cDiplomacy")
                .lore("&7View relations and declare war,", "&7peace, or request alliances.")
                .build(), (player, event) -> new DiplomacyMenu(ctx, player, nation).open(player));

        boolean canEdit = nation.getLeader().getUniqueId().equals(viewer.getUniqueId()) || viewer.hasPermission("geopolitica.admin");
        setItem(16, new ItemBuilder(nation.isOpen() ? Material.OAK_DOOR : Material.IRON_DOOR)
                .name(nation.isOpen() ? "&aOpen: ON" : "&cOpen: OFF")
                .lore("&7Click to toggle whether other towns", "&7may freely /nation join.")
                .build(), (player, event) -> {
            if (!canEdit) {
                player.sendMessage(ChatColor.RED + "Only the nation's leader may change this.");
                return;
            }
            nation.setOpen(!nation.isOpen());
            ctx.nationService().saveNation(nation);
            render(player);
        });
    }
}
