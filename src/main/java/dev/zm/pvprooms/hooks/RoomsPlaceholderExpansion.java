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
 * %identifier_top_normal_wins_1_room1% – name of #1 in normal wins for room1
 * %identifier_top_normal_wins_value_1_room1% – value of #1 in normal wins for room1
 * Supported columns: normal_wins, clan_wins, normal_kills, clan_kills,
 * normal_deaths, clan_deaths
 */
public class RoomsPlaceholderExpansion extends PlaceholderExpansion {

    private static final List<String> LEADERBOARD_COLUMNS = List.of(
            "normal_wins", "clan_wins",
            "normal_kills", "clan_kills",
            "normal_deaths", "clan_deaths", "streak");

    private final ZMPvPRooms plugin;
    private final String identifier;
    private volatile Map<String, Map<String, List<SQLiteDatabase.TopEntry>>> topCache = new HashMap<>();
    private volatile Map<String, Long> topCacheAt = new HashMap<>();
    private volatile Map<String, Boolean> topRefreshing = new HashMap<>();
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
            TopRequest request = parseTopRequest(key);
            if (request == null) {
                return key.contains("value") ? "0" : "N/A";
            }
            triggerTopRefreshIfNeeded(request.scopeKey);
            return resolveTopPlaceholder(request);
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

    public void forceTopRefresh() {
        topCacheAt = new HashMap<>();
        triggerTopRefreshIfNeeded(null);
    }

    public void triggerTopRefreshIfNeeded() {
        triggerTopRefreshIfNeeded(null);
    }

    public void triggerTopRefreshIfNeeded(String roomName) {
        int cacheSecs = Math.max(5, plugin.getConfig().getInt("leaderboards.cache-seconds", 30));
        long now = System.currentTimeMillis();
        String scopeKey = scopeKey(roomName);
        long cachedAt = topCacheAt.getOrDefault(scopeKey, 0L);
        if (now - cachedAt < cacheSecs * 1000L || topRefreshing.getOrDefault(scopeKey, false)) {
            return;
        }
        topRefreshing.put(scopeKey, true);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int size = Math.max(1, plugin.getConfig().getInt("leaderboards.size", 10));
                Map<String, List<SQLiteDatabase.TopEntry>> fresh = new HashMap<>();
                for (String col : LEADERBOARD_COLUMNS) {
                    fresh.put(col, plugin.getDatabase().queryTop(col, size, roomName));
                }
                Map<String, Map<String, List<SQLiteDatabase.TopEntry>>> cache = new HashMap<>(topCache);
                cache.put(scopeKey, fresh);
                topCache = cache;

                Map<String, Long> at = new HashMap<>(topCacheAt);
                at.put(scopeKey, System.currentTimeMillis());
                topCacheAt = at;
            } finally {
                Map<String, Boolean> refreshing = new HashMap<>(topRefreshing);
                refreshing.put(scopeKey, false);
                topRefreshing = refreshing;
            }
        });
    }

    private String resolveTopPlaceholder(TopRequest request) {
        String[] parts = request.baseKey.split("_");
        if (parts.length < 4) return "";

        boolean valueMode = parts.length >= 5 && "value".equals(parts[3]);
        String column;
        int rank;
        try {
            if (valueMode) {
                // top _ <t1> _ <t2> _ value _ <rank>
                if (parts.length < 5)
                    return "0";
                column = parts[1] + "_" + parts[2];
                rank = Integer.parseInt(parts[4]);
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

        Map<String, List<SQLiteDatabase.TopEntry>> scope = topCache.getOrDefault(request.scopeKey, Map.of());
        List<SQLiteDatabase.TopEntry> list = scope.getOrDefault(column, List.of());
        if (rank > list.size()) {
            return valueMode ? "0" : "N/A";
        }

        SQLiteDatabase.TopEntry entry = list.get(rank - 1);
        return valueMode ? String.valueOf(entry.value) : entry.name;
    }

    private TopRequest parseTopRequest(String key) {
        String[] parts = key.split("_");
        if (parts.length < 4) {
            return null;
        }
        boolean valueMode = parts.length >= 5 && "value".equals(parts[3]);
        int minGlobalParts = valueMode ? 5 : 4;
        if (parts.length == minGlobalParts) {
            return new TopRequest(key, "global", null);
        }
        if (parts.length <= minGlobalParts) {
            return null;
        }
        int scopeStart = valueMode ? 5 : 4;
        String scope = joinTail(parts, scopeStart);
        String baseKey = joinHead(parts, scopeStart);
        return new TopRequest(baseKey, scopeKey(scope), scope);
    }

    private String scopeKey(String roomName) {
        return roomName == null || roomName.trim().isEmpty() ? "global" : roomName.toLowerCase(Locale.ROOT).trim();
    }

    private String joinTail(String[] parts, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (i > start) sb.append('_');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private String joinHead(String[] parts, int endExclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < endExclusive; i++) {
            if (i > 0) sb.append('_');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private SQLiteDatabase.StatRow getCachedStats(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        int cacheMs = Math.max(1000, plugin.getConfig().getInt("leaderboards.personal-cache-ms", 3000));

        CachedStats cached = personalCache.get(uuid);
        if (cached != null && now - cached.cachedAt < cacheMs) {
            return cached.row;
        }

        // Return stale data immediately and schedule a background refresh.
        // We do NOT pre-insert a placeholder entry here — the async task will
        // put the real data in the map once it finishes, avoiding the race
        // condition where the pre-inserted entry would suppress the real update.
        final SQLiteDatabase.StatRow stale = cached != null ? cached.row : new SQLiteDatabase.StatRow();

        if (plugin.getDatabase() != null) {
            final String uuidStr = uuid.toString();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                SQLiteDatabase.StatRow fresh = plugin.getDatabase().loadStats(uuidStr);
                personalCache.put(uuid, new CachedStats(fresh, System.currentTimeMillis()));
            });
        }

        return stale;
    }

    /**
     * Removes the cached stats entry for the given player, forcing the next
     * placeholder request to load fresh data from the database. Should be
     * called by {@link dev.zm.pvprooms.managers.MatchManager} immediately
     * after writing new stats to the database.
     *
     * @param uuid the player whose cache entry should be invalidated
     */
    public void invalidatePlayerCache(UUID uuid) {
        if (uuid != null) {
            personalCache.remove(uuid);
        }
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

    private static final class TopRequest {
        final String baseKey;
        final String scopeKey;
        final String roomName;

        TopRequest(String baseKey, String scopeKey, String roomName) {
            this.baseKey = baseKey;
            this.scopeKey = scopeKey;
            this.roomName = roomName;
        }
    }
}
