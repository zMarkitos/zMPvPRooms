package dev.zm.pvprooms.commands;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.managers.RoomManager;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.models.enums.RoomState;
import dev.zm.pvprooms.models.enums.RoomType;
import dev.zm.pvprooms.utils.CC;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.zm.pvprooms.database.SQLiteDatabase;
import java.util.Optional;

public class MainCommand implements CommandExecutor {

    private final ZMPvPRooms plugin;
    private final RoomManager roomManager;

    public MainCommand(ZMPvPRooms plugin, RoomManager roomManager) {
        this.plugin = plugin;
        this.roomManager = roomManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                handleCreate(sender, args);
                break;
            case "delete":
                handleDelete(sender, args);
                break;
            case "edit":
                handleEdit(sender, args);
                break;
            case "join":
                handleJoin(sender, args);
                break;
            case "bet":
                handleBet(sender, args);
                break;
            case "start":
                handleStart(sender, args);
                break;
            case "debug":
                handleDebug(sender, args);
                break;
            case "invite":
                handleInvite(sender, args);
                break;
            case "accept":
                handleAccept(sender, args);
                break;
            case "stop":
                handleStop(sender, args);
                break;
            case "leave":
                handleLeave(sender, args);
                break;
            case "stats":
                handleStats(sender);
                break;
            case "save":
                handleSave(sender, args);
                break;
            case "pos1":
                handleSetTeamSpawn(sender, args, 1);
                break;
            case "pos2":
                handleSetTeamSpawn(sender, args, 2);
                break;
            case "setspectate":
                handleSetSpectateSpawn(sender, args);
                break;
            case "spectate":
                handleSpectate(sender, args);
                break;
            case "setspawn":
                handleSetSpawn(sender);
                break;
            case "reload":
                handleReload(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.create") && !player.hasPermission("zmrooms.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_create"));
            return;
        }

        String roomName = args[1];
        RoomType type = RoomType.NORMAL;

        if (args.length >= 3) {
            try {
                type = RoomType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(plugin.getConfigManager().getMessage("invalid_type"));
                return;
            }
        }

        Room room = roomManager.createRoom(roomName, type);
        if (room == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_already_exists").replace("%room%", roomName));
            return;
        }

        if (plugin.getWorldGuardHook() == null) {
            roomManager.deleteRoom(roomName);
            player.sendMessage(plugin.getConfigManager().getMessage("wg_required"));
            return;
        }

        boolean configuredWithRegion = roomManager.bindRoomToRegion(room, player.getWorld().getName(), roomName);
        if (!configuredWithRegion) {
            roomManager.deleteRoom(roomName);
            player.sendMessage(plugin.getConfigManager().getMessage("wg_region_not_found").replace("%room%", roomName));
            return;
        }

        roomManager.refreshDetectedEntrances(room);
        roomManager.saveRooms();

        String created = plugin.getConfigManager().getMessage("room_created")
                .replace("%room%", roomName)
                .replace("%type%", type.name());
        created += " " + plugin.getConfigManager().getMessage("wg_region_linked").replace("%room%", roomName);
        player.sendMessage(created);
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmrooms.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getConfigManager().getMessage("usage_delete"));
            return;
        }

        String roomName = args[1];
        if (!roomManager.getRoom(roomName).isPresent()) {
            sender.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", roomName));
            return;
        }

        roomManager.deleteRoom(roomName);
        sender.sendMessage(plugin.getConfigManager().getMessage("room_deleted").replace("%room%", roomName));
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_join"));
            return;
        }

        String roomName = args[1];
        Optional<Room> room = roomManager.getRoom(roomName);
        if (room.isPresent()) {
            if (room.get().getType() == RoomType.NORMAL) {
                player.sendMessage(plugin.getConfigManager().getMessage("normal_auto_join"));
                return;
            }
            plugin.getMatchManager().joinRoom(player, room.get());
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", roomName));
        }
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            plugin.getEditorManager().openRoomsList(player);
            return;
        }

        String roomName = args[1];
        Optional<Room> room = roomManager.getRoom(roomName);
        if (!room.isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", roomName));
            return;
        }
        plugin.getEditorManager().openRoomMenu(player, room.get().getName());
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_start"));
            return;
        }

        String roomName = args[1];

        Optional<Room> room = roomManager.getRoom(roomName);
        if (!room.isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", roomName));
            return;
        }
        plugin.getMatchManager().startClanMatch(player, room.get());
    }

    private void handleInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_invite"));
            return;
        }
        Optional<Room> room = roomManager.getRoom(args[1]);
        if (!room.isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", args[1]));
            return;
        }
        plugin.getMatchManager().inviteClan(player, room.get(), args[2]);
    }

    private void handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_accept"));
            return;
        }
        Optional<Room> room = roomManager.getRoom(args[1]);
        if (!room.isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", args[1]));
            return;
        }
        plugin.getMatchManager().acceptClanInvite(player, room.get());
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_stop"));
            return;
        }
        Optional<Room> room = roomManager.getRoom(args[1]);
        if (!room.isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", args[1]));
            return;
        }
        plugin.getMatchManager().stopClanLobby(player, room.get());
    }

    private void handleLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }
        Player player = (Player) sender;
        Optional<Room> room = plugin.getRoomManager().getRoomByPlayer(player);
        if (room.isPresent()) {
            plugin.getMatchManager().leaveRoom(player, room.get(), true);
            return;
        }

        for (Room value : plugin.getRoomManager().getRooms().values()) {
            if (!value.isInArena(player.getLocation())) {
                continue;
            }
            if (value.getState() == RoomState.PLAYING
                    || value.getState() == RoomState.STARTING
                    || value.getState() == RoomState.ENDING) {
                player.teleport(plugin.getReturnSpawn());
                if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                }
                player.sendMessage(plugin.getConfigManager().getMessage("spectate_left"));
                return;
            }
        }
        player.sendMessage(plugin.getConfigManager().getMessage("not_in_room"));
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmrooms.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().getMessage("usage_debug"));
            return;
        }

        String action = args[1].toLowerCase();
        String roomName = args[2];

        Optional<Room> room = roomManager.getRoom(roomName);
        if (!room.isPresent()) {
            sender.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", roomName));
            return;
        }

        if (action.equals("forcestart")) {
            plugin.getMatchManager().forceStart(room.get());
            sender.sendMessage(plugin.getConfigManager().getMessage("debug_force_success").replace("%room%", roomName));
            return;
        }

        if (action.equals("addbot")) {
            if (args.length < 4) {
                sender.sendMessage(plugin.getConfigManager().getMessage("usage_debug"));
                return;
            }

            Player target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                sender.sendMessage(plugin.getConfigManager().getMessage("player_not_online")
                        .replace("%player%", args[3]));
                return;
            }

            boolean success = plugin.getMatchManager().forceJoinRoom(target, room.get());
            if (!success) {
                sender.sendMessage(plugin.getConfigManager().getMessage("debug_addbot_failed")
                        .replace("%player%", target.getName())
                        .replace("%room%", room.get().getName()));
                return;
            }

            sender.sendMessage(plugin.getConfigManager().getMessage("debug_addbot_success")
                    .replace("%player%", target.getName())
                    .replace("%room%", room.get().getName()));
        }
    }

    private void handleBet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.bet")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        player.sendMessage(plugin.getConfigManager().getMessage("bets_coming_soon"));
    }

    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.stats")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (plugin.getDatabase() == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("stats_unavailable"));
            return;
        }

        // Run the stat query asynchronously so the main thread isn't blocked
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            SQLiteDatabase.StatRow row = plugin.getDatabase().loadStats(player.getUniqueId().toString());
            // Send results back on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("stats_header")));
                player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("stats_title")));
                player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("stats_normal")
                        .replace("%wins%", String.valueOf(row.normalWins))
                        .replace("%losses%", String.valueOf(row.normalLosses))));
                player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("stats_clan")
                        .replace("%wins%", String.valueOf(row.clanWins))
                        .replace("%losses%", String.valueOf(row.clanLosses))));
                player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("stats_footer")));
            });
        });
    }

    private void handleSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_save"));
            return;
        }

        String mode = args[1].toLowerCase();
        if (!mode.equals("positions") && !mode.equals("arena") && !mode.equals("all")) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_save"));
            return;
        }

        Optional<Room> optionalRoom = Optional.empty();
        if (args.length >= 3) {
            optionalRoom = roomManager.getRoom(args[2]);
        } else {
            optionalRoom = roomManager.getRoomByPlayer(player);
        }

        if (!optionalRoom.isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("save_room_required"));
            return;
        }

        Room room = optionalRoom.get();

        if (mode.equals("arena") || mode.equals("all")) {
            if (plugin.getWorldGuardHook() == null) {
                player.sendMessage(plugin.getConfigManager().getMessage("wg_required"));
                return;
            }
            if (!roomManager.refreshArenaFromRegion(room)) {
                player.sendMessage(plugin.getConfigManager().getMessage("wg_region_not_found")
                        .replace("%room%",
                                room.getArenaRegionName() == null ? room.getName() : room.getArenaRegionName()));
                return;
            }
        }

        int detected = room.getDetectedEntranceBlocks();
        if (mode.equals("positions") || mode.equals("all")) {
            detected = roomManager.refreshDetectedEntrances(room);
        }

        roomManager.saveRooms();
        player.sendMessage(plugin.getConfigManager().getMessage("save_success")
                .replace("%mode%", mode)
                .replace("%room%", room.getName())
                .replace("%detected%", String.valueOf(detected)));
    }

    private void handleSetTeamSpawn(CommandSender sender, String[] args, int team) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_pos"));
            return;
        }

        Optional<Room> optionalRoom = roomManager.getRoom(args[1]);
        if (!optionalRoom.isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", args[1]));
            return;
        }

        Room room = optionalRoom.get();
        Location location = player.getLocation().clone();
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());

        Optional<dev.zm.pvprooms.hooks.worldguard.WorldGuardHook.RegionBounds> bounds = roomManager
                .getArenaBounds(room);
        if (bounds.isPresent() && !location.getWorld().getName().equalsIgnoreCase(bounds.get().getWorldName())) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_world_mismatch")
                    .replace("%world%", bounds.get().getWorldName()));
            return;
        }

        if (team == 1) {
            room.setSpawn1(location);
        } else {
            room.setSpawn2(location);
        }

        roomManager.saveRooms();
        player.sendMessage(plugin.getConfigManager().getMessage("set_pos_success")
                .replace("%room%", room.getName())
                .replace("%team%", String.valueOf(team)));
    }

    private void handleSetSpectateSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_setspectate"));
            return;
        }

        Optional<Room> optionalRoom = roomManager.getRoom(args[1]);
        if (!optionalRoom.isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", args[1]));
            return;
        }

        Room room = optionalRoom.get();
        Location location = player.getLocation().clone();
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());

        Optional<dev.zm.pvprooms.hooks.worldguard.WorldGuardHook.RegionBounds> bounds = roomManager
                .getArenaBounds(room);
        if (bounds.isPresent() && !location.getWorld().getName().equalsIgnoreCase(bounds.get().getWorldName())) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_world_mismatch")
                    .replace("%world%", bounds.get().getWorldName()));
            return;
        }

        room.setSpectatorSpawn(location);
        roomManager.saveRooms();

        player.sendMessage(plugin.getConfigManager().getMessage("set_spectate_success")
                .replace("%room%", room.getName()));
    }

    private void handleSpectate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage_spectate"));
            return;
        }

        if (args[1].equalsIgnoreCase("leave")) {
            Optional<Room> currentRoom = plugin.getRoomManager().getRoomByPlayer(player);
            if (!currentRoom.isPresent() || !currentRoom.get().getSpectators().contains(player.getUniqueId())) {
                player.sendMessage(plugin.getConfigManager().getMessage("spectate_not_in_mode"));
                return;
            }

            plugin.getMatchManager().removeSpectator(player, currentRoom.get(), true);
            player.sendMessage(plugin.getConfigManager().getMessage("spectate_left"));
            return;
        }

        Optional<Room> optionalRoom = roomManager.getRoom(args[1]);
        if (!optionalRoom.isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_not_found").replace("%room%", args[1]));
            return;
        }

        Room room = optionalRoom.get();
        if (room.getSpectatorSpawn() == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("spectate_spawn_missing"));
            return;
        }

        if (room.getState() != RoomState.PLAYING && room.getState() != RoomState.STARTING) {
            player.sendMessage(plugin.getConfigManager().getMessage("spectate_room_not_active"));
            return;
        }

        if (plugin.getRoomManager().getRoomByPlayer(player).isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("already_in_room"));
            return;
        }

        boolean spectating = plugin.getMatchManager().addSpectator(player, room);
        if (!spectating) {
            player.sendMessage(plugin.getConfigManager().getMessage("spectate_spawn_missing"));
            return;
        }
        player.sendMessage(plugin.getConfigManager().getMessage("spectate_joined")
                .replace("%room%", room.getName()));
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("only_players"));
            return;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("zmrooms.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        plugin.setReturnSpawn(player.getLocation().clone());
        player.sendMessage(plugin.getConfigManager().getMessage("setspawn_success"));
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zmrooms.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }

        if (args.length != 1) {
            sender.sendMessage(plugin.getConfigManager().getMessage("usage_reload"));
            return;
        }

        plugin.reloadAll();
        sender.sendMessage(plugin.getConfigManager().getMessage("reload_success"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_header"));
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_title"));
        if (sender.hasPermission("zmrooms.create") || sender.hasPermission("zmrooms.admin")) {
            sender.sendMessage(plugin.getConfigManager().getRawMessage("help_create"));
            sender.sendMessage(plugin.getConfigManager().getRawMessage("help_delete"));
            sender.sendMessage(plugin.getConfigManager().getRawMessage("help_edit"));
            sender.sendMessage(plugin.getConfigManager().getRawMessage("help_pos1"));
            sender.sendMessage(plugin.getConfigManager().getRawMessage("help_pos2"));
            sender.sendMessage(plugin.getConfigManager().getRawMessage("help_setspectate"));
            sender.sendMessage(plugin.getConfigManager().getRawMessage("help_setspawn"));
            sender.sendMessage(plugin.getConfigManager().getRawMessage("help_save"));
            sender.sendMessage(plugin.getConfigManager().getRawMessage("help_reload"));
            sender.sendMessage(plugin.getConfigManager().getRawMessage("help_start"));
        }
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_invite"));
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_accept"));
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_stop"));
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_leave"));
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_join"));
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_spectate"));
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_bet"));
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_stats"));
        sender.sendMessage(plugin.getConfigManager().getRawMessage("help_footer"));
    }
}
