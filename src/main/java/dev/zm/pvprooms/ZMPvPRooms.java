package dev.zm.pvprooms;

import dev.zm.pvprooms.commands.MainCommand;
import dev.zm.pvprooms.commands.MainTabCompleter;
import dev.zm.pvprooms.database.SQLiteDatabase;
import dev.zm.pvprooms.hooks.RoomsPlaceholderExpansion;
import dev.zm.pvprooms.hooks.clans.ApexClanLoader;
import dev.zm.pvprooms.hooks.clans.ByteClansLoader;
import dev.zm.pvprooms.hooks.clans.ClanProvider;
import dev.zm.pvprooms.hooks.clans.UClansLoader;
import dev.zm.pvprooms.hooks.clans.ZMClansLoader;
import dev.zm.pvprooms.hooks.worldguard.WorldGuardHook;
import dev.zm.pvprooms.listeners.BetListener;
import dev.zm.pvprooms.listeners.EditorListener;
import dev.zm.pvprooms.listeners.RoomListener;
import dev.zm.pvprooms.managers.BetManager;
import dev.zm.pvprooms.managers.ConfigManager;
import dev.zm.pvprooms.managers.EditorManager;
import dev.zm.pvprooms.managers.MatchManager;
import dev.zm.pvprooms.managers.RoomEffectManager;
import dev.zm.pvprooms.managers.RoomManager;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.models.enums.RoomState;
import org.bstats.bukkit.Metrics;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import dev.zm.pvprooms.utils.VersionChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public final class ZMPvPRooms extends JavaPlugin {

    private static final List<String> DEFAULT_CLAN_PRIORITY = List.of(
            "zMClans", "UltimateClans", "uClans", "ByteClans", "ApexClan");

    private static ZMPvPRooms instance;

    private ConfigManager configManager;
    private RoomManager roomManager;
    private SQLiteDatabase database;
    private MatchManager matchManager;
    private BetManager betManager;
    private EditorManager editorManager;
    private RoomEffectManager roomEffectManager;
    private ClanProvider clanProvider;
    private WorldGuardHook worldGuardHook;
    private VersionChecker versionChecker;
    private RoomsPlaceholderExpansion placeholderExpansion;

    private void log(String message) {
        getServer().getConsoleSender().sendMessage(color(message));
    }

    private String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public void onEnable() {
        instance = this;
        // bStats — must be initialized early, before any other setup.
        // The reference is intentionally not stored; bStats manages its own lifecycle.
        new Metrics(this, 33238);

        long start = System.currentTimeMillis();

        loadConfigurations();
        setupDatabase();
        setupManagers();
        registerCommands();
        registerListeners();
        setupIntegrations();

        getServer().getScheduler().runTask(this, this::recoverInterruptedSessions);

        versionChecker = new VersionChecker(this);
        versionChecker.refresh();

        long time = System.currentTimeMillis() - start;

        log("&7&m----------------------------------------");
        log("&c&lzMPvPRooms &7v" + getDescription().getVersion());
        log("&7");
        if (database != null)
            log("&7• &fSQLite: &aConnected");
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null)
            log("&7• &fPlaceholderAPI: &aHooked");
        else
            log("&7• &fPlaceholderAPI: &cNot found");

        if (getServer().getPluginManager().getPlugin("Vault") != null)
            log("&7• &fVault: &aHooked");
        else
            log("&7• &fVault: &cNot found");

        if (clanProvider != null)
            log("&7• &fClan Hook: &a" + clanProvider.getProviderName());
        else
            log("&7• &fClan Hook: &cNone");

        if (worldGuardHook != null)
            log("&7• &fWorldGuard: &aHooked");
        else
            log("&7• &fWorldGuard: &cNot found");

        log("&7");
        log("&a✔ &fPlugin enabled successfully &7(" + time + "ms)");
        log("&7• &fRooms loaded: &c" + roomManager.getRooms().size());
        log("&7&m----------------------------------------");
    }

    @Override
    public void onDisable() {

        if (matchManager != null && roomManager != null) {
            for (Room room : roomManager.getRooms().values()) {
                if (room.getState() == RoomState.PLAYING
                        || room.getState() == RoomState.STARTING
                        || room.getState() == RoomState.ENDING) {
                    matchManager.forceEndMatchOnShutdown(room);
                }
            }
        }

        if (roomManager != null) {
            roomManager.saveRooms();
        }

        if (database != null) {
            database.close();
        }

        if (roomEffectManager != null) {
            roomEffectManager.clearAll();
        }

        log("&c✘ &fPlugin disabled.");
    }

    private void loadConfigurations() {
        saveDefaultConfig();
        reloadConfig();
        getConfig().options().copyDefaults(true);
        configManager = new ConfigManager(this);
    }

    private void setupDatabase() {
        if (getConfig().getBoolean("settings.use-sqlite", true)) {
            database = new SQLiteDatabase(this);
        }
    }

    private void setupManagers() {
        roomManager = new RoomManager(this);
        matchManager = new MatchManager(this);
        betManager = new BetManager(this);
        editorManager = new EditorManager(this);
        roomEffectManager = new RoomEffectManager(this);
    }

    private void registerCommands() {
        PluginCommand command = getCommand("zmrooms");
        if (command == null) {
            getLogger().severe("Command 'zmrooms' not found in plugin.yml.");
            return;
        }
        command.setExecutor(new MainCommand(this, roomManager));
        command.setTabCompleter(new MainTabCompleter(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new RoomListener(this), this);
        getServer().getPluginManager().registerEvents(new BetListener(this), this);
        getServer().getPluginManager().registerEvents(new EditorListener(this), this);
    }

    private void setupIntegrations() {
        setupPlaceholderHook();
        setupVaultHook();
        setupClanHook();
        setupWorldGuardHook();
    }

    /**
     * Called one tick after startup. If a server restart happened while rooms
     * were active, players that are already online need to be teleported back
     * to the return spawn. Rooms are reset to WAITING.
     */
    private void recoverInterruptedSessions() {
        Location returnSpawn = getReturnSpawn();
        for (Room room : roomManager.getRooms().values()) {
            boolean dirty = false;

            for (UUID uuid : List.copyOf(room.getPlayers())) {
                Player online = getServer().getPlayer(uuid);
                if (online != null) {
                    online.teleport(returnSpawn);
                }
                dirty = true;
            }
            for (UUID uuid : List.copyOf(room.getSpectators())) {
                Player online = getServer().getPlayer(uuid);
                if (online != null) {
                    online.teleport(returnSpawn);
                }
                dirty = true;
            }

            if (dirty) {
                room.getPlayers().clear();
                room.getSpectators().clear();
                room.setState(RoomState.WAITING);
                getLogger().info("Recovered interrupted session for room: " + room.getName());
            }
        }
    }

    private void setupPlaceholderHook() {
        if (!getConfig().getBoolean("hooks.placeholderapi", true))
            return;

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            // Keep one reference so MatchManager can invalidate personal stat caches.
            placeholderExpansion = new RoomsPlaceholderExpansion(this, "rooms");
            placeholderExpansion.register();
            // Kick off the first leaderboard refresh immediately so top_* placeholders
            // are populated from boot instead of returning "N/A" until the first query.
            getServer().getScheduler().runTaskAsynchronously(this, (Runnable) placeholderExpansion::triggerTopRefreshIfNeeded);
            new RoomsPlaceholderExpansion(this, "zmrooms").register();
            new RoomsPlaceholderExpansion(this, "zmpvp").register();
            new RoomsPlaceholderExpansion(this, "zmpvprooms").register();
            getLogger().info("PlaceholderAPI hooked.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholders will not work.");
        }
    }

    private void setupVaultHook() {
        if (!getConfig().getBoolean("hooks.vault", true))
            return;
    }

    private void setupClanHook() {
        clanProvider = null;
        if (!getConfig().getBoolean("hooks.clans.enabled", true)) {
            return;
        }

        PluginManager pluginManager = getServer().getPluginManager();
        for (String providerId : resolveClanPriority()) {
            clanProvider = tryLoadClanProvider(providerId, pluginManager);
            if (clanProvider != null) {
                return;
            }
        }
    }

    private List<String> resolveClanPriority() {
        List<String> configured = new ArrayList<>(
                getConfig().getStringList("hooks.clans.provider-priority"));
        for (String fallback : DEFAULT_CLAN_PRIORITY) {
            if (configured.stream().noneMatch(id -> id != null && id.equalsIgnoreCase(fallback))) {
                configured.add(fallback);
            }
        }
        return configured;
    }

    private ClanProvider tryLoadClanProvider(String providerId, PluginManager pluginManager) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }

        String id = providerId.trim().toLowerCase(Locale.ROOT);
        try {
            switch (id) {
                case "zmclans":
                    return isProviderEnabled("hooks.clans.zmclans")
                            ? ZMClansLoader.tryLoad(pluginManager, getLogger())
                            : null;
                case "ultimateclans":
                case "uclans":
                    return isProviderEnabled("hooks.clans.ultimateclans")
                            ? UClansLoader.tryLoad(pluginManager, getLogger())
                            : null;
                case "byteclans":
                    return isProviderEnabled("hooks.clans.byteclans")
                            ? ByteClansLoader.tryLoad(pluginManager, getLogger())
                            : null;
                case "apexclan":
                    return isProviderEnabled("hooks.clans.apexclan")
                            ? ApexClanLoader.tryLoad(pluginManager, getLogger())
                            : null;
                default:
                    getLogger().warning("Unknown clan provider in hooks.clans.provider-priority: " + providerId);
                    return null;
            }
        } catch (LinkageError error) {
            getLogger().info("Clan provider " + providerId
                    + " is not available on this server; skipping.");
        } catch (RuntimeException error) {
            getLogger().log(Level.WARNING, "Failed to load clan provider " + providerId + ".", error);
        }
        return null;
    }

    private boolean isProviderEnabled(String path) {
        return getConfig().getBoolean(path, true);
    }

    private void setupWorldGuardHook() {
        if (!getConfig().getBoolean("hooks.worldguard", true))
            return;

        Plugin wg = getServer().getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) {
            return;
        }

        worldGuardHook = new WorldGuardHook();
    }

    public static ZMPvPRooms getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RoomManager getRoomManager() {
        return roomManager;
    }

    public SQLiteDatabase getDatabase() {
        return database;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public BetManager getBetManager() {
        return betManager;
    }

    public EditorManager getEditorManager() {
        return editorManager;
    }

    public RoomEffectManager getRoomEffectManager() {
        return roomEffectManager;
    }

    public ClanProvider getClanProvider() {
        return clanProvider;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    public VersionChecker getVersionChecker() {
        return versionChecker;
    }

    /**
     * Returns the primary {@link RoomsPlaceholderExpansion} instance, or
     * {@code null}
     * if PlaceholderAPI is not installed. Used by {@link MatchManager} to
     * invalidate
     * personal stat caches after a write so placeholders reflect updated values
     * quickly.
     */
    public RoomsPlaceholderExpansion getPlaceholderExpansion() {
        return placeholderExpansion;
    }

    public void reloadAll() {
        reloadConfig();
        configManager.reloadLang();
        roomManager.loadRooms();
        setupClanHook();
        setupWorldGuardHook();
        if (versionChecker != null) {
            versionChecker.refresh();
        }
    }

    /**
     * Saves the return spawn to both config.yml and the database so it
     * survives server restarts regardless of how config is loaded.
     */
    public void setReturnSpawn(Location location) {
        if (location == null || location.getWorld() == null)
            return;
        getConfig().set("settings.return-spawn", location);
        if (database != null) {
            database.setSetting("return_spawn", serializeLocation(location));
        }
    }

    /**
     * Returns the configured return spawn. The database value takes priority
     * over config.yml so an admin-executed /setspawn always wins.
     * Falls back to the default world spawn if nothing is configured.
     */
    public Location getReturnSpawn() {
        if (database != null) {
            Location fromDb = deserializeLocation(database.getSetting("return_spawn"));
            if (fromDb != null && fromDb.getWorld() != null)
                return fromDb;
        }

        Location configured = getConfig().getLocation("settings.return-spawn");
        if (configured != null && configured.getWorld() != null)
            return configured;

        return getServer().getWorlds().get(0).getSpawnLocation();
    }

    private String serializeLocation(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";"
                + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    private Location deserializeLocation(String raw) {
        if (raw == null || raw.isEmpty())
            return null;
        String[] p = raw.split(";");
        if (p.length != 6)
            return null;
        World world = getServer().getWorld(p[0]);
        if (world == null)
            return null;
        try {
            return new Location(world,
                    Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
