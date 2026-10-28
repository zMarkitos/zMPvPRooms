package dev.zm.pvprooms.hooks.clans;

import me.ulrich.clans.Clans;
import me.ulrich.clans.interfaces.UClans;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import java.util.UUID;
import java.util.logging.Logger;

public final class UClansLoader {

    private static final String[] PLUGIN_NAMES = { "UltimateClans", "uClans", "UClans" };

    private UClansLoader() {
    }

    public static ClanProvider tryLoad(org.bukkit.plugin.PluginManager pluginManager, Logger logger) {
        Plugin plugin = findPlugin(pluginManager);
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        if (!isApiCompatible(plugin, logger)) {
            return null;
        }

        UClans api;
        if (plugin instanceof UClans uClans) {
            api = uClans;
        } else if (plugin instanceof Clans clans) {
            api = clans;
        } else {
            logger.warning("[UClansLoader] Found plugin '" + plugin.getName()
                    + "' but it does not match the expected UClans API type.");
            return null;
        }

        if (api.getPlayerAPI() == null) {
            logger.warning("[UClansLoader] UClans PlayerAPI is null; skipping hook.");
            return null;
        }

        String version = plugin.getDescription().getVersion();
        logger.info("[UClansLoader] Successfully hooked into " + plugin.getName()
                + " v" + version + ".");
        return new UClansProvider(api, plugin.getName());
    }

    private static boolean isApiCompatible(Plugin plugin, Logger logger) {
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> uClans = Class.forName("me.ulrich.clans.interfaces.UClans", false, loader);
            Class<?> playerApi = Class.forName("me.ulrich.clans.api.PlayerAPIManager", false, loader);
            Class<?> clanApi = Class.forName("me.ulrich.clans.interfaces.ClanAPI", false, loader);
            Class<?> clanData = Class.forName("me.ulrich.clans.data.ClanData", false, loader);

            uClans.getMethod("getPlayerAPI");
            uClans.getMethod("getClanAPI");
            playerApi.getMethod("getPlayerClan", UUID.class);
            clanApi.getMethod("getAlliances", UUID.class);
            clanData.getMethod("getId");
            clanData.getMethod("getLeader");
            clanData.getMethod("getOnlineMembers");
            clanData.getMethod("getTag");
            clanData.getMethod("getTagNoColor");
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            logger.warning("[UClansLoader] Installed UltimateClans/uClans API is incompatible; skipping hook.");
            return false;
        }
    }

    private static Plugin findPlugin(org.bukkit.plugin.PluginManager pluginManager) {
        for (String name : PLUGIN_NAMES) {
            Plugin plugin = pluginManager.getPlugin(name);
            if (plugin != null && plugin.isEnabled()) {
                return plugin;
            }
        }

        for (Plugin candidate : pluginManager.getPlugins()) {
            if (candidate == null || !candidate.isEnabled()) {
                continue;
            }
            PluginDescriptionFile description = candidate.getDescription();
            if (description != null && description.getMain() != null
                    && "me.ulrich.clans.Clans".equalsIgnoreCase(description.getMain())) {
                return candidate;
            }
        }
        return null;
    }
}
