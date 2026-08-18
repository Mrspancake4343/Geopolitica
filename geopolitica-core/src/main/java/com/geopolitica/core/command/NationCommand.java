package com.geopolitica.core.command;

import com.geopolitica.api.nation.Nation;
import com.geopolitica.api.state.State;
import com.geopolitica.api.town.Resident;
import com.geopolitica.api.town.TownPermission;
import com.geopolitica.core.economy.EconomyHook;
import com.geopolitica.core.gui.GuiContext;
import com.geopolitica.core.gui.GuiListener;
import com.geopolitica.core.gui.NationMenu;
import com.geopolitica.core.model.NationImpl;
import com.geopolitica.core.model.ResidentImpl;
import com.geopolitica.core.model.StateImpl;
import com.geopolitica.core.model.TownImpl;
import com.geopolitica.core.service.ClaimServiceImpl;
import com.geopolitica.core.service.NationServiceImpl;
import com.geopolitica.core.service.TownServiceImpl;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.awt.Color;

public class NationCommand implements CommandExecutor {

    private static final String PREFIX = ChatColor.GOLD + "[Geopolitica] " + ChatColor.RESET;

    private final TownServiceImpl townService;
    private final NationServiceImpl nationService;
    private final EconomyHook economyHook;
    private final GuiContext guiContext;

    public NationCommand(TownServiceImpl townService, ClaimServiceImpl claimService, NationServiceImpl nationService,
                          EconomyHook economyHook, GuiListener guiListener) {
        this.townService = townService;
        this.nationService = nationService;
        this.economyHook = economyHook;
        this.guiContext = new GuiContext(townService, claimService, nationService, economyHook, guiListener);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                NationImpl nation = currentNation(player);
                if (nation != null) {
                    new NationMenu(guiContext, player, nation).open(player);
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
                case "join" -> requirePlayer(sender, this::cmdJoin, args);
                case "leave" -> requirePlayer(sender, this::cmdLeave, args);
                case "deposit" -> requirePlayer(sender, this::cmdDeposit, args);
                case "withdraw" -> requirePlayer(sender, this::cmdWithdraw, args);
                case "description" -> requirePlayer(sender, this::cmdDescription, args);
                case "color" -> requirePlayer(sender, this::cmdColor, args);
                case "open" -> requirePlayer(sender, this::cmdOpen, args);
                case "war" -> requirePlayer(sender, this::cmdWar, args);
                case "peace" -> requirePlayer(sender, this::cmdPeace, args);
                case "ally" -> requirePlayer(sender, this::cmdAlly, args);
                case "state" -> requirePlayer(sender, this::cmdState, args);
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

    private void cmdGui(Player player, String[] args) {
        NationImpl nation = currentNation(player);
        if (nation == null) {
            throw new IllegalStateException("Your town does not belong to a nation.");
        }
        new NationMenu(guiContext, player, nation).open(player);
    }

    // --- Nation lifecycle -------------------------------------------------

    private void cmdNew(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /nation new <name>");
            return;
        }
        if (!player.hasPermission("geopolitica.nation.create")) {
            player.sendMessage(PREFIX + ChatColor.RED + "You do not have permission to found a nation.");
            return;
        }
        TownImpl town = ownTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.MANAGE_NATION);
        Nation nation = nationService.createNation(args[1], town);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Founded the nation of " + nation.getName() + "!");
    }

    private void cmdDelete(Player player, String[] args) {
        NationImpl nation = requireLeader(player);
        if (args.length < 2 || !args[1].equalsIgnoreCase(nation.getName())) {
            player.sendMessage(PREFIX + ChatColor.RED + "Type /nation delete " + nation.getName() + " to confirm.");
            return;
        }
        nationService.disbandNation(nation);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "The nation has been disbanded.");
    }

    private void cmdJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /nation join <nation>");
            return;
        }
        TownImpl town = ownTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.MANAGE_NATION);
        NationImpl nation = nationService.getNationImpl(args[1])
                .orElseThrow(() -> new IllegalArgumentException("No nation named '" + args[1] + "' exists"));
        nationService.joinNation(town, nation);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Your town joined " + nation.getName() + "!");
    }

    private void cmdLeave(Player player, String[] args) {
        TownImpl town = ownTownOrThrow(player);
        requireTownPermission(player, town, TownPermission.MANAGE_NATION);
        nationService.leaveNation(town);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Your town left its nation.");
    }

    // --- Economy / metadata ------------------------------------------

    private void cmdDeposit(Player player, String[] args) {
        NationImpl nation = requireNation(player);
        double amount = parseAmount(args);
        if (!economyHook.withdraw(player, amount)) {
            throw new IllegalStateException("You do not have " + economyHook.format(amount) + ".");
        }
        nation.depositBank(amount);
        nationService.saveNation(nation);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Deposited " + economyHook.format(amount) + " into the nation bank.");
    }

    private void cmdWithdraw(Player player, String[] args) {
        NationImpl nation = requireLeader(player);
        double amount = parseAmount(args);
        if (!nation.withdrawBank(amount)) {
            throw new IllegalStateException("The nation bank does not have that much.");
        }
        economyHook.deposit(player, amount);
        nationService.saveNation(nation);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Withdrew " + economyHook.format(amount) + " from the nation bank.");
    }

    private double parseAmount(String[] args) {
        return parseAmount("/nation " + args[0] + " <amount>", args.length < 2 ? null : args[1]);
    }

    private double parseAmount(String usage, String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
        try {
            double amount = Double.parseDouble(raw);
            if (amount <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a number");
        }
    }

    private void cmdDescription(Player player, String[] args) {
        NationImpl nation = requireLeader(player);
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /nation description <text>");
            return;
        }
        nation.setDescription(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
        nationService.saveNation(nation);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Description updated.");
    }

    private void cmdColor(Player player, String[] args) {
        NationImpl nation = requireLeader(player);
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /nation color <#rrggbb>");
            return;
        }
        try {
            nation.setMapColor(Color.decode(args[1]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + args[1] + "' is not a valid hex color, e.g. #55AAFF");
        }
        nationService.saveNation(nation);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Map color updated.");
    }

    private void cmdOpen(Player player, String[] args) {
        NationImpl nation = requireLeader(player);
        nation.setOpen(!nation.isOpen());
        nationService.saveNation(nation);
        player.sendMessage(PREFIX + ChatColor.GREEN + nation.getName() + " is now " + (nation.isOpen() ? "open to join." : "invite-only."));
    }

    // --- Diplomacy -------------------------------------------------------

    private void cmdWar(Player player, String[] args) {
        NationImpl nation = requireLeader(player);
        NationImpl other = requireOtherNation(args, nation);
        nationService.declareWar(nation, other);
        player.sendMessage(PREFIX + ChatColor.RED + "War declared on " + other.getName() + "!");
    }

    private void cmdPeace(Player player, String[] args) {
        NationImpl nation = requireLeader(player);
        NationImpl other = requireOtherNation(args, nation);
        nationService.declarePeace(nation, other);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Peace declared with " + other.getName() + ".");
    }

    private void cmdAlly(Player player, String[] args) {
        NationImpl nation = requireLeader(player);
        NationImpl other = requireOtherNation(args, nation);
        nationService.requestAlliance(nation, other);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Alliance requested with " + other.getName() + ".");
    }

    private NationImpl requireOtherNation(String[] args, NationImpl self) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /nation " + args[0] + " <nation>");
        }
        NationImpl other = nationService.getNationImpl(args[1])
                .orElseThrow(() -> new IllegalArgumentException("No nation named '" + args[1] + "' exists"));
        if (other == self) {
            throw new IllegalArgumentException("You cannot target your own nation.");
        }
        return other;
    }

    // --- States --------------------------------------------------------

    private void cmdState(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "Usage: /nation state <new|delete|join|leave> ...");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "new" -> {
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /nation state new <name>");
                    return;
                }
                TownImpl town = ownTownOrThrow(player);
                requireTownPermission(player, town, TownPermission.MANAGE_NATION);
                NationImpl nation = town.getDirectNationImpl();
                if (nation == null) {
                    throw new IllegalStateException("Your town must be a direct member of a nation to found a state.");
                }
                State state = nationService.createState(args[2], nation, town);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Founded the state of " + state.getName() + "!");
            }
            case "delete" -> {
                TownImpl town = ownTownOrThrow(player);
                requireTownPermission(player, town, TownPermission.MANAGE_NATION);
                StateImpl state = town.getStateImpl();
                if (state == null || state.getCapitalImpl() != town) {
                    throw new IllegalStateException("Your town is not the capital of a state.");
                }
                if (args.length < 3 || !args[2].equalsIgnoreCase(state.getName())) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Type /nation state delete " + state.getName() + " to confirm.");
                    return;
                }
                nationService.disbandState(state);
                player.sendMessage(PREFIX + ChatColor.YELLOW + "The state has been disbanded.");
            }
            case "join" -> {
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /nation state join <state>");
                    return;
                }
                TownImpl town = ownTownOrThrow(player);
                requireTownPermission(player, town, TownPermission.MANAGE_NATION);
                StateImpl state = nationService.getStateImpl(args[2])
                        .orElseThrow(() -> new IllegalArgumentException("No state named '" + args[2] + "' exists"));
                nationService.joinState(town, state);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Your town joined " + state.getName() + "!");
            }
            case "leave" -> {
                TownImpl town = ownTownOrThrow(player);
                requireTownPermission(player, town, TownPermission.MANAGE_NATION);
                nationService.leaveState(town);
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Your town left its state.");
            }
            case "leader" -> {
                StateImpl state = requireStateLeader(player);
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /nation state leader <player>");
                    return;
                }
                Resident target = townService.getResident(args[2])
                        .orElseThrow(() -> new IllegalArgumentException("No known resident named '" + args[2] + "' exists"));
                nationService.setStateLeader(state, target);
                player.sendMessage(PREFIX + ChatColor.GREEN + target.getName() + " is now the leader of " + state.getName() + ".");
            }
            case "secede" -> {
                StateImpl state = requireStateLeader(player);
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /nation state secede <newNationName>");
                    return;
                }
                Nation newNation = nationService.secedeState(state, args[2]);
                player.sendMessage(PREFIX + ChatColor.GREEN + state.getName() + " has seceded and founded the nation of "
                        + newNation.getName() + "!");
            }
            case "capital" -> {
                StateImpl state = requireStateLeader(player);
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /nation state capital <town>");
                    return;
                }
                TownImpl newCapital = townService.getTownImpl(args[2])
                        .orElseThrow(() -> new IllegalArgumentException("No town named '" + args[2] + "' exists"));
                nationService.setStateCapital(state, newCapital);
                player.sendMessage(PREFIX + ChatColor.GREEN + newCapital.getName() + " is now the capital of " + state.getName() + ".");
            }
            case "kick" -> {
                StateImpl state = requireStateLeader(player);
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /nation state kick <town>");
                    return;
                }
                TownImpl target = townService.getTownImpl(args[2])
                        .orElseThrow(() -> new IllegalArgumentException("No town named '" + args[2] + "' exists"));
                nationService.kickTownFromState(state, target);
                player.sendMessage(PREFIX + ChatColor.YELLOW + target.getName() + " has been kicked from " + state.getName() + ".");
            }
            case "tax" -> {
                StateImpl state = requireStateLeader(player);
                if (args.length < 4) {
                    player.sendMessage(PREFIX + "Usage: /nation state tax <town> <amount>");
                    return;
                }
                TownImpl target = townService.getTownImpl(args[2])
                        .orElseThrow(() -> new IllegalArgumentException("No town named '" + args[2] + "' exists"));
                double amount = parseAmount("/nation state tax <town> <amount>", args[3]);
                nationService.collectStateTax(state, target, amount);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Collected " + economyHook.format(amount) + " from " + target.getName() + ".");
            }
            case "deposit" -> {
                StateImpl state = requireState(player);
                double amount = parseAmount("/nation state deposit <amount>", args.length < 3 ? null : args[2]);
                if (!economyHook.withdraw(player, amount)) {
                    throw new IllegalStateException("You do not have " + economyHook.format(amount) + ".");
                }
                state.depositBank(amount);
                nationService.saveState(state);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Deposited " + economyHook.format(amount) + " into the state bank.");
            }
            case "withdraw" -> {
                StateImpl state = requireStateLeader(player);
                double amount = parseAmount("/nation state withdraw <amount>", args.length < 3 ? null : args[2]);
                if (!state.withdrawBank(amount)) {
                    throw new IllegalStateException("The state bank does not have that much.");
                }
                economyHook.deposit(player, amount);
                nationService.saveState(state);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Withdrew " + economyHook.format(amount) + " from the state bank.");
            }
            case "description" -> {
                StateImpl state = requireStateLeader(player);
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /nation state description <text>");
                    return;
                }
                state.setDescription(String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                nationService.saveState(state);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Description updated.");
            }
            case "color" -> {
                StateImpl state = requireStateLeader(player);
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /nation state color <#rrggbb>");
                    return;
                }
                try {
                    state.setMapColor(Color.decode(args[2]));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("'" + args[2] + "' is not a valid hex color, e.g. #55AAFF");
                }
                nationService.saveState(state);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Map color updated.");
            }
            case "open" -> {
                StateImpl state = requireStateLeader(player);
                state.setOpen(!state.isOpen());
                nationService.saveState(state);
                player.sendMessage(PREFIX + ChatColor.GREEN + state.getName() + " is now "
                        + (state.isOpen() ? "open to join." : "invite-only."));
            }
            case "info" -> {
                StateImpl state = args.length >= 3
                        ? nationService.getStateImpl(args[2]).orElseThrow(() -> new IllegalArgumentException("No such state."))
                        : requireState(player);
                player.sendMessage(PREFIX + ChatColor.GOLD + "== " + state.getName() + " ==");
                player.sendMessage(ChatColor.GRAY + "Nation: " + ChatColor.WHITE + state.getNation().getName());
                player.sendMessage(ChatColor.GRAY + "Leader: " + ChatColor.WHITE + state.getLeader().getName());
                player.sendMessage(ChatColor.GRAY + "Capital: " + ChatColor.WHITE + state.getCapital().getName());
                player.sendMessage(ChatColor.GRAY + "Towns: " + ChatColor.WHITE + state.getTowns().size());
                player.sendMessage(ChatColor.GRAY + "Bank: " + ChatColor.WHITE + economyHook.format(state.getBankBalance()));
                player.sendMessage(ChatColor.GRAY + "Open: " + ChatColor.WHITE + state.isOpen());
                if (!state.getDescription().isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + state.getDescription());
                }
            }
            default -> player.sendMessage(PREFIX + "Usage: /nation state <new|delete|join|leave|leader|secede|"
                    + "capital|kick|tax|deposit|withdraw|description|color|open|info> ...");
        }
    }

    // --- Info --------------------------------------------------------

    private void cmdInfo(CommandSender sender, String[] args) {
        NationImpl nation;
        if (args.length >= 2) {
            nation = nationService.getNationImpl(args[1]).orElseThrow(() -> new IllegalArgumentException("No such nation."));
        } else if (sender instanceof Player player) {
            nation = currentNation(player);
            if (nation == null) {
                throw new IllegalArgumentException("Your town does not belong to a nation.");
            }
        } else {
            throw new IllegalArgumentException("Usage: /nation info <name>");
        }

        sender.sendMessage(PREFIX + ChatColor.GOLD + "== " + nation.getName() + " ==");
        sender.sendMessage(ChatColor.GRAY + "Leader: " + ChatColor.WHITE + nation.getLeader().getName());
        sender.sendMessage(ChatColor.GRAY + "Capital: " + ChatColor.WHITE + nation.getCapital().getName());
        sender.sendMessage(ChatColor.GRAY + "States: " + ChatColor.WHITE + nation.getStates().size()
                + ChatColor.GRAY + "  Towns: " + ChatColor.WHITE + nation.getAllTowns().size());
        sender.sendMessage(ChatColor.GRAY + "Bank: " + ChatColor.WHITE + economyHook.format(nation.getBankBalance()));
        sender.sendMessage(ChatColor.GRAY + "Open: " + ChatColor.WHITE + nation.isOpen());
        if (!nation.getDescription().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + nation.getDescription());
        }
    }

    private void cmdList(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "Nations (" + nationService.getNations().size() + ")");
        for (var nation : nationService.getNations()) {
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + nation.getName()
                    + ChatColor.GRAY + " (" + nation.getAllTowns().size() + " towns)");
        }
    }

    // --- Shared helpers ------------------------------------------------

    private TownImpl ownTownOrThrow(Player player) {
        ResidentImpl resident = townService.getResidentImpl(player.getUniqueId());
        TownImpl town = resident.getTownImpl();
        if (town == null) {
            throw new IllegalStateException("You do not belong to a town.");
        }
        return town;
    }

    private NationImpl currentNation(Player player) {
        TownImpl town = townService.getResidentImpl(player.getUniqueId()).getTownImpl();
        if (town == null) {
            return null;
        }
        if (town.getDirectNationImpl() != null) {
            return town.getDirectNationImpl();
        }
        if (town.getStateImpl() != null) {
            return town.getStateImpl().getNationImpl();
        }
        return null;
    }

    private NationImpl requireNation(Player player) {
        NationImpl nation = currentNation(player);
        if (nation == null) {
            throw new IllegalStateException("Your town does not belong to a nation.");
        }
        return nation;
    }

    private NationImpl requireLeader(Player player) {
        NationImpl nation = requireNation(player);
        if (!nation.getLeader().getUniqueId().equals(player.getUniqueId()) && !player.hasPermission("geopolitica.admin")) {
            throw new IllegalStateException("Only the nation's leader may do that.");
        }
        return nation;
    }

    private StateImpl requireState(Player player) {
        TownImpl town = ownTownOrThrow(player);
        StateImpl state = town.getStateImpl();
        if (state == null) {
            throw new IllegalStateException("Your town does not belong to a state.");
        }
        return state;
    }

    private StateImpl requireStateLeader(Player player) {
        StateImpl state = requireState(player);
        if (!state.getLeader().getUniqueId().equals(player.getUniqueId()) && !player.hasPermission("geopolitica.admin")) {
            throw new IllegalStateException("Only the state's leader may do that.");
        }
        return state;
    }

    private void requireTownPermission(Player player, TownImpl town, TownPermission permission) {
        ResidentImpl resident = townService.getResidentImpl(player.getUniqueId());
        if (!resident.hasPermission(permission) && !player.hasPermission("geopolitica.admin")) {
            throw new IllegalStateException("Your rank does not allow you to manage nation affiliation.");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "Commands:");
        sender.sendMessage(ChatColor.GRAY + "/nation new <name>, delete <name>, info [nation], list");
        sender.sendMessage(ChatColor.GRAY + "/nation join <nation>, leave");
        sender.sendMessage(ChatColor.GRAY + "/nation deposit|withdraw <amount>, description <text>, color <#hex>, open");
        sender.sendMessage(ChatColor.GRAY + "/nation war|peace|ally <nation>");
        sender.sendMessage(ChatColor.GRAY + "/nation state new|delete|join|leave|info ...");
        sender.sendMessage(ChatColor.GRAY + "/nation state leader|secede|capital|kick|tax <name/player/town> ...  (state leader only)");
        sender.sendMessage(ChatColor.GRAY + "/nation state deposit|withdraw <amount>, description <text>, color <#hex>, open");
        sender.sendMessage(ChatColor.GRAY + "/nation gui - opens the nation menu (also opened by /nation with no arguments)");
    }
}
