package dev.zm.pvprooms.commands;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.models.enums.RoomType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MainTabCompleter implements TabCompleter {

    private final ZMPvPRooms plugin;

    public MainTabCompleter(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(
                    Arrays.asList("join", "leave", "spectate", "bet", "stats", "start", "invite", "accept", "stop"));
            if (sender.hasPermission("zmrooms.admin") || sender.hasPermission("zmrooms.create")) {
                subCommands.addAll(Arrays.asList("create", "delete", "edit", "pos1", "pos2", "setspectate", "setspawn",
                        "reload", "save", "start", "debug"));
            }
            return StringUtil.copyPartialMatches(args[0], subCommands, completions);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "delete":
                case "edit":
                case "join":
                case "start":
                case "accept":
                case "stop":
                case "bet":
                    return StringUtil.copyPartialMatches(args[1],
                            new ArrayList<>(plugin.getRoomManager().getRooms().keySet()), completions);
                case "spectate":
                    completions.add("leave");
                    completions.addAll(plugin.getRoomManager().getRooms().keySet());
                    return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
                case "invite":
                    return StringUtil.copyPartialMatches(args[1],
                            new ArrayList<>(plugin.getRoomManager().getRooms().keySet()), completions);
                case "pos1":
                case "pos2":
                case "setspectate":
                    List<String> rooms = new ArrayList<>(plugin.getRoomManager().getRooms().keySet());
                    return StringUtil.copyPartialMatches(args[1], rooms, completions);
                case "create":
                    completions.add("<nombre>");
                    return completions;
                case "debug":
                    if (sender.hasPermission("zmrooms.admin")) {
                        completions.add("forcestart");
                        completions.add("addbot");
                        return completions;
                    }
                    break;
                case "save":
                    if (sender.hasPermission("zmrooms.admin")) {
                        completions.add("positions");
                        completions.add("arena");
                        completions.add("all");
                        return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
                    }
                    break;
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "create":
                    List<String> types = Arrays.stream(RoomType.values()).map(Enum::name).collect(Collectors.toList());
                    return StringUtil.copyPartialMatches(args[2], types, completions);
                case "start":
                    return Collections.emptyList();
                case "invite":
                    if (sender instanceof Player && plugin.getClanProvider() != null) {
                        Player player = (Player) sender;
                        String ownClan = plugin.getClanProvider().getClanName(player);
                        List<String> clans = Bukkit.getOnlinePlayers().stream()
                                .map(plugin.getClanProvider()::getClanName)
                                .filter(name -> name != null && !name.trim().isEmpty())
                                .distinct()
                                .filter(name -> ownClan == null || !name.equalsIgnoreCase(ownClan))
                                .collect(Collectors.toList());
                        if (!clans.isEmpty()) {
                            return StringUtil.copyPartialMatches(args[2], clans, completions);
                        }
                    }
                    return Collections.singletonList("<clan>");
                case "debug":
                    if (args[1].equalsIgnoreCase("forcestart") || args[1].equalsIgnoreCase("addbot")) {
                        List<String> rooms = new ArrayList<>(plugin.getRoomManager().getRooms().keySet());
                        return StringUtil.copyPartialMatches(args[2], rooms, completions);
                    }
                    break;
                case "save":
                    if (args[1].equalsIgnoreCase("positions") || args[1].equalsIgnoreCase("arena")
                            || args[1].equalsIgnoreCase("all")) {
                        List<String> rooms = new ArrayList<>(plugin.getRoomManager().getRooms().keySet());
                        return StringUtil.copyPartialMatches(args[2], rooms, completions);
                    }
                    break;
                case "pos1":
                case "pos2":
                case "setspectate":
                case "spectate":
                    List<String> rooms = new ArrayList<>(plugin.getRoomManager().getRooms().keySet());
                    return StringUtil.copyPartialMatches(args[2], rooms, completions);
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("addbot")) {
            List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[3], players, completions);
        }

        return Collections.emptyList();
    }
}
