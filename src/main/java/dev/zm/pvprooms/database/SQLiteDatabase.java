package dev.zm.pvprooms.database;

import dev.zm.pvprooms.ZMPvPRooms;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages a single persistent SQLite connection with WAL mode for safe
 * concurrent reads. All write operations use atomic UPSERT statements
 * (INSERT OR IGNORE + UPDATE) to avoid lost-update races.
 */
public class SQLiteDatabase {

    private final ZMPvPRooms plugin;
    private Connection connection;

    public SQLiteDatabase(ZMPvPRooms plugin) {
        this.plugin = plugin;
        connect();
        applyPragmas();
        createTables();
        runMigrations();
    }

    // Connection management

    private void connect() {
        File dbFile = new File(plugin.getDataFolder(), "database.db");
        try {
            dbFile.getParentFile().mkdirs();
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().severe("Could not connect to SQLite: " + e.getMessage());
        }
    }

    /**
     * Enable WAL mode and optimize SQLite settings for write performance
     * and concurrent reads.
     */
    private void applyPragmas() {
        if (connection == null) return;
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");       // concurrent readers + writer
            st.execute("PRAGMA synchronous=NORMAL");     // faster than FULL, still safe with WAL
            st.execute("PRAGMA cache_size=-8000");       // ~8 MB page cache
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA temp_store=MEMORY");
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not apply SQLite pragmas: " + e.getMessage());
        }
    }

    /** Returns the active connection, reconnecting if it was closed. */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
                applyPragmas();
            }
        } catch (SQLException e) {
            connect();
            applyPragmas();
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing SQLite connection", e);
        }
    }

    // Schema

    private void createTables() {
        String playerStats =
                "CREATE TABLE IF NOT EXISTS player_stats (" +
                "  uuid          VARCHAR(36) PRIMARY KEY," +
                "  name          VARCHAR(16)," +
                "  normal_kills  INTEGER NOT NULL DEFAULT 0," +
                "  normal_deaths INTEGER NOT NULL DEFAULT 0," +
                "  normal_wins   INTEGER NOT NULL DEFAULT 0," +
                "  normal_losses INTEGER NOT NULL DEFAULT 0," +
                "  clan_kills    INTEGER NOT NULL DEFAULT 0," +
                "  clan_deaths   INTEGER NOT NULL DEFAULT 0," +
                "  clan_wins     INTEGER NOT NULL DEFAULT 0," +
                "  clan_losses   INTEGER NOT NULL DEFAULT 0," +
                "  streak        INTEGER NOT NULL DEFAULT 0" +
                ");";

        String pluginSettings =
                "CREATE TABLE IF NOT EXISTS plugin_settings (" +
                "  setting_key   VARCHAR(64) PRIMARY KEY," +
                "  setting_value TEXT" +
                ");";

        // Indexes for fast leaderboard ORDER BY queries
        String idxNormalWins   = "CREATE INDEX IF NOT EXISTS idx_normal_wins   ON player_stats(normal_wins   DESC);";
        String idxClanWins     = "CREATE INDEX IF NOT EXISTS idx_clan_wins     ON player_stats(clan_wins     DESC);";
        String idxNormalKills  = "CREATE INDEX IF NOT EXISTS idx_normal_kills  ON player_stats(normal_kills  DESC);";
        String idxClanKills    = "CREATE INDEX IF NOT EXISTS idx_clan_kills    ON player_stats(clan_kills    DESC);";
        String idxNormalDeaths = "CREATE INDEX IF NOT EXISTS idx_normal_deaths ON player_stats(normal_deaths DESC);";
        String idxClanDeaths   = "CREATE INDEX IF NOT EXISTS idx_clan_deaths   ON player_stats(clan_deaths   DESC);";
        String idxStreak       = "CREATE INDEX IF NOT EXISTS idx_streak        ON player_stats(streak        DESC);";

        try (Statement st = getConnection().createStatement()) {
            st.execute(playerStats);
            st.execute(pluginSettings);
            st.execute(idxNormalWins);
            st.execute(idxClanWins);
            st.execute(idxNormalKills);
            st.execute(idxClanKills);
            st.execute(idxNormalDeaths);
            st.execute(idxClanDeaths);
            st.execute(idxStreak);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables/indexes: " + e.getMessage());
        }
    }

    // Migration system

    /**
     * Current schema version. Increment this constant and add a migration block
     * in {@link #runMigrations()} whenever a structural change is needed.
     */
    private static final int CURRENT_SCHEMA_VERSION = 2;

    /**
     * Runs all pending migrations in order. Safe to call on every startup:
     * already-applied migrations are skipped. Existing data is never lost.
     */
    private void runMigrations() {
        int current = 0;
        String raw = getSetting("db_schema_version");
        if (raw != null) {
            try { current = Integer.parseInt(raw); } catch (NumberFormatException ignored) {}
        }

        if (current >= CURRENT_SCHEMA_VERSION) {
            return; // Already up-to-date
        }

        plugin.getLogger().info("[DB] Schema version " + current
                + " detected, migrating to v" + CURRENT_SCHEMA_VERSION + "...");

        // v1 → v2: Add player_bets table
        if (current < 2) {
            applyMigration(2, "CREATE TABLE IF NOT EXISTS player_bets (" +
                    "  id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  better_uuid   VARCHAR(36) NOT NULL," +
                    "  better_name   VARCHAR(16)," +
                    "  target_uuid   VARCHAR(36) NOT NULL," +
                    "  room_name     VARCHAR(64) NOT NULL," +
                    "  placed_at     INTEGER NOT NULL" +
                    ");");
            applyMigration(2, "CREATE INDEX IF NOT EXISTS idx_bets_room ON player_bets(room_name);");
        }

        // Save updated version
        setSetting("db_schema_version", String.valueOf(CURRENT_SCHEMA_VERSION));
        plugin.getLogger().info("[DB] Migration complete. Schema is now v" + CURRENT_SCHEMA_VERSION + ".");
    }

    /**
     * Executes a single DDL statement as part of a migration.
     * Errors are logged but do not stop the migration chain.
     */
    private void applyMigration(int version, String ddl) {
        try (Statement st = getConnection().createStatement()) {
            st.execute(ddl);
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB] Migration v" + version + " DDL failed: " + e.getMessage());
        }
    }

    // Atomic stat operations (UPSERT pattern — thread-safe with WAL)

    /**
     * Ensures the player row exists, then atomically increments a single stat
     * column. Uses two statements inside a single connection to avoid any
     * lost-update race between UPDATE + INSERT fallback.
     *
     * @param uuid       Player UUID string
     * @param playerName Current display name (kept up-to-date on every write)
     * @param column     Column to increment (validated by callers)
     */
    public void incrementStat(String uuid, String playerName, String column) {
        if (uuid == null || column == null) return;

        // Ensure the row exists without touching existing values
        String upsertRow =
                "INSERT INTO player_stats (uuid, name) VALUES (?, ?)" +
                " ON CONFLICT(uuid) DO UPDATE SET name = excluded.name";

        // Then increment the target column atomically
        String increment = "UPDATE player_stats SET " + column + " = " + column + " + 1 WHERE uuid = ?";

        Connection conn = getConnection();
        if (conn == null) return;
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(upsertRow)) {
                ps1.setString(1, uuid);
                ps1.setString(2, playerName != null ? playerName : "Unknown");
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement(increment)) {
                ps2.setString(1, uuid);
                ps2.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            tryRollback(conn);
            plugin.getLogger().warning("Error incrementing stat '" + column + "': " + e.getMessage());
        } finally {
            tryRestoreAutoCommit(conn);
        }
    }

    /**
     * Sets a stat column to a specific value (e.g. streak reset to 0).
     */
    public void setStat(String uuid, String playerName, String column, int value) {
        if (uuid == null || column == null) return;

        String upsertRow =
                "INSERT INTO player_stats (uuid, name) VALUES (?, ?)" +
                " ON CONFLICT(uuid) DO UPDATE SET name = excluded.name";
        String update = "UPDATE player_stats SET " + column + " = ? WHERE uuid = ?";

        Connection conn = getConnection();
        if (conn == null) return;
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(upsertRow)) {
                ps1.setString(1, uuid);
                ps1.setString(2, playerName != null ? playerName : "Unknown");
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement(update)) {
                ps2.setInt(1, value);
                ps2.setString(2, uuid);
                ps2.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            tryRollback(conn);
            plugin.getLogger().warning("Error setting stat '" + column + "': " + e.getMessage());
        } finally {
            tryRestoreAutoCommit(conn);
        }
    }

    /**
     * Atomically increments the streak column (winner keeps growing).
     */
    public void incrementStreak(String uuid, String playerName) {
        incrementStat(uuid, playerName, "streak");
    }

    /**
     * Resets the streak to 0.
     */
    public void resetStreak(String uuid, String playerName) {
        setStat(uuid, playerName, "streak", 0);
    }

    // Leaderboard queries

    /**
     * Returns up to {@code limit} entries ordered by {@code column} descending.
     * Only rows with name IS NOT NULL are included.
     */
    public List<TopEntry> queryTop(String column, int limit) {
        List<TopEntry> result = new ArrayList<>();
        String query =
                "SELECT name, " + column + " AS val" +
                " FROM player_stats" +
                " WHERE name IS NOT NULL AND " + column + " > 0" +
                " ORDER BY " + column + " DESC, name ASC" +
                " LIMIT ?";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TopEntry(rs.getString("name"), rs.getInt("val")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error querying leaderboard '" + column + "': " + e.getMessage());
        }
        return result;
    }

    // Personal stats query

    /**
     * Loads a complete stat row for the given UUID, or a zeroed snapshot if
     * the player has no data yet.
     */
    public StatRow loadStats(String uuid) {
        StatRow row = new StatRow();
        String query =
                "SELECT normal_kills, normal_deaths, normal_wins, normal_losses," +
                "       clan_kills, clan_deaths, clan_wins, clan_losses, streak" +
                " FROM player_stats WHERE uuid = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    row.normalKills  = rs.getInt("normal_kills");
                    row.normalDeaths = rs.getInt("normal_deaths");
                    row.normalWins   = rs.getInt("normal_wins");
                    row.normalLosses = rs.getInt("normal_losses");
                    row.clanKills    = rs.getInt("clan_kills");
                    row.clanDeaths   = rs.getInt("clan_deaths");
                    row.clanWins     = rs.getInt("clan_wins");
                    row.clanLosses   = rs.getInt("clan_losses");
                    row.streak       = rs.getInt("streak");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error loading stats for " + uuid + ": " + e.getMessage());
        }
        return row;
    }

    // Plugin settings

    public void setSetting(String key, String value) {
        if (key == null || key.trim().isEmpty()) return;
        String query =
                "INSERT INTO plugin_settings(setting_key, setting_value) VALUES(?, ?)" +
                " ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save setting '" + key + "': " + e.getMessage());
        }
    }

    public String getSetting(String key) {
        if (key == null || key.trim().isEmpty()) return null;
        String query = "SELECT setting_value FROM plugin_settings WHERE setting_key = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("setting_value");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not read setting '" + key + "': " + e.getMessage());
        }
        return null;
    }

    // Bet persistence

    /**
     * Saves a bet record. Called when a player confirms their bet.
     *
     * @param betterUuid UUID of the player who placed the bet
     * @param betterName Display name of the better (kept for record)
     * @param targetUuid UUID of the player being bet on
     * @param roomName   Name of the room where the match is taking place
     */
    public void saveBet(String betterUuid, String betterName, String targetUuid, String roomName) {
        if (betterUuid == null || targetUuid == null || roomName == null) return;
        String query = "INSERT INTO player_bets (better_uuid, better_name, target_uuid, room_name, placed_at)" +
                " VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, betterUuid);
            ps.setString(2, betterName != null ? betterName : "Unknown");
            ps.setString(3, targetUuid);
            ps.setString(4, roomName.toLowerCase());
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save bet: " + e.getMessage());
        }
    }

    /**
     * Returns all active bet records for the given room.
     *
     * @param roomName Name of the room
     * @return List of {@link BetRow} objects
     */
    public List<BetRow> getBetsByRoom(String roomName) {
        List<BetRow> result = new ArrayList<>();
        if (roomName == null) return result;
        String query = "SELECT id, better_uuid, better_name, target_uuid FROM player_bets WHERE room_name = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, roomName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BetRow row = new BetRow();
                    row.id          = rs.getLong("id");
                    row.betterUuid  = rs.getString("better_uuid");
                    row.betterName  = rs.getString("better_name");
                    row.targetUuid  = rs.getString("target_uuid");
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load bets for room '" + roomName + "': " + e.getMessage());
        }
        return result;
    }

    /**
     * Removes all bet records for a room once the match has concluded.
     *
     * @param roomName Name of the room
     */
    public void removeBetsByRoom(String roomName) {
        if (roomName == null) return;
        String query = "DELETE FROM player_bets WHERE room_name = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(query)) {
            ps.setString(1, roomName.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not remove bets for room '" + roomName + "': " + e.getMessage());
        }
    }

    // Helpers

    private void tryRollback(Connection conn) {
        try { conn.rollback(); } catch (SQLException ignored) {}
    }

    private void tryRestoreAutoCommit(Connection conn) {
        try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
    }

    // Data transfer objects

    /** A single leaderboard entry. */
    public static final class TopEntry {
        public final String name;
        public final int value;

        public TopEntry(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    /** A complete snapshot of a player's stats row. */
    public static final class StatRow {
        public int normalKills;
        public int normalDeaths;
        public int normalWins;
        public int normalLosses;
        public int clanKills;
        public int clanDeaths;
        public int clanWins;
        public int clanLosses;
        public int streak;
    }

    /** A single bet record retrieved from the database. */
    public static final class BetRow {
        public long   id;
        public String betterUuid;
        public String betterName;
        public String targetUuid;
    }
}
