package dev.zm.pvprooms.hooks;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.database.SQLiteDatabase;
import dev.zm.pvprooms.models.Room;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * %identifier_currentzone% – room name the player is in, or "none"
 *
 * Personal stats (total):
 * %identifier_kills% – normal_kills + clan_kills
 * %identifier_deaths% – normal_deaths + clan_deaths
 * %identifier_wins% – normal_wins + clan_wins
 * %identifier_losses% – normal_losses + clan_losses
 * %identifier_streak% – current win streak
 * %identifier_kdr% – total kills / total deaths (2 decimal places)
 *
 * Personal stats (by mode):
 * %identifier_mynormalkills% – normal kills
 * %identifier_mynormaldeaths% – normal deaths
 * %identifier_mynormalwins% – normal wins
 * %identifier_mynormallosses% – normal losses
 * %identifier_myclankills% – clan kills
 * %identifier_myclandeaths% – clan deaths
 * %identifier_myclanwins% – clan wins
 * %identifier_myclanlosses% – clan losses
 *
 * Leaderboards (name / value):
 * %identifier_top_normal_wins_1% – name of #1 in normal wins
 * %identifier_top_normal_wins_value_1% – value of #1 in normal wins
 * Supported columns: normal_wins, clan_wins, normal_kills, clan_kills,
 * normal_deaths, clan_deaths
 */
public class RoomsPlaceholderExpansion extends PlaceholderExpansion {

    private static final List<String> LEADERBOARD_COLUMNS = List.of(
            "normal_wins", "clan_wins",
            "normal_kills", "clan_kills",
            "normal_deaths", "clan_deaths");

    private final ZMPvPRooms plugin;
    private final String identifier;
    private volatile Map<String, List<SQLiteDatabase.TopEntry>> topCache = new HashMap<>();
    private volatile long topCacheAt = 0L;
    private volatile boolean topRefreshing = false;
    private final ConcurrentHashMap<UUID, CachedStats> personalCache = new ConcurrentHashMap<>();

    public RoomsPlaceholderExpansion(ZMPvPRooms plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier.toLowerCase(Locale.ROOT);
    }

    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getAuthor() {
        return "zMarkitos_";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null)
            return "";

        String key = params.toLowerCase(Locale.ROOT).trim();

        if (key.equals("currentzone")) {
            Optional<Room> room = player.isOnline()
                    ? plugin.getRoomManager().getRoomByPlayer(player.getPlayer())
                    : Optional.empty();
            return room.map(Room::getName).orElse("none");
        }

        if (key.startsWith("top_")) {
            triggerTopRefreshIfNeeded();
            return resolveTopPlaceholder(key);
        }

        if (key.startsWith("status_")) {
            String roomName = key.substring("status_".length());
            Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
            if (!optRoom.isPresent()) {
                return dev.zm.pvprooms.utils.CC
                        .translate(plugin.getConfigManager().getRawMessage("placeholders.status_unknown"));
            }
            Room room = optRoom.get();
            if (!room.isEnabled()) {
                return dev.zm.pvprooms.utils.CC
                        .translate(plugin.getConfigManager().getRawMessage("placeholders.status_disabled"));
            }
            if (room.getState() == dev.zm.pvprooms.models.enums.RoomState.WAITING) {
                return dev.zm.pvprooms.utils.CC
                        .translate(plugin.getConfigManager().getRawMessage("placeholders.status_active"));
            }
            return dev.zm.pvprooms.utils.CC
                    .translate(plugin.getConfigManager().getRawMessage("placeholders.status_playing"));
        }

        if (key.startsWith("players_")) {
            String roomName = key.substring("players_".length());
            Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
            return optRoom.map(room -> String.valueOf(room.getPlayers().size())).orElse("0");
        }

        if (key.startsWith("max_")) {
            String roomName = key.substring("max_".length());
            Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
            return optRoom.map(room -> String.valueOf(room.getCapacity())).orElse("0");
        }

        SQLiteDatabase.StatRow stats = getCachedStats(player.getUniqueId(),
                player.isOnline() ? player.getPlayer().getName() : player.getName());

        switch (key) {
            case "kills":
                return String.valueOf(stats.normalKills + stats.clanKills);
            case "deaths":
                return String.valueOf(stats.normalDeaths + stats.clanDeaths);
            case "wins":
                return String.valueOf(stats.normalWins + stats.clanWins);
            case "losses":
                return String.valueOf(stats.normalLosses + stats.clanLosses);
            case "streak":
                return String.valueOf(stats.streak);
            case "kdr": {
                int totalDeaths = stats.normalDeaths + stats.clanDeaths;
                int totalKills = stats.normalKills + stats.clanKills;
                double kdr = totalDeaths <= 0 ? totalKills : totalKills / (double) totalDeaths;
                return String.format(Locale.US, "%.2f", kdr);
            }
            // Normal mode
            case "mynormalkills":
                return String.valueOf(stats.normalKills);
            case "mynormaldeaths":
                return String.valueOf(stats.normalDeaths);
            case "mynormalwins":
                return String.valueOf(stats.normalWins);
            case "mynormallosses":
                return String.valueOf(stats.normalLosses);
            // Clan mode
            case "myclankills":
                return String.valueOf(stats.clanKills);
            case "myclandeaths":
                return String.valueOf(stats.clanDeaths);
            case "myclanwins":
                return String.valueOf(stats.clanWins);
            case "myclanlosses":
                return String.valueOf(stats.clanLosses);
            default:
                return null;
        }
    }

    private void triggerTopRefreshIfNeeded() {
        int cacheSecs = Math.max(5, plugin.getConfig().getInt("leaderboards.cache-seconds", 30));
        long now = System.currentTimeMillis();
        if (now - topCacheAt < cacheSecs * 1000L || topRefreshing) {
            return;
        }
        topRefreshing = true;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int size = Math.max(1, plugin.getConfig().getInt("leaderboards.size", 10));
                Map<String, List<SQLiteDatabase.TopEntry>> fresh = new HashMap<>();
                for (String col : LEADERBOARD_COLUMNS) {
                    fresh.put(col, plugin.getDatabase().queryTop(col, size));
                }
                topCache = fresh;
                topCacheAt = System.currentTimeMillis();
            } finally {
                topRefreshing = false;
            }
        });
    }

    private String resolveTopPlaceholder(String key) {
        String[] parts = key.split("_");
        if (parts.length < 4)
            return "";

        boolean valueMode = parts.length >= 5 && "value".equals(parts[3]);
        String column;
        int rank;
        try {
            if (valueMode) {
                // top _ <t1> _ <t2> _ value _ <rank>
                if (parts.length < 6)
                    return "0";
                column = parts[1] + "_" + parts[2];
                rank = Integer.parseInt(parts[5]);
            } else {
                // top _ <t1> _ <t2> _ <rank>
                column = parts[1] + "_" + parts[2];
                rank = Integer.parseInt(parts[3]);
            }
        } catch (NumberFormatException e) {
            return valueMode ? "0" : "N/A";
        }

        if (rank < 1 || !LEADERBOARD_COLUMNS.contains(column)) {
            return valueMode ? "0" : "N/A";
        }

        List<SQLiteDatabase.TopEntry> list = topCache.getOrDefault(column, List.of());
        if (rank > list.size()) {
            return valueMode ? "0" : "N/A";
        }

        SQLiteDatabase.TopEntry entry = list.get(rank - 1);
        return valueMode ? String.valueOf(entry.value) : entry.name;
    }

    private SQLiteDatabase.StatRow getCachedStats(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        int cacheMs = Math.max(1000, plugin.getConfig().getInt("leaderboards.personal-cache-ms", 3000));

        CachedStats cached = personalCache.get(uuid);
        if (cached != null && now - cached.cachedAt < cacheMs) {
            return cached.row;
        }

        // Return stale data immediately and refresh in background
        final SQLiteDatabase.StatRow stale = cached != null ? cached.row : new SQLiteDatabase.StatRow();

        if (plugin.getDatabase() != null) {
            final String uuidStr = uuid.toString();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                SQLiteDatabase.StatRow fresh = plugin.getDatabase().loadStats(uuidStr);
                personalCache.put(uuid, new CachedStats(fresh, System.currentTimeMillis()));
            });
        }

        // Optimistically insert a fresh entry so next call gets real data
        if (cached == null) {
            personalCache.put(uuid, new CachedStats(stale, 0L)); // cachedAt=0 forces next refresh
        }

        return stale;
    }

    // Internal cache holder

    private static final class CachedStats {
        final SQLiteDatabase.StatRow row;
        final long cachedAt;

        CachedStats(SQLiteDatabase.StatRow row, long cachedAt) {
            this.row = row;
            this.cachedAt = cachedAt;
        }
    }
}
