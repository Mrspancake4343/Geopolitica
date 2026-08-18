package com.geopolitica.core.command;

import com.geopolitica.core.service.TownServiceImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TownTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "new", "delete", "claim", "unclaim", "join", "leave", "kick", "rank",
            "deposit", "withdraw", "sethome", "home", "description", "color", "open", "pvp", "gui", "info", "list"
    );
    private static final List<String> RANK_SUBCOMMANDS = List.of("create", "delete", "set", "assign");

    private final TownServiceImpl townService;

    public TownTabCompleter(TownServiceImpl townService) {
        this.townService = townService;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("info"))) {
            return filter(townService.getTowns().stream().map(t -> t.getName()).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rank")) {
            return filter(RANK_SUBCOMMANDS, args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
