package dev.zm.pvprooms.hooks.clans;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ByteClansProvider implements ClanProvider {

    private final Object api;

    public ByteClansProvider(Object api) {
        this.api = api;
    }

    @Override
    public String getProviderName() {
        return "ByteClans";
    }

    @Override
    public String getClanName(Player player) {
        if (player == null || api == null) return null;
        
        try {
            Object member = api.getClass().getMethod("getClanMemberOrNull", java.util.UUID.class).invoke(api, player.getUniqueId());
            if (member == null) return null;
            
            // Try to get clan
            Object clan = member.getClass().getMethod("getClan").invoke(member);
            if (clan == null) return null;
            
            // Try to get tag or name
            try {
                return (String) clan.getClass().getMethod("getTag").invoke(clan);
            } catch (Exception e) {
                return (String) clan.getClass().getMethod("getName").invoke(clan);
            }
        } catch (Exception e) {
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
        if (player == null || api == null) return false;
        
        try {
            Object member = api.getClass().getMethod("getClanMemberOrNull", java.util.UUID.class).invoke(api, player.getUniqueId());
            if (member == null) return false;
            
            // Check role
            Object role = member.getClass().getMethod("getRole").invoke(member);
            if (role != null) {
                String roleName = role.toString();
                return roleName.equalsIgnoreCase("LEADER") || roleName.equalsIgnoreCase("OWNER");
            }
        } catch (Exception e) {
            // Ignored
        }
        return false;
    }

    @Override
    public int getOnlineMembersCount(Player player) {
        if (player == null || api == null) return 0;
        
        try {
            Object member = api.getClass().getMethod("getClanMemberOrNull", java.util.UUID.class).invoke(api, player.getUniqueId());
            if (member == null) return 0;
            
            Object clan = member.getClass().getMethod("getClan").invoke(member);
            if (clan == null) return 0;
            
            java.util.Collection<?> onlineMembers = (java.util.Collection<?>) clan.getClass().getMethod("getOnlineMembers").invoke(clan);
            return onlineMembers != null ? onlineMembers.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public Set<String> getAlliedClanNames(Player player) {
        Set<String> allies = new HashSet<>();
        if (player == null || api == null) return allies;
        
        try {
            Object member = api.getClass().getMethod("getClanMemberOrNull", java.util.UUID.class).invoke(api, player.getUniqueId());
            if (member == null) return allies;
            
            Object clan = member.getClass().getMethod("getClan").invoke(member);
            if (clan == null) return allies;
            
            try {
                java.util.Collection<?> alliances = (java.util.Collection<?>) clan.getClass().getMethod("getAllies").invoke(clan);
                if (alliances != null) {
                    for (Object ally : alliances) {
                        try {
                            String tag = (String) ally.getClass().getMethod("getTag").invoke(ally);
                            if (tag != null) allies.add(tag);
                        } catch (Exception ex) {
                            String name = (String) ally.getClass().getMethod("getName").invoke(ally);
                            if (name != null) allies.add(name);
                        }
                    }
                }
            } catch (Exception e) {
                // If getAllies is not found or fails, try getAlliances()
                java.util.Collection<?> alliances = (java.util.Collection<?>) clan.getClass().getMethod("getAlliances").invoke(clan);
                if (alliances != null) {
                    for (Object ally : alliances) {
                        try {
                            String tag = (String) ally.getClass().getMethod("getTag").invoke(ally);
                            if (tag != null) allies.add(tag);
                        } catch (Exception ex) {
                            String name = (String) ally.getClass().getMethod("getName").invoke(ally);
                            if (name != null) allies.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignored
        }
        return allies;
    }
}
