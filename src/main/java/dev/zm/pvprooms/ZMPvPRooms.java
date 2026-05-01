package dev.zm.pvprooms;

import dev.zm.pvprooms.commands.MainCommand;
import dev.zm.pvprooms.commands.MainTabCompleter;
import dev.zm.pvprooms.database.SQLiteDatabase;
import dev.zm.pvprooms.hooks.RoomsPlaceholderExpansion;
import dev.zm.pvprooms.hooks.clans.ClanProvider;
import dev.zm.pvprooms.hooks.clans.UClansProvider;
import dev.zm.pvprooms.hooks.worldguard.WorldGuardHook;
import dev.zm.pvprooms.listeners.BetListener;
import dev.zm.pvprooms.listeners.EditorListener;
import dev.zm.pvprooms.listeners.RoomListener;
import dev.zm.pvprooms.managers.BetManager;
import dev.zm.pvprooms.managers.ConfigManager;
import dev.zm.pvprooms.managers.EditorManager;
import dev.zm.pvprooms.managers.MatchManager;
import dev.zm.pvprooms.managers.RoomManager;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.models.enums.RoomState;
import me.ulrich.clans.Clans;
import me.ulrich.clans.interfaces.UClans;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import dev.zm.pvprooms.utils.VersionChecker;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class ZMPvPRooms extends JavaPlugin {

    private static ZMPvPRooms instance;

    private ConfigManager configManager;
    private RoomManager roomManager;
    private SQLiteDatabase database;
    private MatchManager matchManager;
    private BetManager betManager;
    private EditorManager editorManager;
    private ClanProvider clanProvider;
    private WorldGuardHook worldGuardHook;
    private VersionChecker versionChecker;

    private void log(String message) {
        getServer().getConsoleSender().sendMessage(color(message));
    }

    private String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public void onEnable() {
        instance = this;
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

        log("&c✘ &fPlugin disabled.");
    }

    private void loadConfigurations() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
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
            new RoomsPlaceholderExpansion(this, "rooms").register();
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
        if (!getConfig().getBoolean("hooks.clans.enabled", true))
            return;

        List<String> priority = getConfig().getStringList("hooks.clans.provider-priority");
        if (priority.isEmpty()) {
            priority = Arrays.asList("uClans", "UltimateClans");
        }

        for (String providerName : priority) {
            ClanProvider provider = tryLoadProvider(providerName);
            if (provider != null) {
                clanProvider = provider;
                return;
            }
        }
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

    private ClanProvider tryLoadProvider(String providerName) {
        String key = providerName.toLowerCase(java.util.Locale.ROOT);
        if (key.equals("ultimateclans") || key.equals("uclans") || key.equals("uclansapi")) {
            if (!getConfig().getBoolean("hooks.clans.ultimateclans", true))
                return null;
            return loadUClansProvider();
        }
        return null;
    }

    private ClanProvider loadUClansProvider() {
        Plugin plugin = findFirstEnabledPlugin("uClans", "UClans", "UltimateClans");
        if (plugin == null) {
            plugin = findPluginByMainClass("me.ulrich.clans.Clans");
        }
        if (plugin == null || !plugin.isEnabled())
            return null;

        UClans api;
        if (plugin instanceof UClans) {
            api = (UClans) plugin;
        } else if (plugin instanceof Clans) {
            api = (Clans) plugin;
        } else {
            getLogger().warning("UltimateClans found but does not match the expected API type.");
            return null;
        }

        if (api.getPlayerAPI() == null) {
            getLogger().warning("UClans PlayerAPI is null.");
            return null;
        }

        getLogger().info("UClans hooked: " + plugin.getName());
        return new UClansProvider(api);
    }

    private Plugin findFirstEnabledPlugin(String... names) {
        for (String name : names) {
            Plugin p = getServer().getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled())
                return p;
        }
        return null;
    }

    private Plugin findPluginByMainClass(String mainClass) {
        for (Plugin p : getServer().getPluginManager().getPlugins()) {
            if (p == null || !p.isEnabled())
                continue;
            PluginDescriptionFile desc = p.getDescription();
            if (desc != null && mainClass.equalsIgnoreCase(desc.getMain()))
                return p;
        }
        return null;
    }

    // ---- Accessors ----

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

    public ClanProvider getClanProvider() {
        return clanProvider;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    public VersionChecker getVersionChecker() {
        return versionChecker;
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

    // ---- Return spawn (persisted in DB + config.yml) ----

    /**
     * Saves the return spawn to both config.yml and the database so it
     * survives server restarts regardless of how config is loaded.
     */
    public void setReturnSpawn(Location location) {
        if (location == null || location.getWorld() == null)
            return;
        getConfig().set("settings.return-spawn", location);
        // saveConfig(); // Removed to prevent Bukkit from stripping config.yml comments
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
