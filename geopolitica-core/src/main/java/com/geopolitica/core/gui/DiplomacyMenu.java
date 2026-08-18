package com.geopolitica.core.gui;

import com.geopolitica.api.nation.DiplomaticStatus;
import com.geopolitica.api.nation.Nation;
import com.geopolitica.core.model.NationImpl;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class DiplomacyMenu extends Menu {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final NationImpl nation;

    public DiplomacyMenu(GuiContext ctx, Player viewer, NationImpl nation) {
        super(ctx, viewer, 5, ChatColor.DARK_GRAY + nation.getName() + " Diplomacy");
        this.nation = nation;
        render(viewer);
    }

    private void render(Player viewer) {
        clear();
        fillBorder(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());

        boolean canEdit = nation.getLeader().getUniqueId().equals(viewer.getUniqueId()) || viewer.hasPermission("geopolitica.admin");

        int index = 0;
        for (Nation other : ctx.nationService().getNations()) {
            if (other == nation || !(other instanceof NationImpl otherImpl)) {
                continue;
            }
            if (index >= SLOTS.length) {
                break;
            }
            DiplomaticStatus relation = ctx.nationService().getRelation(nation, other);
            Material material = switch (relation) {
                case ALLY -> Material.LIME_WOOL;
                case ENEMY -> Material.RED_WOOL;
                case NEUTRAL -> Material.YELLOW_WOOL;
            };
            setItem(SLOTS[index], new ItemBuilder(material)
                    .name("&f" + other.getName())
                    .lore("&7Relation: &f" + relation,
                            "&7Left-click: primary action",
                            "&7Right-click: request alliance")
                    .build(), (player, event) -> {
                if (!canEdit) {
                    player.sendMessage(ChatColor.RED + "Only the nation's leader may manage diplomacy.");
                    return;
                }
                if (event.isRightClick()) {
                    ctx.nationService().requestAlliance(nation, otherImpl);
                    player.sendMessage(ChatColor.GREEN + "Alliance requested with " + otherImpl.getName() + ".");
                    render(player);
                    return;
                }
                switch (relation) {
                    case NEUTRAL -> new ConfirmMenu(ctx, player, ChatColor.DARK_RED + "Confirm",
                            "Declare war on " + otherImpl.getName() + "?", () -> {
                        ctx.nationService().declareWar(nation, otherImpl);
                        player.sendMessage(ChatColor.RED + "War declared on " + otherImpl.getName() + "!");
                        new DiplomacyMenu(ctx, player, nation).open(player);
                    }).open(player);
                    case ENEMY -> {
                        ctx.nationService().declarePeace(nation, otherImpl);
                        player.sendMessage(ChatColor.YELLOW + "Peace declared with " + otherImpl.getName() + ".");
                        render(player);
                    }
                    case ALLY -> new ConfirmMenu(ctx, player, ChatColor.DARK_RED + "Confirm",
                            "Break alliance with " + otherImpl.getName() + "?", () -> {
                        ctx.nationService().breakAlliance(nation, otherImpl);
                        player.sendMessage(ChatColor.YELLOW + "Alliance with " + otherImpl.getName() + " broken.");
                        new DiplomacyMenu(ctx, player, nation).open(player);
                    }).open(player);
                }
            });
            index++;
        }
    }
}
