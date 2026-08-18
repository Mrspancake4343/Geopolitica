package com.geopolitica.core.command;

import com.geopolitica.api.claim.Claim;
import com.geopolitica.api.events.ResidentLeaveTownEvent;
import com.geopolitica.api.town.TownPermission;
import com.geopolitica.core.economy.EconomyHook;
import com.geopolitica.core.gui.GuiListener;
import com.geopolitica.core.gui.GuiContext;
import com.geopolitica.core.gui.TownMenu;
import com.geopolitica.core.model.ResidentImpl;
import com.geopolitica.core.model.TownImpl;
import com.geopolitica.core.model.TownRankImpl;
import com.geopolitica.core.service.ClaimServiceImpl;
import com.geopolitica.core.service.NationServiceImpl;
import com.geopolitica.core.service.TownServiceImpl;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.Optional;

public class TownCommand implements CommandExecutor {

    private static final String PREFIX = ChatColor.GOLD + "[Geopolitica] " + ChatColor.RESET;

    private final TownServiceImpl townService;
    private final ClaimServiceImpl claimService;
    private final EconomyHook economyHook;
    private final GuiContext guiContext;

    public TownCommand(TownServiceImpl townService, ClaimServiceImpl claimService, NationServiceImpl nationService,
                        EconomyHook economyHook, GuiListener guiListener) {
        this.townService = townService;
        this.claimService = claimService;
        this.economyHook = economyHook;
        this.guiContext = new GuiContext(townService, claimService, nationService, economyHook, guiListener);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                TownImpl town = townService.getResidentImpl(player.getUniqueId()).getTownImpl();
                if (town != null) {
                    new TownMenu(guiContext, player, town).open(player);
                    return true;
                }
            }
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        try {
            switch (sub) {
                case "new" -> requirePlayer(sender, this::cmdNew, args);
                case "delete" -> requirePlayer(sender, this::cmdDelete, args);
                case "claim" -> requirePlayer(sender, this::cmdClaim, args);
                case "unclaim" -> requirePlayer(sender, this::cmdUnclaim, args);
                case "join" -> requirePlayer(sender, this::cmdJoin, args);
                case "leave" -> requirePlayer(sender, this::cmdLeave, args);
                case "kick" -> requirePlayer(sender, this::cmdKick, args);
                case "rank" -> requirePlayer(sender, this::cmdRank, args);
                case "deposit" -> requirePlayer(sender, this::cmdDeposit, args);
                case "withdraw" -> requirePlayer(sender, this::cmdWithdraw, args);
                case "sethome" -> requirePlayer(sender, this::cmdSetHome, args);
                case "home" -> requirePlayer(sender, this::cmdHome, args);
                case "description" -> requirePlayer(sender, this::cmdDescription, args);
                case "color" -> requirePlayer(sender, this::cmdColor, args);
                case "open" -> requirePlayer(sender, this::cmdOpen, args);
                case "pvp" -> requirePlayer(sender, this::cmdPvp, args);
                case "gui" -> requirePlayer(sender, this::cmdGui, args);
                case "info" -> cmdInfo(sender, args);
                case "list" -> cmdList(sender);
                default -> sendHelp(sender);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + e.getMessage());
        }
        return true;
    }

    private void cmdGui(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        new TownMenu(guiContext, player, town).open(player);
    }

    private interface PlayerAction {
        void run(Player player, String[] args);
    }

    private void requirePlayer(CommandSender sender, PlayerAction action, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "This command can only be used in-game.");
            return;
        }
        action.run(player, args);
    }

    // --- Town lifecycle -----------------------------------------------

    private void cmdNew(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /town new <name>");
            return;
        }
        if (!player.hasPermission("geopolitica.town.create")) {
            player.sendMessage(PREFIX + ChatColor.RED + "You do not have permission to create a town.");
            return;
        }
        ResidentImpl resident = townService.getResidentImpl(player.getUniqueId());
        townService.createTown(args[1], resident);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Founded the town of " + args[1] + "!");
    }

    private void cmdDelete(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.DISBAND_TOWN);
        if (args.length < 2 || !args[1].equalsIgnoreCase(town.getName())) {
            player.sendMessage(PREFIX + ChatColor.RED + "Type /town delete " + town.getName() + " to confirm.");
            return;
        }
        townService.disbandTown(town);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Your town has been disbanded.");
    }

    // --- Claiming --------------------------------------------------------

    private void cmdClaim(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.CLAIM_LAND);
        Chunk chunk = player.getLocation().getChunk();
        Claim claim = claimService.claim(town, chunk);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Claimed chunk (" + claim.getChunkX() + ", " + claim.getChunkZ() + ") for " + town.getName() + ".");
    }

    private void cmdUnclaim(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.UNCLAIM_LAND);
        Chunk chunk = player.getLocation().getChunk();
        Claim claim = claimService.getClaim(chunk).orElse(null);
        if (claim == null || claim.getTown() != town) {
            player.sendMessage(PREFIX + ChatColor.RED + "Your town does not own this chunk.");
            return;
        }
        claimService.unclaim(claim);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Unclaimed this chunk.");
    }

    // --- Membership --------------------------------------------------

    private void cmdJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /town join <town>");
            return;
        }
        TownImpl town = townService.getTownImpl(args[1])
                .orElseThrow(() -> new IllegalArgumentException("No town named '" + args[1] + "' exists"));
        if (!town.isOpen()) {
            throw new IllegalStateException(town.getName() + " is invite-only.");
        }
        townService.joinTown(town, townService.getResidentImpl(player.getUniqueId()));
        player.sendMessage(PREFIX + ChatColor.GREEN + "You joined " + town.getName() + "!");
    }

    private void cmdLeave(Player player, String[] args) {
        ResidentImpl resident = townService.getResidentImpl(player.getUniqueId());
        townService.leaveTown(resident, ResidentLeaveTownEvent.Cause.LEFT);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "You left your town.");
    }

    private void cmdKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /town kick <player>");
            return;
        }
        TownImpl town = residentTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.KICK_RESIDENT);
        ResidentImpl target = town.getResidentImpls().stream()
                .filter(r -> r.getName().equalsIgnoreCase(args[1]))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(args[1] + " is not a resident of your town"));
        townService.leaveTown(target, ResidentLeaveTownEvent.Cause.KICKED);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Kicked " + target.getName() + " from " + town.getName() + ".");
    }

    // --- Ranks -------------------------------------------------------

    private void cmdRank(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /town rank <create|delete|set|assign> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> {
                requireTownPermission(player, town, TownPermission.MANAGE_RANKS);
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /town rank create <name>");
                    return;
                }
                town.createRank(args[2]);
                townService.saveTown(town);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Created rank '" + args[2] + "'.");
            }
            case "delete" -> {
                requireTownPermission(player, town, TownPermission.MANAGE_RANKS);
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /town rank delete <name>");
                    return;
                }
                town.deleteRank(args[2]);
                townService.saveTown(town);
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Deleted rank '" + args[2] + "'.");
            }
            case "set" -> {
                requireTownPermission(player, town, TownPermission.MANAGE_RANKS);
                if (args.length < 5) {
                    player.sendMessage(PREFIX + "Usage: /town rank set <rank> <permission> <true|false>");
                    return;
                }
                TownRankImpl rank = town.getRankImpl(args[2]);
                if (rank == null) {
                    throw new IllegalArgumentException("No rank named '" + args[2] + "' exists");
                }
                TownPermission permission = parsePermission(args[3]);
                rank.setPermission(permission, Boolean.parseBoolean(args[4]));
                townService.saveTown(town);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Updated '" + rank.getName() + "'.");
            }
            case "assign" -> {
                requireTownPermission(player, town, TownPermission.ASSIGN_RANKS);
                if (args.length < 4) {
                    player.sendMessage(PREFIX + "Usage: /town rank assign <player> <rank>");
                    return;
                }
                ResidentImpl target = town.getResidentImpls().stream()
                        .filter(r -> r.getName().equalsIgnoreCase(args[2]))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(args[2] + " is not a resident of your town"));
                TownRankImpl rank = town.getRankImpl(args[3]);
                if (rank == null) {
                    throw new IllegalArgumentException("No rank named '" + args[3] + "' exists");
                }
                townService.assignRank(town, target, rank);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Assigned " + target.getName() + " to '" + rank.getName() + "'.");
            }
            default -> player.sendMessage(PREFIX + "Usage: /town rank <create|delete|set|assign> ...");
        }
    }

    private TownPermission parsePermission(String value) {
        try {
            return TownPermission.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown permission '" + value + "'");
        }
    }

    // --- Economy -------------------------------------------------------

    private void cmdDeposit(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        double amount = parseAmount(args);
        if (!economyHook.withdraw(player, amount)) {
            throw new IllegalStateException("You do not have " + economyHook.format(amount) + ".");
        }
        town.depositBank(amount);
        townService.saveTown(town);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Deposited " + economyHook.format(amount) + " into the town bank.");
    }

    private void cmdWithdraw(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.WITHDRAW_BANK);
        double amount = parseAmount(args);
        if (!town.withdrawBank(amount)) {
            throw new IllegalStateException("The town bank does not have that much.");
        }
        economyHook.deposit(player, amount);
        townService.saveTown(town);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Withdrew " + economyHook.format(amount) + " from the town bank.");
    }

    private double parseAmount(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /town " + args[0] + " <amount>");
        }
        try {
            double amount = Double.parseDouble(args[1]);
            if (amount <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + args[1] + "' is not a number");
        }
    }

    // --- Metadata / moderation ------------------------------------------

    private void cmdSetHome(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.SET_HOME);
        Claim claim = claimService.getClaim(player.getLocation()).orElse(null);
        if (claim == null || claim.getTown() != town) {
            throw new IllegalStateException("You must be standing in your town's territory to set its home.");
        }
        town.setHomeLocation(player.getLocation());
        townService.saveTown(town);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Town home set.");
    }

    private void cmdHome(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        town.getHomeLocation().ifPresentOrElse(
                player::teleport,
                () -> player.sendMessage(PREFIX + ChatColor.RED + "Your town has not set a home yet.")
        );
    }

    private void cmdDescription(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.EDIT_METADATA);
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /town description <text>");
            return;
        }
        town.setDescription(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
        townService.saveTown(town);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Description updated.");
    }

    private void cmdColor(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.EDIT_METADATA);
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /town color <#rrggbb>");
            return;
        }
        try {
            town.setMapColor(Color.decode(args[1]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + args[1] + "' is not a valid hex color, e.g. #55AAFF");
        }
        townService.saveTown(town);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Map color updated.");
    }

    private void cmdOpen(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.EDIT_METADATA);
        town.setOpen(!town.isOpen());
        townService.saveTown(town);
        player.sendMessage(PREFIX + ChatColor.GREEN + town.getName() + " is now " + (town.isOpen() ? "open to join." : "invite-only."));
    }

    private void cmdPvp(Player player, String[] args) {
        TownImpl town = residentTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.TOGGLE_PVP);
        town.setPvpEnabled(!town.isPvpEnabled());
        townService.saveTown(town);
        player.sendMessage(PREFIX + ChatColor.GREEN + "PvP in " + town.getName() + " is now " + (town.isPvpEnabled() ? "enabled." : "disabled."));
    }

    // --- Info --------------------------------------------------------

    private void cmdInfo(CommandSender sender, String[] args) {
        Optional<TownImpl> townOpt;
        if (args.length >= 2) {
            townOpt = townService.getTownImpl(args[1]);
        } else if (sender instanceof Player player) {
            townOpt = Optional.ofNullable(townService.getResidentImpl(player.getUniqueId()).getTownImpl());
        } else {
            townOpt = Optional.empty();
        }
        TownImpl town = townOpt.orElseThrow(() -> new IllegalArgumentException("No such town."));

        sender.sendMessage(PREFIX + ChatColor.GOLD + "== " + town.getName() + " ==");
        sender.sendMessage(ChatColor.GRAY + "Owner: " + ChatColor.WHITE + town.getOwner().getName());
        sender.sendMessage(ChatColor.GRAY + "Residents: " + ChatColor.WHITE + town.getResidents().size());
        sender.sendMessage(ChatColor.GRAY + "Claims: " + ChatColor.WHITE + town.getClaimCount() + "/" + (town.getMaxClaims() < 0 ? "∞" : town.getMaxClaims()));
        sender.sendMessage(ChatColor.GRAY + "Bank: " + ChatColor.WHITE + economyHook.format(town.getBankBalance()));
        sender.sendMessage(ChatColor.GRAY + "Open: " + ChatColor.WHITE + town.isOpen() + ChatColor.GRAY + "  PvP: " + ChatColor.WHITE + town.isPvpEnabled());
        sender.sendMessage(ChatColor.GRAY + "State: " + ChatColor.WHITE + town.getState().map(s -> s.getName()).orElse("None")
                + ChatColor.GRAY + "  Nation: " + ChatColor.WHITE + town.getNation().map(n -> n.getName()).orElse("None"));
        if (!town.getDescription().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + town.getDescription());
        }
    }

    private void cmdList(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "Towns (" + townService.getTowns().size() + ")");
        for (var town : townService.getTowns()) {
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + town.getName() + ChatColor.GRAY + " (" + town.getResidents().size() + " residents)");
        }
    }

    // --- Shared helpers ------------------------------------------------

    private TownImpl residentTownOrThrow(Player player) {
        ResidentImpl resident = townService.getResidentImpl(player.getUniqueId());
        TownImpl town = resident.getTownImpl();
        if (town == null) {
            throw new IllegalStateException("You do not belong to a town.");
        }
        return town;
    }

    private void requireTownPermission(Player player, TownImpl town, TownPermission permission) {
        ResidentImpl resident = townService.getResidentImpl(player.getUniqueId());
        if (!resident.hasPermission(permission) && !player.hasPermission("geopolitica.admin")) {
            throw new IllegalStateException("Your rank does not allow you to do that.");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "Commands:");
        sender.sendMessage(ChatColor.GRAY + "/town new <name>, delete <name>, info [town], list");
        sender.sendMessage(ChatColor.GRAY + "/town claim, unclaim, sethome, home");
        sender.sendMessage(ChatColor.GRAY + "/town join <town>, leave, kick <player>");
        sender.sendMessage(ChatColor.GRAY + "/town rank create|delete|set|assign ...");
        sender.sendMessage(ChatColor.GRAY + "/town deposit|withdraw <amount>, description <text>, color <#hex>, open, pvp");
        sender.sendMessage(ChatColor.GRAY + "/town gui - opens the town menu (also opened by /town with no arguments)");
    }
}
