package dev.zm.pvprooms.models;

import dev.zm.pvprooms.models.enums.RoomState;
import dev.zm.pvprooms.models.enums.RoomType;
import dev.zm.pvprooms.utils.CC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import dev.zm.pvprooms.models.enums.BetMode;

public class Room {

    private String name;
    private RoomType type;
    private RoomState state;

    private Location spawn1;
    private Location spawn2;
    private Location spectatorSpawn;

    private Location arenaPos1;
    private Location arenaPos2;
    private String arenaRegionName;
    private String arenaWorldName;

    private Location barrierPos1;
    private Location barrierPos2;
    private final List<DoorRegion> doorRegions;

    private final List<UUID> players;
    private final List<UUID> spectators;

    private boolean keepInventory;
    private boolean keepExp;
    private boolean combatLog;
    private boolean preventBlocks;
    private Material barrierMaterial;
    private final List<PotionEffect> effects;

    private Material doorMaterial;
    private int playersPerTeam;
    private int minPlayersToStart;
    private int doorOpenDelay;
    private boolean titlesEnabled;
    private boolean actionBarEnabled;
    private boolean chatEnabled;
    private BetMode betMode;
    private int maxDuelTime;
    private int postMatchTeleportDelay;
    private int detectedEntranceBlocks;

    public Room(String name, RoomType type) {
        this.name = name;
        this.type = type;
        this.state = RoomState.WAITING;
        this.players = new ArrayList<>();
        this.spectators = new ArrayList<>();

        this.keepInventory = false;
        this.keepExp = false;
        this.combatLog = true;
        this.preventBlocks = true;
        this.barrierMaterial = Material.BARRIER;
        this.effects = new ArrayList<>();
        this.doorRegions = new ArrayList<>();

        this.doorMaterial = Material.GLASS;
        this.playersPerTeam = 1;
        this.minPlayersToStart = 2;
        this.doorOpenDelay = 10;
        this.titlesEnabled = true;
        this.actionBarEnabled = true;
        this.chatEnabled = true;
        this.betMode = BetMode.OPTIONAL;
        this.maxDuelTime = 0;
        this.postMatchTeleportDelay = 8;
        this.detectedEntranceBlocks = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RoomType getType() {
        return type;
    }

    public void setType(RoomType type) {
        this.type = type;
    }

    public RoomState getState() {
        return state;
    }

    public void setState(RoomState state) {
        this.state = state;
    }

    public Location getSpawn1() {
        return spawn1;
    }

    public void setSpawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    public Location getSpawn2() {
        return spawn2;
    }

    public void setSpawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn;
    }

    public void setSpectatorSpawn(Location spectatorSpawn) {
        this.spectatorSpawn = spectatorSpawn;
    }

    public Location getArenaPos1() {
        return arenaPos1;
    }

    public void setArenaPos1(Location arenaPos1) {
        this.arenaPos1 = arenaPos1;
    }

    public Location getArenaPos2() {
        return arenaPos2;
    }

    public void setArenaPos2(Location arenaPos2) {
        this.arenaPos2 = arenaPos2;
    }

    public String getArenaRegionName() {
        return arenaRegionName;
    }

    public void setArenaRegionName(String arenaRegionName) {
        this.arenaRegionName = arenaRegionName;
    }

    public String getArenaWorldName() {
        return arenaWorldName;
    }

    public void setArenaWorldName(String arenaWorldName) {
        this.arenaWorldName = arenaWorldName;
    }

    public Location getBarrierPos1() {
        return barrierPos1;
    }

    public void setBarrierPos1(Location barrierPos1) {
        this.barrierPos1 = barrierPos1 == null ? null : barrierPos1.clone();
    }

    public Location getBarrierPos2() {
        return barrierPos2;
    }

    public void setBarrierPos2(Location barrierPos2) {
        this.barrierPos2 = barrierPos2 == null ? null : barrierPos2.clone();
    }

    public void addDoorRegion(Location pos1, Location pos2) {
        addDoorRegion(pos1, pos2, new ArrayList<>());
    }

    public void addDoorRegion(Location pos1, Location pos2, List<BlockCoordinate> blockCoordinates) {
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null) {
            return;
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return;
        }

        DoorRegion newRegion = new DoorRegion(pos1, pos2, blockCoordinates);
        doorRegions.removeIf(region -> region.sameBounds(newRegion));
        doorRegions.add(newRegion);
    }

    public void clearDoorRegions() {
        doorRegions.clear();
    }

    public List<DoorRegion> getDoorRegions() {
        return new ArrayList<>(doorRegions);
    }

    public List<UUID> getPlayers() {
        return players;
    }

    public List<UUID> getSpectators() {
        return spectators;
    }

    public boolean isKeepInventory() {
        return keepInventory;
    }

    public void setKeepInventory(boolean keepInventory) {
        this.keepInventory = keepInventory;
        this.keepExp = keepInventory;
    }

    public boolean isKeepExp() {
        return keepExp;
    }

    public void setKeepExp(boolean keepExp) {
        this.keepExp = keepExp;
    }

    public boolean isCombatLog() {
        return combatLog;
    }

    public void setCombatLog(boolean combatLog) {
        this.combatLog = combatLog;
    }

    public boolean isPreventBlocks() {
        return preventBlocks;
    }

    public void setPreventBlocks(boolean preventBlocks) {
        this.preventBlocks = preventBlocks;
    }

    public Material getBarrierMaterial() {
        return barrierMaterial;
    }

    public void setBarrierMaterial(Material barrierMaterial) {
        this.barrierMaterial = barrierMaterial;
    }

    public List<PotionEffect> getEffects() {
        return effects;
    }

    public void addEffect(PotionEffect effect) {
        this.effects.add(effect);
    }

    public void clearEffects() {
        this.effects.clear();
    }

    public Material getDoorMaterial() {
        return doorMaterial;
    }

    public void setDoorMaterial(Material doorMaterial) {
        this.doorMaterial = doorMaterial;
    }

    public int getPlayersPerTeam() {
        return playersPerTeam;
    }

    public void setPlayersPerTeam(int playersPerTeam) {
        this.playersPerTeam = Math.max(1, Math.min(20, playersPerTeam));
    }

    public int getDoorOpenDelay() {
        return doorOpenDelay;
    }

    public void setDoorOpenDelay(int doorOpenDelay) {
        this.doorOpenDelay = Math.max(0, doorOpenDelay);
    }

    public boolean isUseActionBarsAndTitles() {
        return titlesEnabled || actionBarEnabled;
    }

    public void setUseActionBarsAndTitles(boolean useActionBarsAndTitles) {
        this.titlesEnabled = useActionBarsAndTitles;
        this.actionBarEnabled = useActionBarsAndTitles;
    }

    public BetMode getBetMode() {
        return betMode;
    }

    public void setBetMode(BetMode betMode) {
        this.betMode = betMode;
    }

    public int getMaxDuelTime() {
        return maxDuelTime;
    }

    public void setMaxDuelTime(int maxDuelTime) {
        this.maxDuelTime = Math.max(0, maxDuelTime);
    }

    public int getPostMatchTeleportDelay() {
        return postMatchTeleportDelay;
    }

    public void setPostMatchTeleportDelay(int postMatchTeleportDelay) {
        this.postMatchTeleportDelay = Math.max(0, postMatchTeleportDelay);
    }

    public int getMinPlayersToStart() {
        return minPlayersToStart;
    }

    public void setMinPlayersToStart(int minPlayersToStart) {
        this.minPlayersToStart = Math.max(2, Math.min(40, minPlayersToStart));
    }

    public boolean isTitlesEnabled() {
        return titlesEnabled;
    }

    public void setTitlesEnabled(boolean titlesEnabled) {
        this.titlesEnabled = titlesEnabled;
    }

    public boolean isActionBarEnabled() {
        return actionBarEnabled;
    }

    public void setActionBarEnabled(boolean actionBarEnabled) {
        this.actionBarEnabled = actionBarEnabled;
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public void setChatEnabled(boolean chatEnabled) {
        this.chatEnabled = chatEnabled;
    }

    public int getDetectedEntranceBlocks() {
        return detectedEntranceBlocks;
    }

    public void setDetectedEntranceBlocks(int detectedEntranceBlocks) {
        this.detectedEntranceBlocks = Math.max(0, detectedEntranceBlocks);
    }

    public void addPlayer(Player player) {
        if (!players.contains(player.getUniqueId())) {
            players.add(player.getUniqueId());
        }
    }

    public void removePlayer(Player player) {
        players.remove(player.getUniqueId());
    }

    public boolean isFull() {
        return players.size() >= (playersPerTeam * 2);
    }

    public int getCapacity() {
        return playersPerTeam * 2;
    }

    public boolean hasEnoughPlayersToStart() {
        return players.size() >= Math.min(minPlayersToStart, getCapacity());
    }

    public void removePlayer(UUID playerId) {
        players.remove(playerId);
    }

    public void broadcast(String message) {
        String translated = CC.translate(message);
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(translated);
            }
        }
        for (UUID uuid : spectators) {
            Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null && spectator.isOnline()) {
                spectator.sendMessage(translated);
            }
        }
    }

    public boolean isInArena(Location loc) {
        if (arenaPos1 == null || arenaPos2 == null)
            return false;
        if (!loc.getWorld().getName().equals(arenaPos1.getWorld().getName()))
            return false;

        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        double minX = Math.min(arenaPos1.getX(), arenaPos2.getX());
        double maxX = Math.max(arenaPos1.getX(), arenaPos2.getX());
        double minY = Math.min(arenaPos1.getY(), arenaPos2.getY());
        double maxY = Math.max(arenaPos1.getY(), arenaPos2.getY());
        double minZ = Math.min(arenaPos1.getZ(), arenaPos2.getZ());
        double maxZ = Math.max(arenaPos1.getZ(), arenaPos2.getZ());

        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public static class DoorRegion {
        private final Location pos1;
        private final Location pos2;
        private final List<BlockCoordinate> blockCoordinates;

        public DoorRegion(Location pos1, Location pos2) {
            this(pos1, pos2, new ArrayList<>());
        }

        public DoorRegion(Location pos1, Location pos2, List<BlockCoordinate> blockCoordinates) {
            this.pos1 = pos1.clone();
            this.pos2 = pos2.clone();
            this.blockCoordinates = new ArrayList<>();
            if (blockCoordinates != null) {
                this.blockCoordinates.addAll(blockCoordinates);
            }
        }

        public Location getPos1() {
            return pos1.clone();
        }

        public Location getPos2() {
            return pos2.clone();
        }

        public List<BlockCoordinate> getBlockCoordinates() {
            return new ArrayList<>(blockCoordinates);
        }

        public boolean hasBlockCoordinates() {
            return !blockCoordinates.isEmpty();
        }

        private boolean sameBounds(DoorRegion other) {
            if (other == null || pos1.getWorld() == null || pos2.getWorld() == null
                    || other.pos1.getWorld() == null || other.pos2.getWorld() == null) {
                return false;
            }

            if (!pos1.getWorld().equals(other.pos1.getWorld())) {
                return false;
            }

            int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
            int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
            int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
            int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
            int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
            int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

            int otherMinX = Math.min(other.pos1.getBlockX(), other.pos2.getBlockX());
            int otherMaxX = Math.max(other.pos1.getBlockX(), other.pos2.getBlockX());
            int otherMinY = Math.min(other.pos1.getBlockY(), other.pos2.getBlockY());
            int otherMaxY = Math.max(other.pos1.getBlockY(), other.pos2.getBlockY());
            int otherMinZ = Math.min(other.pos1.getBlockZ(), other.pos2.getBlockZ());
            int otherMaxZ = Math.max(other.pos1.getBlockZ(), other.pos2.getBlockZ());

            return minX == otherMinX && maxX == otherMaxX
                    && minY == otherMinY && maxY == otherMaxY
                    && minZ == otherMinZ && maxZ == otherMaxZ;
        }
    }

    public static class BlockCoordinate {
        private final int x;
        private final int y;
        private final int z;

        public BlockCoordinate(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }
    }
}
