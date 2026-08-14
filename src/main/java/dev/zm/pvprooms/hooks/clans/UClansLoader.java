package dev.zm.pvprooms.hooks.clans;

import me.ulrich.clans.Clans;
import me.ulrich.clans.interfaces.UClans;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

/**
 * Isolated loader for the UltimateClans / uClans hook.
 *
 */
public final class UClansLoader {

    private UClansLoader() {
    }

    public static ClanProvider tryLoad(org.bukkit.plugin.PluginManager pluginManager,
            java.util.logging.Logger logger) {
        Plugin plugin = findPlugin(pluginManager);
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }

        UClans api;
        if (plugin instanceof UClans) {
            api = (UClans) plugin;
        } else if (plugin instanceof Clans) {
            api = (Clans) plugin;
        } else {
            logger.warning("[UClansLoader] Found plugin '" + plugin.getName()
                    + "' but it does not match the expected UClans API type.");
            return null;
        }

        if (api.getPlayerAPI() == null) {
            logger.warning("[UClansLoader] UClans PlayerAPI is null — skipping hook.");
            return null;
        }

        logger.info("[UClansLoader] Successfully hooked into: " + plugin.getName());
        return new UClansProvider(api);
    }

    private static Plugin findPlugin(org.bukkit.plugin.PluginManager pm) {
        // Try well-known plugin names first
        for (String name : new String[] { "uClans", "UClans", "UltimateClans" }) {
            Plugin p = pm.getPlugin(name);
            if (p != null && p.isEnabled()) {
                return p;
            }
        }
        // Fallback: scan all plugins by their main class
        for (Plugin p : pm.getPlugins()) {
            if (p == null || !p.isEnabled())
                continue;
            PluginDescriptionFile desc = p.getDescription();
            if (desc != null && "me.ulrich.clans.Clans".equalsIgnoreCase(desc.getMain())) {
                return p;
            }
        }
        return null;
    }
}
