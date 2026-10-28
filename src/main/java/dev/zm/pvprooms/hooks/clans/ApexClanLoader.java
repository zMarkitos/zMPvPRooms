package dev.zm.pvprooms.hooks.clans;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class ApexClanLoader {

    private static final String CLAN_SERVICE_CLASS = "org.apex.clan.api.ClanService";
    private static final String[] PLUGIN_NAMES = { "ApexClan", "Apex Clan", "apexclan" };

    private ApexClanLoader() {
    }

    public static ClanProvider tryLoad(PluginManager pluginManager, Logger logger) {
        Plugin plugin = getPlugin(pluginManager);
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }

        try {
            Class<?> serviceClass = plugin.getClass().getClassLoader().loadClass(CLAN_SERVICE_CLASS);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(serviceClass);
            if (registration == null) {
                logger.warning("[ApexClanLoader] ApexClan is enabled but ClanService is not registered.");
                return null;
            }

            Object provider = registration.getProvider();
            if (provider == null) {
                logger.warning("[ApexClanLoader] ClanService provider is null.");
                return null;
            }

            logger.info("[ApexClanLoader] Successfully hooked into ApexClan.");
            return new ApexClanProvider(provider, logger);
        } catch (ClassNotFoundException error) {
            logger.warning("[ApexClanLoader] ClanService class not found.");
        } catch (LinkageError error) {
            logger.warning("[ApexClanLoader] ApexClan API is not compatible with this server.");
        } catch (ReflectiveOperationException error) {
            logger.log(Level.WARNING, "[ApexClanLoader] Failed to hook ApexClan.", error);
        }
        return null;
    }

    private static Plugin getPlugin(PluginManager pluginManager) {
        for (String name : PLUGIN_NAMES) {
            Plugin plugin = pluginManager.getPlugin(name);
            if (plugin != null && plugin.isEnabled()) {
                return plugin;
            }
        }
        return null;
    }
}
