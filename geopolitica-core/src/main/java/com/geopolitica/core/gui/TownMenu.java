package com.geopolitica.core.gui;

import com.geopolitica.api.claim.Claim;
import com.geopolitica.api.events.ResidentLeaveTownEvent;
import com.geopolitica.core.model.ResidentImpl;
import com.geopolitica.core.model.TownImpl;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class TownMenu extends Menu {

    private final TownImpl town;

    public TownMenu(GuiContext ctx, Player viewer, TownImpl town) {
        super(ctx, viewer, 5, ChatColor.DARK_GRAY + town.getName());
        this.town = town;
        render();
    }

    private void render() {
        clear();
        fillBorder(new ItemBuilder(Material.BLUE_STAINED_GLASS_PANE).name(" ").build());

        setItem(10, new ItemBuilder(Material.BOOK)
                .name("&6" + town.getName())
                .lore("&7Owner: &f" + town.getOwner().getName(),
                        "&7Residents: &f" + town.getResidents().size(),
                        "&7Claims: &f" + town.getClaimCount() + "/" + (town.getMaxClaims() < 0 ? "∞" : town.getMaxClaims()),
                        "&7Bank: &f" + ctx.economyHook().format(town.getBankBalance()))
                .build());

        setItem(11, new ItemBuilder(Material.GRASS_BLOCK)
                .name("&aClaim this chunk")
                .lore("&7Claims the chunk you are standing in.")
                .build(), (player, event) -> {
            try {
                Claim claim = ctx.claimService().claim(town, player.getLocation().getChunk());
                player.sendMessage(ChatColor.GREEN + "Claimed (" + claim.getChunkX() + ", " + claim.getChunkZ() + ").");
            } catch (IllegalStateException | IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + e.getMessage());
            }
            render();
        });

        setItem(12, new ItemBuilder(Material.BARRIER)
                .name("&cUnclaim this chunk")
                .lore("&7Unclaims the chunk you are standing in.")
                .build(), (player, event) -> {
            try {
                Claim claim = ctx.claimService().getClaim(player.getLocation()).orElse(null);
                if (claim == null || claim.getTown() != town) {
                    player.sendMessage(ChatColor.RED + "Your town does not own this chunk.");
                } else {
                    ctx.claimService().unclaim(claim);
                    player.sendMessage(ChatColor.YELLOW + "Unclaimed this chunk.");
                }
            } catch (IllegalStateException | IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + e.getMessage());
            }
            render();
        });

        setItem(13, new ItemBuilder(Material.GOLD_INGOT)
                .name("&6Bank: " + ctx.economyHook().format(town.getBankBalance()))
                .lore("&7Use &f/town deposit <amount>", "&7or &f/town withdraw <amount>")
                .build());

        setItem(14, new ItemBuilder(Material.IRON_CHESTPLATE)
                .name("&bRanks")
                .lore("&7View and edit this town's ranks.")
                .build(), (player, event) -> new RankListMenu(ctx, player, town).open(player));

        setItem(15, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&bMembers (" + town.getResidents().size() + ")")
                .build(), (player, event) -> new MemberListMenu(ctx, player, town).open(player));

        String nationLine = town.getNation().map(n -> n.getName()).orElse("None");
        String stateLine = town.getState().map(s -> s.getName()).orElse("None");
        setItem(16, new ItemBuilder(Material.BEACON)
                .name("&dHierarchy")
                .lore("&7State: &f" + stateLine, "&7Nation: &f" + nationLine)
                .build(), (player, event) -> {
            if (town.getDirectNationImpl() != null) {
                new NationMenu(ctx, player, town.getDirectNationImpl()).open(player);
            } else if (town.getStateImpl() != null) {
                new NationMenu(ctx, player, town.getStateImpl().getNationImpl()).open(player);
            } else {
                player.sendMessage(ChatColor.YELLOW + "Your town has no nation. Use /nation join <name> or /nation new <name>.");
            }
        });

        setItem(19, new ItemBuilder(Material.RED_BED)
                .name("&aTeleport home")
                .build(), (player, event) -> {
            town.getHomeLocation().ifPresentOrElse(player::teleport,
                    () -> player.sendMessage(ChatColor.RED + "Your town has not set a home yet."));
        });

        setItem(20, new ItemBuilder(Material.COMPASS)
                .name("&aSet home here")
                .build(), (player, event) -> {
            var claim = ctx.claimService().getClaim(player.getLocation()).orElse(null);
            if (claim == null || claim.getTown() != town) {
                player.sendMessage(ChatColor.RED + "You must stand in your town's territory to set its home.");
            } else {
                town.setHomeLocation(player.getLocation());
                ctx.townService().saveTown(town);
                player.sendMessage(ChatColor.GREEN + "Town home set.");
            }
        });

        setItem(21, new ItemBuilder(town.isOpen() ? Material.OAK_DOOR : Material.IRON_DOOR)
                .name(town.isOpen() ? "&aOpen: ON" : "&cOpen: OFF")
                .lore("&7Click to toggle whether other players", "&7may freely /town join.")
                .build(), (player, event) -> {
            town.setOpen(!town.isOpen());
            ctx.townService().saveTown(town);
            render();
        });

        setItem(22, new ItemBuilder(town.isPvpEnabled() ? Material.IRON_SWORD : Material.WOODEN_HOE)
                .name(town.isPvpEnabled() ? "&cPvP: ON" : "&aPvP: OFF")
                .build(), (player, event) -> {
            town.setPvpEnabled(!town.isPvpEnabled());
            ctx.townService().saveTown(town);
            render();
        });

        setItem(40, new ItemBuilder(Material.TNT)
                .name("&c&lLeave / Disband")
                .lore("&7Click for options.")
                .build(), (player, event) -> {
            ResidentImpl resident = ctx.townService().getResidentImpl(player.getUniqueId());
            boolean isOwner = town.getOwner().getUniqueId().equals(resident.getUniqueId());
            String question = isOwner ? "Disband " + town.getName() + "? This cannot be undone." : "Leave " + town.getName() + "?";
            new ConfirmMenu(ctx, player, ChatColor.DARK_RED + "Confirm", question, () -> {
                try {
                    if (isOwner) {
                        ctx.townService().disbandTown(town);
                        player.sendMessage(ChatColor.YELLOW + "Your town has been disbanded.");
                    } else {
                        ctx.townService().leaveTown(resident, ResidentLeaveTownEvent.Cause.LEFT);
                        player.sendMessage(ChatColor.YELLOW + "You left your town.");
                    }
                } catch (IllegalStateException e) {
                    player.sendMessage(ChatColor.RED + e.getMessage());
                }
            }).open(player);
        });
    }
}
