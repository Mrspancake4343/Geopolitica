package com.geopolitica.core.command;

import com.geopolitica.core.service.NationServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NationTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "new", "delete", "join", "leave", "deposit", "withdraw", "description",
            "color", "open", "war", "peace", "ally", "state", "gui", "info", "list"
    );
    private static final List<String> STATE_SUBCOMMANDS = List.of(
            "new", "delete", "join", "leave", "leader", "secede", "capital", "kick",
            "tax", "deposit", "withdraw", "description", "color", "open", "info"
    );
    private static final List<String> STATE_NAME_ARG_SUBCOMMANDS = List.of("join", "info");
    private static final List<String> TOWN_NAME_ARG_SUBCOMMANDS = List.of("capital", "kick", "tax");

    private final NationServiceImpl nationService;

    public NationTabCompleter(NationServiceImpl nationService) {
        this.nationService = nationService;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && List.of("join", "info", "war", "peace", "ally").contains(args[0].toLowerCase())) {
            return filter(nationService.getNations().stream().map(n -> n.getName()).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("state")) {
            return filter(STATE_SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("state")) {
            String stateSub = args[1].toLowerCase();
            if (STATE_NAME_ARG_SUBCOMMANDS.contains(stateSub)) {
                return filter(nationService.getStates().stream().map(s -> s.getName()).collect(Collectors.toList()), args[2]);
            }
            if (TOWN_NAME_ARG_SUBCOMMANDS.contains(stateSub)) {
                return filter(nationService.getStates().stream()
                        .flatMap(s -> s.getTowns().stream())
                        .map(t -> t.getName())
                        .distinct()
                        .collect(Collectors.toList()), args[2]);
            }
            if (stateSub.equals("leader")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
            }
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
