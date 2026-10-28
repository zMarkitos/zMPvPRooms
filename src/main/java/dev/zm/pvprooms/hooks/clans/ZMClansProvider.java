package dev.zm.pvprooms.hooks.clans;

import dev.zm.clan.api.ClanMemberSnapshot;
import dev.zm.clan.api.ClanSnapshot;
import dev.zm.clan.api.ZMClansAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ZMClansProvider implements ClanProvider {

    private final ZMClansAPI api;

    public ZMClansProvider(ZMClansAPI api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    @Override
    public String getProviderName() {
        return "zMClans";
    }

    @Override
    public String getClanName(Player player) {
        if (player == null) {
            return null;
        }
        return findClan(player).map(ClanSnapshot::name).orElse(null);
    }

    @Override
    public boolean areInSameClan(Player player1, Player player2) {
        if (player1 == null || player2 == null) {
            return false;
        }
        return findClan(player1)
                .flatMap(first -> findClan(player2)
                        .map(second -> first.id().equals(second.id())))
                .orElse(false);
    }

    @Override
    public boolean isClanLeader(Player player) {
        if (player == null) {
            return false;
        }
        return findClan(player)
                .map(clan -> player.getUniqueId().equals(clan.leaderId()))
                .orElse(false);
    }

    @Override
    public int getOnlineMembersCount(Player player) {
        if (player == null) {
            return 0;
        }
        ClanSnapshot clan = findClan(player).orElse(null);
        if (clan == null) {
            return 0;
        }

        int online = 0;
        for (ClanMemberSnapshot member : api.getMembers(clan.id())) {
            Player onlinePlayer = Bukkit.getPlayer(member.playerId());
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                online++;
            }
        }
        return online;
    }

    @Override
    public Set<String> getAlliedClanNames(Player player) {
        if (player == null) {
            return Set.of();
        }
        ClanSnapshot clan = findClan(player).orElse(null);
        if (clan == null || clan.allies().isEmpty()) {
            return Set.of();
        }

        Set<String> names = new HashSet<>(clan.allies().size());
        for (UUID allyId : clan.allies()) {
            api.getClan(allyId).map(ClanSnapshot::name).ifPresent(names::add);
        }
        return names;
    }

    private Optional<ClanSnapshot> findClan(Player player) {
        return api.getClanByPlayer(player.getUniqueId());
    }
}
