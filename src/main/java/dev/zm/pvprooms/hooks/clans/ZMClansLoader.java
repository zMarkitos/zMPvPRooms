package dev.zm.pvprooms.hooks.clans;

import dev.zm.clan.api.ZMClansAPI;
import dev.zm.clan.zMClans;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.logging.Logger;

public final class ZMClansLoader {

    private static final String PLUGIN_NAME = "zMClans";

    private ZMClansLoader() {
    }

    public static ClanProvider tryLoad(PluginManager pluginManager, Logger logger) {
        Plugin plugin = pluginManager.getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        if (!(plugin instanceof zMClans clans)) {
            logger.warning("[zMClans] Found plugin '" + PLUGIN_NAME
                    + "' but it is not the expected zMClans implementation.");
            return null;
        }

        ZMClansAPI api = clans.getAPI();
        if (api == null) {
            logger.warning("[zMClans] The API is not available; skipping hook.");
            return null;
        }

        logger.info("[zMClans] Successfully hooked into zMClans.");
        return new ZMClansProvider(api);
    }
}
