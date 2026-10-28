package dev.zm.pvprooms.hooks.clans;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.logging.Logger;

public final class ByteClansLoader {

    private static final String API_CLASS = "team.bytephoria.byteclans.bukkitapi.ByteClans";

    private ByteClansLoader() {
    }

    public static ClanProvider tryLoad(org.bukkit.plugin.PluginManager pluginManager, Logger logger) {
        Plugin plugin = findPlugin(pluginManager);
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }

        Object api;
        try {
            Class<?> apiClass = Class.forName(API_CLASS, true, plugin.getClass().getClassLoader());
            Method getAPI = apiClass.getMethod("getAPI");
            api = getAPI.invoke(null);
        } catch (ReflectiveOperationException | LinkageError error) {
            logger.warning("[ByteClansLoader] Found plugin '" + plugin.getName()
                    + "' but failed to obtain its API.");
            return null;
        }

        if (api == null) {
            logger.warning("[ByteClansLoader] ByteClans API is null; skipping hook.");
            return null;
        }

        try {
            logger.info("[ByteClansLoader] Successfully hooked into: " + plugin.getName());
            return new ByteClansProvider(api);
        } catch (NoSuchMethodException error) {
            logger.warning("[ByteClansLoader] ByteClans API is missing expected methods; skipping hook.");
            return null;
        }
    }

    private static Plugin findPlugin(org.bukkit.plugin.PluginManager pluginManager) {
        Plugin plugin = pluginManager.getPlugin("ByteClans");
        if (plugin != null && plugin.isEnabled()) {
            return plugin;
        }

        for (Plugin candidate : pluginManager.getPlugins()) {
            if (candidate == null || !candidate.isEnabled()) {
                continue;
            }
            PluginDescriptionFile description = candidate.getDescription();
            if (description != null && description.getMain() != null
                    && description.getMain().toLowerCase(Locale.ROOT).contains("byteclans")) {
                return candidate;
            }
        }
        return null;
    }
}
