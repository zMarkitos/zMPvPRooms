package dev.zm.pvprooms.hooks.clans;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ApexClanProvider implements ClanProvider {

    private final Object clanService;
    private final Logger logger;
    private final Method getClanMethod;
    private final Map<Class<?>, Method[]> labelMethods = new ConcurrentHashMap<>();

    public ApexClanProvider(Object clanService, Logger logger) throws NoSuchMethodException {
        this.clanService = clanService;
        this.logger = logger;
        this.getClanMethod = clanService.getClass().getMethod("getClan", UUID.class);
    }

    @Override
    public String getProviderName() {
        return "ApexClan";
    }

    @Override
    public String getClanName(Player player) {
        if (player == null) {
            return null;
        }
        try {
            Optional<?> clan = getClan(player.getUniqueId());
            return clan.map(this::resolveClanLabel).orElse(null);
        } catch (ReflectiveOperationException error) {
            logger.fine("[ApexClanProvider] Failed to resolve clan for " + player.getName() + ": " + error.getMessage());
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
        return Collections.emptySet();
    }

    private Optional<?> getClan(UUID playerId) throws ReflectiveOperationException {
        Object result = getClanMethod.invoke(clanService, playerId);
        if (result instanceof Optional<?> optional) {
            return optional;
        }
        return Optional.ofNullable(result);
    }

    private String resolveClanLabel(Object clan) {
        if (clan == null) {
            return null;
        }
        Method[] methods = labelMethods.computeIfAbsent(clan.getClass(), this::resolveLabelMethods);
        for (Method method : methods) {
            if (method == null) {
                continue;
            }
            try {
                Object value = method.invoke(clan);
                if (value instanceof String text && !text.trim().isEmpty()) {
                    return text.trim();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private Method[] resolveLabelMethods(Class<?> type) {
        Method[] methods = new Method[2];
        methods[0] = findMethod(type, "getTag");
        methods[1] = findMethod(type, "getName");
        return methods;
    }

    private Method findMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
