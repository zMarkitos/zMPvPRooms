package dev.zm.pvprooms.hooks.clans;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import java.lang.reflect.Method;

/**
 * Isolated loader for the ByteClans hook using reflection.
 */
public final class ByteClansLoader {

    private ByteClansLoader() {}

    /**
     * Attempts to build a {@link ByteClansProvider} by locating an enabled ByteClans plugin.
     */
    public static ClanProvider tryLoad(org.bukkit.plugin.PluginManager pluginManager,
                                       java.util.logging.Logger logger) {
        Plugin plugin = findPlugin(pluginManager);
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }

        Object api;
        try {
            Class<?> byteClansClass = Class.forName("team.bytephoria.byteclans.bukkitapi.ByteClans");
            Method getAPIMethod = byteClansClass.getMethod("getAPI");
            api = getAPIMethod.invoke(null);
        } catch (Exception e) {
            logger.warning("[ByteClansLoader] Found plugin '" + plugin.getName()
                    + "' but failed to get its API via reflection.");
            return null;
        }

        if (api == null) {
            logger.warning("[ByteClansLoader] ByteClans API is null — skipping hook.");
            return null;
        }

        logger.info("[ByteClansLoader] Successfully hooked into: " + plugin.getName());
        return new ByteClansProvider(api);
    }

    private static Plugin findPlugin(org.bukkit.plugin.PluginManager pm) {
        // Try well-known plugin names first
        Plugin p = pm.getPlugin("ByteClans");
        if (p != null && p.isEnabled()) {
            return p;
        }
        
        // Fallback: scan all plugins by their main class
        for (Plugin pl : pm.getPlugins()) {
            if (pl == null || !pl.isEnabled()) continue;
            PluginDescriptionFile desc = pl.getDescription();
            if (desc != null && desc.getMain() != null && desc.getMain().toLowerCase().contains("byteclans")) {
                return pl;
            }
        }
        return null;
    }
}
