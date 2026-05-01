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
 * PlaceholderAPI expansion for zMPvPRooms.
 *
 * <p>Registered under four identifiers so server owners can use whichever
 * prefix they prefer: {@code rooms}, {@code zmrooms}, {@code zmpvp},
 * {@code zmpvprooms}.</p>
 *
 * <h3>Available placeholders</h3>
 * <pre>
 *  %identifier_currentzone%          – room name the player is in, or "none"
 *
 *  Personal stats (total):
 *  %identifier_kills%                – normal_kills + clan_kills
 *  %identifier_deaths%               – normal_deaths + clan_deaths
 *  %identifier_wins%                 – normal_wins + clan_wins
 *  %identifier_losses%               – normal_losses + clan_losses
 *  %identifier_streak%               – current win streak
 *  %identifier_kdr%                  – total kills / total deaths (2 decimal places)
 *
 *  Personal stats (by mode):
 *  %identifier_mynormalkills%        – normal kills
 *  %identifier_mynormaldeaths%       – normal deaths
 *  %identifier_mynormalwins%         – normal wins
 *  %identifier_mynormallosses%       – normal losses
 *  %identifier_myclankills%          – clan kills
 *  %identifier_myclandeaths%         – clan deaths
 *  %identifier_myclanwins%           – clan wins
 *  %identifier_myclanlosses%         – clan losses
 *
 *  Leaderboards (name / value):
 *  %identifier_top_normal_wins_1%    – name of #1 in normal wins
 *  %identifier_top_normal_wins_value_1% – value of #1 in normal wins
 *  Supported columns: normal_wins, clan_wins, normal_kills, clan_kills,
 *                     normal_deaths, clan_deaths
 * </pre>
 */
public class RoomsPlaceholderExpansion extends PlaceholderExpansion {

    // Columns that are allowed in leaderboard queries (whitelist for SQL safety)
    private static final List<String> LEADERBOARD_COLUMNS = List.of(
            "normal_wins", "clan_wins",
            "normal_kills", "clan_kills",
            "normal_deaths", "clan_deaths"
    );

    private final ZMPvPRooms plugin;
    private final String identifier;

    // ----- Leaderboard cache (refreshed asynchronously) -----
    /** Snapshot of leaderboard data. Replaced atomically after each async refresh. */
    private volatile Map<String, List<SQLiteDatabase.TopEntry>> topCache = new HashMap<>();
    /** Timestamp of the last completed cache refresh. */
    private volatile long topCacheAt = 0L;
    /** Guards against scheduling multiple simultaneous refreshes. */
    private volatile boolean topRefreshing = false;

    // ----- Personal stats cache -----
    /** Thread-safe map: UUID → cached snapshot. */
    private final ConcurrentHashMap<UUID, CachedStats> personalCache = new ConcurrentHashMap<>();

    public RoomsPlaceholderExpansion(ZMPvPRooms plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier.toLowerCase(Locale.ROOT);
    }

    // -------------------------------------------------------------------------
    // PlaceholderExpansion contract
    // -------------------------------------------------------------------------

    @Override
    public @NotNull String getIdentifier() { return identifier; }

    @Override
    public @NotNull String getAuthor() { return "zMarkitos_"; }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    /** Keep the expansion registered across reloads. */
    @Override
    public boolean persist() { return true; }

    // -------------------------------------------------------------------------
    // Placeholder resolution
    // -------------------------------------------------------------------------

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        String key = params.toLowerCase(Locale.ROOT).trim();

        // -- Current zone --
        if (key.equals("currentzone")) {
            Optional<Room> room = player.isOnline()
                    ? plugin.getRoomManager().getRoomByPlayer(player.getPlayer())
                    : Optional.empty();
            return room.map(Room::getName).orElse("none");
        }

        // -- Leaderboard --
        if (key.startsWith("top_")) {
            triggerTopRefreshIfNeeded();
            return resolveTopPlaceholder(key);
        }

        // -- Personal stats --
        SQLiteDatabase.StatRow stats = getCachedStats(player.getUniqueId(),
                player.isOnline() ? player.getPlayer().getName() : player.getName());

        switch (key) {
            // ---- Combined totals ----
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
                int totalKills  = stats.normalKills  + stats.clanKills;
                double kdr = totalDeaths <= 0 ? totalKills : totalKills / (double) totalDeaths;
                return String.format(Locale.US, "%.2f", kdr);
            }
            // ---- Normal mode ----
            case "mynormalkills":
                return String.valueOf(stats.normalKills);
            case "mynormaldeaths":
                return String.valueOf(stats.normalDeaths);
            case "mynormalwins":
                return String.valueOf(stats.normalWins);
            case "mynormallosses":
                return String.valueOf(stats.normalLosses);
            // ---- Clan mode ----
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

    // -------------------------------------------------------------------------
    // Leaderboard
    // -------------------------------------------------------------------------

    /**
     * Schedules an async cache refresh if the data is stale and no refresh is
     * already running. The main thread never blocks on SQL.
     */
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
                topCache   = fresh;
                topCacheAt = System.currentTimeMillis();
            } finally {
                topRefreshing = false;
            }
        });
    }

    /**
     * Resolves a top_ placeholder from the in-memory cache.
     *
     * <p>Supported formats:
     * <ul>
     *   <li>{@code top_normal_wins_1}          → player name at rank 1</li>
     *   <li>{@code top_normal_wins_value_1}     → score at rank 1</li>
     * </ul>
     */
    private String resolveTopPlaceholder(String key) {
        // key examples:
        //   top_normal_wins_1           → parts: [top][normal][wins][1]        length=4
        //   top_normal_wins_value_1     → parts: [top][normal][wins][value][1]  length=5
        String[] parts = key.split("_");
        if (parts.length < 4) return "";

        boolean valueMode = parts.length >= 5 && "value".equals(parts[3]);
        String column;
        int rank;
        try {
            if (valueMode) {
                // top _ <t1> _ <t2> _ value _ <rank>
                if (parts.length < 6) return "0";
                column = parts[1] + "_" + parts[2];
                rank   = Integer.parseInt(parts[5]);
            } else {
                // top _ <t1> _ <t2> _ <rank>
                column = parts[1] + "_" + parts[2];
                rank   = Integer.parseInt(parts[3]);
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

    // -------------------------------------------------------------------------
    // Personal stats cache
    // -------------------------------------------------------------------------

    /**
     * Returns a stat snapshot for the player, loading from DB asynchronously
     * if the cached copy is stale. The main thread always gets cached data
     * instantly — no blocking SQL.
     */
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

    // -------------------------------------------------------------------------
    // Internal cache holder
    // -------------------------------------------------------------------------

    private static final class CachedStats {
        final SQLiteDatabase.StatRow row;
        final long cachedAt;

        CachedStats(SQLiteDatabase.StatRow row, long cachedAt) {
            this.row = row;
            this.cachedAt = cachedAt;
        }
    }
}
