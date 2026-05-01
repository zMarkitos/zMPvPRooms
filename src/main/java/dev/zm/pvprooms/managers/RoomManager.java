package dev.zm.pvprooms.managers;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.hooks.worldguard.WorldGuardHook;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.models.enums.BetMode;
import dev.zm.pvprooms.models.enums.RoomType;
import dev.zm.pvprooms.utils.ArenaBoundaryScanner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RoomManager {

    private final ZMPvPRooms plugin;
    private final Map<String, Room> rooms;
    private final File roomsFile;
    private FileConfiguration roomsConfig;

    public RoomManager(ZMPvPRooms plugin) {
        this.plugin = plugin;
        this.rooms = new HashMap<>();
        this.roomsFile = new File(plugin.getDataFolder(), "rooms.yml");
        loadRooms();
    }

    public void loadRooms() {
        if (!roomsFile.exists()) {
            try {
                roomsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        roomsConfig = YamlConfiguration.loadConfiguration(roomsFile);
        rooms.clear();

        ConfigurationSection section = roomsConfig.getConfigurationSection("rooms");
        if (section == null) {
            return;
        }

        for (String name : section.getKeys(false)) {
            String typeStr = section.getString(name + ".type", "NORMAL");
            RoomType type = parseEnum(RoomType.class, typeStr, RoomType.NORMAL, "room type", name);
            Room room = new Room(name, type);

            room.setKeepInventory(section.getBoolean(name + ".keepInventory", false));
            room.setKeepExp(section.getBoolean(name + ".keepExp", false));
            room.setDoorMaterial(parseMaterial(section.getString(name + ".doorMaterial", "GLASS"), name));
            room.setPlayersPerTeam(section.getInt(name + ".playersPerTeam", 1));
            room.setMinPlayersToStart(section.getInt(name + ".minPlayers", room.getPlayersPerTeam() * 2));
            room.setDoorOpenDelay(section.getInt(name + ".doorOpenDelay", 10));
            boolean legacyActions = section.getBoolean(name + ".useActionBarsAndTitles", true);
            room.setTitlesEnabled(section.getBoolean(name + ".messages.titles", legacyActions));
            room.setActionBarEnabled(section.getBoolean(name + ".messages.actionBar", legacyActions));
            room.setChatEnabled(section.getBoolean(name + ".messages.chat", true));
            room.setBetMode(parseEnum(BetMode.class, section.getString(name + ".betMode", "OPTIONAL"), BetMode.OPTIONAL,
                    "bet mode", name));
            room.setMaxDuelTime(section.getInt(name + ".maxDuelTime", 0));
            room.setPostMatchTeleportDelay(section.getInt(name + ".postMatchTeleportDelay", 8));
            room.setDetectedEntranceBlocks(section.getInt(name + ".detectedEntranceBlocks", 0));

            room.setArenaRegionName(section.getString(name + ".region.name"));
            room.setArenaWorldName(section.getString(name + ".region.world"));

            room.setSpawn1(getLocation(section, name + ".spawn1"));
            room.setSpawn2(getLocation(section, name + ".spawn2"));
            room.setSpectatorSpawn(getLocation(section, name + ".spectatorSpawn"));

            // Backward compatibility with old saved coordinates.
            Location legacyArenaPos1 = getLocation(section, name + ".arenaPos1");
            Location legacyArenaPos2 = getLocation(section, name + ".arenaPos2");
            if (legacyArenaPos1 != null && legacyArenaPos2 != null) {
                room.setArenaPos1(legacyArenaPos1);
                room.setArenaPos2(legacyArenaPos2);
            }

            if (room.getArenaRegionName() != null && room.getArenaWorldName() != null) {
                refreshArenaFromRegion(room);
            }

            rooms.put(name.toLowerCase(), room);
        }
        plugin.getLogger().info("Loaded " + rooms.size() + " rooms from rooms.yml");
    }

    public void saveRooms() {
        roomsConfig.set("rooms", null);
        for (Room room : rooms.values()) {
            String path = "rooms." + room.getName();
            roomsConfig.set(path + ".type", room.getType().name());
            roomsConfig.set(path + ".keepInventory", room.isKeepInventory());
            roomsConfig.set(path + ".keepExp", room.isKeepExp());
            roomsConfig.set(path + ".doorMaterial", room.getDoorMaterial().name());
            roomsConfig.set(path + ".playersPerTeam", room.getPlayersPerTeam());
            roomsConfig.set(path + ".minPlayers", room.getMinPlayersToStart());
            roomsConfig.set(path + ".doorOpenDelay", room.getDoorOpenDelay());
            roomsConfig.set(path + ".useActionBarsAndTitles", room.isUseActionBarsAndTitles()); // legacy
            roomsConfig.set(path + ".messages.titles", room.isTitlesEnabled());
            roomsConfig.set(path + ".messages.actionBar", room.isActionBarEnabled());
            roomsConfig.set(path + ".messages.chat", room.isChatEnabled());
            roomsConfig.set(path + ".betMode", room.getBetMode().name());
            roomsConfig.set(path + ".maxDuelTime", room.getMaxDuelTime());
            roomsConfig.set(path + ".postMatchTeleportDelay", room.getPostMatchTeleportDelay());
            roomsConfig.set(path + ".detectedEntranceBlocks", room.getDetectedEntranceBlocks());

            roomsConfig.set(path + ".region.name", room.getArenaRegionName());
            roomsConfig.set(path + ".region.world", room.getArenaWorldName());

            setLocation(roomsConfig, path + ".spawn1", room.getSpawn1());
            setLocation(roomsConfig, path + ".spawn2", room.getSpawn2());
            setLocation(roomsConfig, path + ".spectatorSpawn", room.getSpectatorSpawn());

            // Cleanup obsolete keys from old format.
            roomsConfig.set(path + ".arenaPos1", null);
            roomsConfig.set(path + ".arenaPos2", null);
            roomsConfig.set(path + ".barrierPos1", null);
            roomsConfig.set(path + ".barrierPos2", null);
            roomsConfig.set(path + ".barriers", null);
            roomsConfig.set(path + ".coordinates", null);
        }
        try {
            roomsConfig.save(roomsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Optional<WorldGuardHook.RegionBounds> getArenaBounds(Room room) {
        WorldGuardHook hook = plugin.getWorldGuardHook();
        if (hook != null && room.getArenaRegionName() != null && room.getArenaWorldName() != null) {
            Optional<WorldGuardHook.RegionBounds> bounds = hook.getRegionBounds(room.getArenaWorldName(), room.getArenaRegionName());
            if (bounds.isPresent()) {
                return bounds;
            }
        }

        if (room.getArenaPos1() == null || room.getArenaPos2() == null
                || room.getArenaPos1().getWorld() == null || room.getArenaPos2().getWorld() == null
                || !room.getArenaPos1().getWorld().equals(room.getArenaPos2().getWorld())) {
            return Optional.empty();
        }

        return Optional.of(new WorldGuardHook.RegionBounds(
                room.getArenaPos1().getWorld().getName(),
                room.getArenaPos1().getBlockX(), room.getArenaPos1().getBlockY(), room.getArenaPos1().getBlockZ(),
                room.getArenaPos2().getBlockX(), room.getArenaPos2().getBlockY(), room.getArenaPos2().getBlockZ()
        ));
    }

    public boolean bindRoomToRegion(Room room, String worldName, String regionName) {
        WorldGuardHook hook = plugin.getWorldGuardHook();
        if (hook == null) {
            return false;
        }

        String normalized = hook.normalizeRegionId(regionName);
        if (normalized.isEmpty()) {
            return false;
        }

        Optional<WorldGuardHook.RegionBounds> bounds = hook.getRegionBounds(worldName, normalized);
        if (!bounds.isPresent()) {
            return false;
        }

        room.setArenaRegionName(normalized);
        room.setArenaWorldName(worldName);
        applyBoundsToRoom(room, bounds.get());
        return true;
    }

    public boolean refreshArenaFromRegion(Room room) {
        WorldGuardHook hook = plugin.getWorldGuardHook();
        if (hook == null || room.getArenaRegionName() == null || room.getArenaWorldName() == null) {
            return false;
        }

        Optional<WorldGuardHook.RegionBounds> bounds = hook.getRegionBounds(room.getArenaWorldName(), room.getArenaRegionName());
        if (!bounds.isPresent()) {
            return false;
        }

        applyBoundsToRoom(room, bounds.get());
        return true;
    }

    public int refreshDetectedEntrances(Room room) {
        Optional<WorldGuardHook.RegionBounds> bounds = getArenaBounds(room);
        if (!bounds.isPresent()) {
            room.setDetectedEntranceBlocks(0);
            return 0;
        }

        World world = Bukkit.getWorld(bounds.get().getWorldName());
        if (world == null) {
            room.setDetectedEntranceBlocks(0);
            return 0;
        }

        List<ArenaBoundaryScanner.BlockPoint> points = ArenaBoundaryScanner.detectEntranceGaps(world, bounds.get());
        room.setDetectedEntranceBlocks(points.size());
        return points.size();
    }

    public boolean isConfigured(Room room) {
        return hasArena(room) && room.getSpawn1() != null && room.getSpawn2() != null;
    }

    public boolean hasArena(Room room) {
        return getArenaBounds(room).isPresent();
    }

    private void applyBoundsToRoom(Room room, WorldGuardHook.RegionBounds bounds) {
        World world = Bukkit.getWorld(bounds.getWorldName());
        if (world == null) {
            return;
        }
        room.setArenaPos1(bounds.toMinLocation(world));
        room.setArenaPos2(bounds.toMaxLocation(world));
    }

    private Location getLocation(ConfigurationSection config, String path) {
        if (!config.contains(path)) {
            return null;
        }
        String world = config.getString(path + ".world");
        if (world == null) {
            return null;
        }
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw");
        float pitch = (float) config.getDouble(path + ".pitch");
        return new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    private void setLocation(FileConfiguration config, String path, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());
    }

    public Room createRoom(String name, RoomType type) {
        if (rooms.containsKey(name.toLowerCase())) {
            return null;
        }
        Room room = new Room(name, type);
        rooms.put(name.toLowerCase(), room);
        saveRooms();
        return room;
    }

    public void deleteRoom(String name) {
        rooms.remove(name.toLowerCase());
        saveRooms();
    }

    public Optional<Room> getRoom(String name) {
        return Optional.ofNullable(rooms.get(name.toLowerCase()));
    }

    public Optional<Room> getRoomByPlayer(Player player) {
        return rooms.values().stream()
                .filter(room -> room.getPlayers().contains(player.getUniqueId()) || room.getSpectators().contains(player.getUniqueId()))
                .findFirst();
    }

    public Map<String, Room> getRooms() {
        return rooms;
    }

    private Material parseMaterial(String value, String roomName) {
        if (value == null) {
            return Material.GLASS;
        }
        Material material = Material.matchMaterial(value.toUpperCase());
        if (material == null) {
            plugin.getLogger().warning("Invalid material '" + value + "' in room '" + roomName + "'. Using GLASS.");
            return Material.GLASS;
        }
        return material;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, E fallback, String label, String room) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(
                    "Invalid " + label + " '" + value + "' in room '" + room + "'. Using " + fallback.name() + ".");
            return fallback;
        }
    }
}
