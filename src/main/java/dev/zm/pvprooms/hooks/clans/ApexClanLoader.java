package dev.zm.pvprooms.hooks.clans;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

public final class ApexClanLoader {

    private static final String CLAN_SERVICE_CLASS = "org.apex.clan.api.ClanService";

    private ApexClanLoader() {}

    public static ClanProvider tryLoad(PluginManager pluginManager, Logger logger) {
        Plugin plugin = getPlugin(pluginManager);
        if (plugin == null || !plugin.isEnabled()) {
            logger.warning("[ApexClanLoader] ApexClan plugin not found or disabled.");
            return null;
        }

        try {
            Class<?> serviceClass = plugin.getClass().getClassLoader().loadClass(CLAN_SERVICE_CLASS);
            logger.info("[ApexClanLoader] Found API class: " + serviceClass.getName());

            // Obtenemos el servicio registrado
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(serviceClass);
            if (registration == null) {
                logger.warning("[ApexClanLoader] ApexClan found but ClanService is not registered. " +
                               "Ensure the ApexClan plugin is enabled and registers the service.");
                return null;
            }

            Object provider = registration.getProvider();
            if (provider == null) {
                logger.warning("[ApexClanLoader] ClanService provider is null.");
                return null;
            }

            logger.info("[ApexClanLoader] Successfully hooked into ApexClan.");
            return new ApexClanProvider(provider, logger);

        } catch (ClassNotFoundException ex) {
            logger.warning("[ApexClanLoader] ClanService class not found. " +
                           "Make sure the ApexClan API JAR is on the classpath (or plugin is installed).");
        } catch (Exception ex) {
            logger.warning("[ApexClanLoader] Failed to hook ApexClan: " + ex.getMessage());
            ex.printStackTrace();
        }
        return null;
    }

    private static Plugin getPlugin(PluginManager pluginManager) {
        for (String name : new String[]{"ApexClan", "Apex Clan", "apexclan"}) {
            Plugin p = pluginManager.getPlugin(name);
            if (p != null) return p;
        }
        return null;
    }
}