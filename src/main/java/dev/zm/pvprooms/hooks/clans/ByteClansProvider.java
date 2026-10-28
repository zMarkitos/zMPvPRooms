package dev.zm.pvprooms.hooks.clans;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ByteClansProvider implements ClanProvider {

    private final Object api;
    private final Method getMemberMethod;
    private final Map<Class<?>, Map<String, Method>> methodCache = new ConcurrentHashMap<>();

    public ByteClansProvider(Object api) throws NoSuchMethodException {
        this.api = api;
        this.getMemberMethod = api.getClass().getMethod("getClanMemberOrNull", UUID.class);
    }

    @Override
    public String getProviderName() {
        return "ByteClans";
    }

    @Override
    public String getClanName(Player player) {
        if (player == null) {
            return null;
        }
        try {
            Object clan = getClan(player);
            return clan == null ? null : resolveClanLabel(clan);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Override
    public boolean areInSameClan(Player player1, Player player2) {
        String clan1 = getClanName(player1);
        String clan2 = getClanName(player2);
        return clan1 != null && clan2 != null && clan1.equalsIgnoreCase(clan2);
    }

    @Override
    public boolean isClanLeader(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Object member = getMember(player);
            if (member == null) {
                return false;
            }
            Object role = invokeOptional(member, "getRole");
            if (role == null) {
                return false;
            }
            String roleName = role.toString();
            return roleName.equalsIgnoreCase("LEADER") || roleName.equalsIgnoreCase("OWNER");
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Override
    public int getOnlineMembersCount(Player player) {
        if (player == null) {
            return 0;
        }
        try {
            Object clan = getClan(player);
            if (clan == null) {
                return 0;
            }
            Object onlineMembers = invokeOptional(clan, "getOnlineMembers");
            return onlineMembers instanceof Collection<?> members ? members.size() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    @Override
    public Set<String> getAlliedClanNames(Player player) {
        if (player == null) {
            return Set.of();
        }
        try {
            Object clan = getClan(player);
            if (clan == null) {
                return Set.of();
            }

            Object alliances = invokeOptional(clan, "getAllies");
            if (!(alliances instanceof Collection<?>)) {
                alliances = invokeOptional(clan, "getAlliances");
            }
            if (!(alliances instanceof Collection<?> collection)) {
                return Set.of();
            }

            Set<String> allies = new HashSet<>(collection.size());
            for (Object ally : collection) {
                if (ally == null) {
                    continue;
                }
                String label = resolveClanLabel(ally);
                if (label != null) {
                    allies.add(label);
                }
            }
            return allies;
        } catch (ReflectiveOperationException ignored) {
            return Set.of();
        }
    }

    private Object getMember(Player player) throws ReflectiveOperationException {
        return getMemberMethod.invoke(api, player.getUniqueId());
    }

    private Object getClan(Player player) throws ReflectiveOperationException {
        Object member = getMember(player);
        if (member == null) {
            return null;
        }
        return method(member, "getClan").invoke(member);
    }

    private String resolveClanLabel(Object clan) throws ReflectiveOperationException {
        Object tag = invokeOptional(clan, "getTag");
        if (tag instanceof String value && !value.trim().isEmpty()) {
            return value;
        }
        Object name = invokeOptional(clan, "getName");
        if (name instanceof String value && !value.trim().isEmpty()) {
            return value;
        }
        return null;
    }

    private Object invokeOptional(Object target, String methodName) throws ReflectiveOperationException {
        try {
            return method(target, methodName).invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Method method(Object target, String methodName) throws NoSuchMethodException {
        Map<String, Method> methods = methodCache.computeIfAbsent(
                target.getClass(), ignored -> new ConcurrentHashMap<>());
        Method method = methods.get(methodName);
        if (method == null) {
            method = target.getClass().getMethod(methodName);
            methods.put(methodName, method);
        }
        return method;
    }
}
