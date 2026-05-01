package dev.zm.pvprooms.hooks.clans;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;

public interface ClanProvider {

    String getProviderName();

    String getClanName(Player player);

    boolean areInSameClan(Player player1, Player player2);

    default boolean isClanLeader(Player player) {
        return false;
    }

    default int getOnlineMembersCount(Player player) {
        return 0;
    }

    default Set<String> getAlliedClanNames(Player player) {
        return Collections.emptySet();
    }
}
