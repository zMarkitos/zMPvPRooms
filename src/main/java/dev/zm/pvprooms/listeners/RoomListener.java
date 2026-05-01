package dev.zm.pvprooms.listeners;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.models.enums.RoomState;
import dev.zm.pvprooms.models.enums.RoomType;
import dev.zm.pvprooms.utils.CC;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.Optional;
import java.util.List;
import java.util.Locale;

public class RoomListener implements Listener {

    private final ZMPvPRooms plugin;

    public RoomListener(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Optional<Room> optionalRoom = plugin.getRoomManager().getRoomByPlayer(player);

        if (!optionalRoom.isPresent()) {
            return;
        }

        Room room = optionalRoom.get();
        if (room.getState() != RoomState.PLAYING) {
            return;
        }

        if (room.isKeepInventory()) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            plugin.getMatchManager().handleDeathAsSpectator(player, player.getKiller(), room);
            return;
        }

        if (room.isKeepExp()) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }

        plugin.getMatchManager().handleDeath(player, player.getKiller(), room);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        Optional<Room> attackerRoom = plugin.getRoomManager().getRoomByPlayer(attacker);
        Optional<Room> victimRoom = plugin.getRoomManager().getRoomByPlayer(victim);
        if (!attackerRoom.isPresent() || !victimRoom.isPresent() || attackerRoom.get() != victimRoom.get()) {
            return;
        }

        Room room = attackerRoom.get();

        // Block ALL damage while the countdown is running or match is not yet active.
        // This covers WAITING, STARTING, and ENDING states.
        if (room.getState() != RoomState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        // During PLAYING: block friendly fire (same team in NORMAL, same clan in CLAN).
        if (plugin.getMatchManager().areSameTeam(room, attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getRoomManager().getRoomByPlayer(player).ifPresent(room -> {
            if (room.getSpectators().contains(player.getUniqueId())) {
                room.getSpectators().remove(player.getUniqueId());
                plugin.getMatchManager().queueReturnSpawnOnJoin(player.getUniqueId());
                return;
            }
            plugin.getMatchManager().handleQuit(player, room);
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getMatchManager().handleJoinReturnSpawn(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        plugin.getMatchManager().handleRespawnTeleport(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        plugin.getRoomManager().getRoomByPlayer(player).ifPresent(room -> {
            if (room.getSpectators().contains(player.getUniqueId())) {
                room.getSpectators().remove(player.getUniqueId());
                plugin.getMatchManager().queueReturnSpawnOnJoin(player.getUniqueId());
                return;
            }
            plugin.getMatchManager().handleQuit(player, room);
        });
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Optional<Room> optionalRoom = plugin.getRoomManager().getRoomByPlayer(player);

        if (optionalRoom.isPresent() && optionalRoom.get().isPreventBlocks() && !player.hasPermission("zmrooms.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Optional<Room> optionalRoom = plugin.getRoomManager().getRoomByPlayer(player);

        if (optionalRoom.isPresent() && optionalRoom.get().isPreventBlocks() && !player.hasPermission("zmrooms.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Optional<Room> playerRoom = plugin.getRoomManager().getRoomByPlayer(player);

        if (playerRoom.isPresent()) {
            Room room = playerRoom.get();
            if (room.getSpectators().contains(player.getUniqueId())) {
                if (!isInsideArena(room, event.getTo())) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage("spectate_cannot_leave"));
                }
                return;
            }

            if (room.getType() == RoomType.NORMAL
                    && (room.getState() == RoomState.WAITING || room.getState() == RoomState.STARTING)
                    && !isInsideArena(room, event.getTo())) {
                plugin.getMatchManager().handleQuit(player, room);
                return;
            }

            if (room.getState() == RoomState.PLAYING && !isInsideArena(room, event.getTo())) {
                event.setCancelled(true);
                player.teleport(event.getFrom());
                player.sendMessage(plugin.getConfigManager().getMessage("cannot_leave_arena"));
            }
            return;
        }

        for (Room room : plugin.getRoomManager().getRooms().values()) {
            if (!isInsideArena(room, event.getTo())) {
                continue;
            }

            if (room.getType() == RoomType.NORMAL && room.getState() == RoomState.WAITING
                    && !room.isFull() && !isInsideArena(room, event.getFrom())
                    && player.hasPermission("zmrooms.use")
                    && player.getGameMode() != GameMode.SPECTATOR) {
                plugin.getMatchManager().joinRoom(player, room);
                break;
            }

            if (room.getState() == RoomState.PLAYING || room.getState() == RoomState.STARTING
                    || room.getState() == RoomState.ENDING) {
                event.setCancelled(true);
                player.teleport(event.getFrom());
                player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("prefix")
                        + plugin.getConfigManager().getRawMessage("arena_in_use")));
            }
            break;
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Optional<Room> playerRoom = plugin.getRoomManager().getRoomByPlayer(player);
        if (event.getTo() == null) {
            return;
        }

        if (playerRoom.isPresent()) {
            Room room = playerRoom.get();
            if (room.getSpectators().contains(player.getUniqueId())) {
                if (!isInsideArena(room, event.getTo())) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage("spectate_cannot_leave"));
                }
                return;
            }

            if (room.getType() == RoomType.NORMAL
                    && (room.getState() == RoomState.WAITING || room.getState() == RoomState.STARTING)
                    && !isInsideArena(room, event.getTo())) {
                plugin.getMatchManager().handleQuit(player, room);
                return;
            }

            if (room.getState() == RoomState.PLAYING && !isInsideArena(room, event.getTo())) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("cannot_leave_arena"));
            }
            return;
        }

        for (Room room : plugin.getRoomManager().getRooms().values()) {
            if (!isInsideArena(room, event.getTo())) {
                continue;
            }
            if (room.getState() == RoomState.PLAYING || room.getState() == RoomState.STARTING
                    || room.getState() == RoomState.ENDING) {
                event.setCancelled(true);
                player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("prefix")
                        + plugin.getConfigManager().getRawMessage("arena_in_use")));
            }
            break;
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Optional<Room> playerRoom = plugin.getRoomManager().getRoomByPlayer(player);
        if (!playerRoom.isPresent()) {
            return;
        }

        Room room = playerRoom.get();
        if (room.getSpectators().contains(player.getUniqueId())) {
            room.getSpectators().remove(player.getUniqueId());
            plugin.getMatchManager().queueReturnSpawnOnJoin(player.getUniqueId());
            return;
        }

        if (room.getState() == RoomState.PLAYING) {
            plugin.getMatchManager().handleQuit(player, room);
            player.sendMessage(plugin.getConfigManager().getMessage("match_player_left_lose")
                    .replace("%player%", player.getName()));
            return;
        }

        plugin.getMatchManager().handleQuit(player, room);
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Optional<Room> room = plugin.getRoomManager().getRoomByPlayer(player);
        if (!room.isPresent()) {
            return;
        }
        String message = event.getMessage();
        if (message == null || message.length() <= 1) {
            return;
        }
        String command = message.substring(1).toLowerCase(Locale.ROOT).trim();
        if (command.isEmpty()) {
            return;
        }

        if (command.startsWith("zmrooms ") || command.equals("zmrooms")
                || command.startsWith("zmr ") || command.equals("zmr")
                || command.startsWith("pvprooms ") || command.equals("pvprooms")) {
            return;
        }

        List<String> blocked = plugin.getConfig().getStringList("room-protection.blacklist-commands");
        for (String raw : blocked) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String token = raw.toLowerCase(Locale.ROOT).trim();
            if (token.startsWith("/")) {
                token = token.substring(1);
            }
            if (command.equals(token) || command.startsWith(token + " ")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("room_command_blocked"));
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        Optional<Room> room = plugin.getRoomManager().getRoomByPlayer(player);
        if (!room.isPresent()) {
            return;
        }
        if (plugin.getConfig().getBoolean("room-protection.allow-external-inventories", true)) {
            return;
        }
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE || type == InventoryType.PLAYER) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(plugin.getConfigManager().getMessage("room_external_inventory_blocked"));
    }

    private boolean isInsideArena(Room room, Location location) {
        if (room == null || location == null || location.getWorld() == null) {
            return false;
        }

        Optional<dev.zm.pvprooms.hooks.worldguard.WorldGuardHook.RegionBounds> bounds =
                plugin.getRoomManager().getArenaBounds(room);
        if (bounds.isPresent()) {
            dev.zm.pvprooms.hooks.worldguard.WorldGuardHook.RegionBounds region = bounds.get();
            if (!location.getWorld().getName().equalsIgnoreCase(region.getWorldName())) {
                return false;
            }
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            int minX = Math.min(region.getMinX(), region.getMaxX());
            int maxX = Math.max(region.getMinX(), region.getMaxX());
            int minY = Math.min(region.getMinY(), region.getMaxY());
            int maxY = Math.max(region.getMinY(), region.getMaxY());
            int minZ = Math.min(region.getMinZ(), region.getMaxZ());
            int maxZ = Math.max(region.getMinZ(), region.getMaxZ());
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        return room.isInArena(location);
    }
}
