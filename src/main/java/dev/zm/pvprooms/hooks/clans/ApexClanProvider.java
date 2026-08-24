package dev.zm.pvprooms.hooks.clans;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.logging.Logger;

/**
 * Reflection-based provider for ApexClan.
 *
 * This keeps the plugin optional at runtime: if ApexClan is not installed or
 * the API changes, the hook simply stays disabled instead of breaking startup.
 */
public final class ApexClanProvider implements ClanProvider {

    private final Object clanService;
    private final Logger logger;
    private final Method getClanMethod;
    private final Method getClanByNameMethod;
    private final Method getRelationMethod;

    public ApexClanProvider(Object clanService, Logger logger) throws NoSuchMethodException {
        this.clanService = clanService;
        this.logger = logger;
        Class<?> serviceClass = clanService.getClass();
        this.getClanMethod = serviceClass.getMethod("getClan", UUID.class);
        this.getClanByNameMethod = findOptionalMethod(serviceClass, "getClanByName", String.class);
        this.getRelationMethod = findOptionalMethod(serviceClass, "getRelation", UUID.class, UUID.class);
    }

    @Override
    public String getProviderName() {
        return "ApexClan";
    }

    @Override
    public String getClanName(Player player) {
        if (player == null || clanService == null) {
            return null;
        }

        try {
            Optional<?> clan = getClan(player.getUniqueId());
            if (!clan.isPresent()) {
                return null;
            }
            return resolveClanLabel(clan.get());
        } catch (Exception ex) {
            logger.fine("[ApexClanProvider] Failed to resolve clan for " + player.getName() + ": " + ex.getMessage());
            return null;
        }
    }

    @Override
    public boolean areInSameClan(Player player1, Player player2) {
        String clan1 = getClanName(player1);
        String clan2 = getClanName(player2);
        return clan1 != null && clan2 != null
                && clan1.toLowerCase(Locale.ROOT).equals(clan2.toLowerCase(Locale.ROOT));
    }

    @Override
    public Set<String> getAlliedClanNames(Player player) {
        if (player == null || clanService == null) {
            return Collections.emptySet();
        }
        return new HashSet<>();
    }

    private Optional<?> getClan(UUID playerId) throws Exception {
        Object result = getClanMethod.invoke(clanService, playerId);
        if (result instanceof Optional) {
            return (Optional<?>) result;
        }
        return Optional.ofNullable(result);
    }

    private String resolveClanLabel(Object clan) throws Exception {
        if (clan == null) {
            return null;
        }

        for (String methodName : new String[] { "getTag", "getName" }) {
            try {
                Object value = clan.getClass().getMethod(methodName).invoke(clan);
                if (value instanceof String && !((String) value).trim().isEmpty()) {
                    return ((String) value).trim();
                }
            } catch (NoSuchMethodException ignored) {
                // Try next candidate.
            }
        }
        return null;
    }

    private static Method findOptionalMethod(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
