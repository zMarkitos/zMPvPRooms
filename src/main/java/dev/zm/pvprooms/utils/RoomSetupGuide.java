package dev.zm.pvprooms.utils;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.hooks.worldguard.WorldGuardHook;
import dev.zm.pvprooms.models.Room;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RoomSetupGuide {

    private RoomSetupGuide() {
    }

    public static List<String> missingRequired(ZMPvPRooms plugin, Room room) {
        List<String> missing = new ArrayList<>(4);
        Optional<WorldGuardHook.RegionBounds> bounds = plugin.getRoomManager().getArenaBounds(room);
        if (!bounds.isPresent()) {
            missing.add("setup_missing_arena");
        }

        if (room.getSpawn1() == null) {
            missing.add("setup_missing_spawn1");
        }
        if (room.getSpawn2() == null) {
            missing.add("setup_missing_spawn2");
        }

        if (bounds.isPresent()
                && ((room.getSpawn1() != null && !isInArenaWorld(room.getSpawn1(), bounds.get()))
                        || (room.getSpawn2() != null && !isInArenaWorld(room.getSpawn2(), bounds.get())))) {
            missing.add("setup_missing_world");
        }
        return missing;
    }

    public static void sendStatus(ZMPvPRooms plugin, CommandSender sender, Room room, boolean includeOptional) {
        if (sender == null || room == null) {
            return;
        }

        List<String> missing = missingRequired(plugin, room);
        if (missing.isEmpty()) {
            sender.sendMessage(plugin.getConfigManager().getMessage("setup_ready")
                    .replace("%room%", room.getName()));
        } else {
            sender.sendMessage(plugin.getConfigManager().getMessage("setup_missing_header")
                    .replace("%room%", room.getName()));
            sendKeys(plugin, sender, room, missing);
        }

        if (!includeOptional) {
            return;
        }
        if (room.getSpectatorSpawn() == null) {
            sendKey(plugin, sender, room, "setup_optional_spectator");
        }
        sendKey(plugin, sender, room, "setup_optional_return_spawn");
    }

    public static void sendMissingRequired(ZMPvPRooms plugin, CommandSender sender, Room room) {
        sendStatus(plugin, sender, room, false);
    }

    private static boolean isInArenaWorld(Location location, WorldGuardHook.RegionBounds bounds) {
        return location.getWorld() != null
                && bounds.getWorldName().equals(location.getWorld().getName());
    }

    private static void sendKeys(ZMPvPRooms plugin, CommandSender sender, Room room, List<String> keys) {
        for (String key : keys) {
            sendKey(plugin, sender, room, key);
        }
    }

    private static void sendKey(ZMPvPRooms plugin, CommandSender sender, Room room, String key) {
        sender.sendMessage(plugin.getConfigManager().getRawMessage(key)
                .replace("%room%", room.getName()));
    }
}
