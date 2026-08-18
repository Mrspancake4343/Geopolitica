package com.geopolitica.core.command;

import com.geopolitica.core.GeopoliticaPlugin;
import com.geopolitica.core.model.NationImpl;
import com.geopolitica.core.model.StateImpl;
import com.geopolitica.core.model.TownImpl;
import com.geopolitica.core.service.NationServiceImpl;
import com.geopolitica.core.service.TownServiceImpl;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class GAdminCommand implements CommandExecutor {

    private static final String PREFIX = ChatColor.GOLD + "[Geopolitica] " + ChatColor.RESET;

    private final GeopoliticaPlugin plugin;
    private final TownServiceImpl townService;
    private final NationServiceImpl nationService;

    public GAdminCommand(GeopoliticaPlugin plugin, TownServiceImpl townService, NationServiceImpl nationService) {
        this.plugin = plugin;
        this.townService = townService;
        this.nationService = nationService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX + "Usage: /gadmin <reload|freeze|unfreeze|delete|setbank|nation> ...");
            return true;
        }
        try {
            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    plugin.reloadPluginConfig();
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration reloaded.");
                }
                case "freeze" -> setFrozen(sender, args, true);
                case "unfreeze" -> setFrozen(sender, args, false);
                case "delete" -> {
                    TownImpl town = requireTown(args, 1);
                    townService.disbandTown(town);
                    sender.sendMessage(PREFIX + ChatColor.YELLOW + "Deleted town '" + town.getName() + "'.");
                }
                case "setbank" -> {
                    if (args.length < 3) {
                        sender.sendMessage(PREFIX + "Usage: /gadmin setbank <town> <amount>");
                        return true;
                    }
                    TownImpl town = requireTown(args, 1);
                    double amount = Double.parseDouble(args[2]);
                    town.withdrawBank(town.getBankBalance());
                    town.depositBank(amount);
                    townService.saveTown(town);
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Set " + town.getName() + "'s bank to " + amount + ".");
                }
                case "nation" -> handleNation(sender, args);
                case "state" -> handleState(sender, args);
                default -> sender.sendMessage(PREFIX + "Usage: /gadmin <reload|freeze|unfreeze|delete|setbank|nation|state> ...");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "That is not a valid number.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + e.getMessage());
        }
        return true;
    }

    private void handleNation(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /gadmin nation <freeze|unfreeze|delete|setbank> <name> [amount]");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "freeze" -> {
                NationImpl nation = requireNation(args, 2);
                nation.setFrozen(true);
                nationService.saveNation(nation);
                sender.sendMessage(PREFIX + ChatColor.GREEN + nation.getName() + " is now frozen.");
            }
            case "unfreeze" -> {
                NationImpl nation = requireNation(args, 2);
                nation.setFrozen(false);
                nationService.saveNation(nation);
                sender.sendMessage(PREFIX + ChatColor.GREEN + nation.getName() + " is now unfrozen.");
            }
            case "delete" -> {
                NationImpl nation = requireNation(args, 2);
                nationService.disbandNation(nation);
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Deleted nation '" + nation.getName() + "'.");
            }
            case "setbank" -> {
                if (args.length < 4) {
                    sender.sendMessage(PREFIX + "Usage: /gadmin nation setbank <name> <amount>");
                    return;
                }
                NationImpl nation = requireNation(args, 2);
                double amount = Double.parseDouble(args[3]);
                nation.withdrawBank(nation.getBankBalance());
                nation.depositBank(amount);
                nationService.saveNation(nation);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Set " + nation.getName() + "'s bank to " + amount + ".");
            }
            default -> sender.sendMessage(PREFIX + "Usage: /gadmin nation <freeze|unfreeze|delete|setbank> <name> [amount]");
        }
    }

    private void handleState(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /gadmin state <freeze|unfreeze|delete|setbank> <name> [amount]");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "freeze" -> {
                StateImpl state = requireState(args, 2);
                state.setFrozen(true);
                nationService.saveState(state);
                sender.sendMessage(PREFIX + ChatColor.GREEN + state.getName() + " is now frozen.");
            }
            case "unfreeze" -> {
                StateImpl state = requireState(args, 2);
                state.setFrozen(false);
                nationService.saveState(state);
                sender.sendMessage(PREFIX + ChatColor.GREEN + state.getName() + " is now unfrozen.");
            }
            case "delete" -> {
                StateImpl state = requireState(args, 2);
                nationService.disbandState(state);
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Deleted state '" + state.getName() + "'.");
            }
            case "setbank" -> {
                if (args.length < 4) {
                    sender.sendMessage(PREFIX + "Usage: /gadmin state setbank <name> <amount>");
                    return;
                }
                StateImpl state = requireState(args, 2);
                double amount = Double.parseDouble(args[3]);
                state.withdrawBank(state.getBankBalance());
                state.depositBank(amount);
                nationService.saveState(state);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Set " + state.getName() + "'s bank to " + amount + ".");
            }
            default -> sender.sendMessage(PREFIX + "Usage: /gadmin state <freeze|unfreeze|delete|setbank> <name> [amount]");
        }
    }

    private void setFrozen(CommandSender sender, String[] args, boolean frozen) {
        TownImpl town = requireTown(args, 1);
        town.setFrozen(frozen);
        townService.saveTown(town);
        sender.sendMessage(PREFIX + ChatColor.GREEN + town.getName() + " is now " + (frozen ? "frozen." : "unfrozen."));
    }

    private TownImpl requireTown(String[] args, int index) {
        if (args.length <= index) {
            throw new IllegalArgumentException("Usage: /gadmin " + args[0] + " <town>");
        }
        return townService.getTownImpl(args[index])
                .orElseThrow(() -> new IllegalArgumentException("No town named '" + args[index] + "' exists"));
    }

    private NationImpl requireNation(String[] args, int index) {
        if (args.length <= index) {
            throw new IllegalArgumentException("Usage: /gadmin nation " + args[1] + " <nation>");
        }
        return nationService.getNationImpl(args[index])
                .orElseThrow(() -> new IllegalArgumentException("No nation named '" + args[index] + "' exists"));
    }

    private StateImpl requireState(String[] args, int index) {
        if (args.length <= index) {
            throw new IllegalArgumentException("Usage: /gadmin state " + args[1] + " <state>");
        }
        return nationService.getStateImpl(args[index])
                .orElseThrow(() -> new IllegalArgumentException("No state named '" + args[index] + "' exists"));
    }
}
