package dev.zm.pvprooms.hooks.clans;

import me.ulrich.clans.api.PlayerAPIManager;
import me.ulrich.clans.data.ClanData;
import me.ulrich.clans.interfaces.ClanAPI;
import me.ulrich.clans.interfaces.UClans;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Official UClans provider (no reflection).
 */
public class UClansProvider implements ClanProvider {

    private static final long CACHE_TTL_MS = 15_000L;

    private final PlayerAPIManager playerAPI;
    private final ClanAPI clanAPI;
    private final Map<UUID, CacheEntry> clanCache = new ConcurrentHashMap<>();

    public UClansProvider(UClans uClansApi) {
        this.playerAPI = uClansApi.getPlayerAPI();
        this.clanAPI = uClansApi.getClanAPI();
    }

    @Override
    public String getProviderName() {
        return "uClans";
    }

    @Override
    public String getClanName(Player player) {
        if (player == null || playerAPI == null) {
            return null;
        }

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        CacheEntry cached = clanCache.get(playerId);
        if (cached != null && cached.expiresAt >= now) {
            return cached.clanName;
        }

        String clanName = resolveClanName(playerId);
        clanCache.put(playerId, new CacheEntry(clanName, now + CACHE_TTL_MS));
        return clanName;
    }

    @Override
    public boolean areInSameClan(Player player1, Player player2) {
        String clan1 = getClanName(player1);
        String clan2 = getClanName(player2);
        return clan1 != null && clan2 != null
                && clan1.toLowerCase(Locale.ROOT).equals(clan2.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean isClanLeader(Player player) {
        if (player == null || playerAPI == null) {
            return false;
        }
        Optional<ClanData> clan = playerAPI.getPlayerClan(player.getUniqueId());
        return clan.isPresent() && player.getUniqueId().equals(clan.get().getLeader());
    }

    @Override
    public int getOnlineMembersCount(Player player) {
        if (player == null || playerAPI == null) {
            return 0;
        }
        Optional<ClanData> clan = playerAPI.getPlayerClan(player.getUniqueId());
        if (!clan.isPresent() || clan.get().getOnlineMembers() == null) {
            return 0;
        }
        return clan.get().getOnlineMembers().size();
    }

    @Override
    public Set<String> getAlliedClanNames(Player player) {
        Set<String> allies = new HashSet<>();
        if (player == null || playerAPI == null || clanAPI == null) {
            return allies;
        }

        Optional<ClanData> clanOpt = playerAPI.getPlayerClan(player.getUniqueId());
        if (!clanOpt.isPresent()) {
            return allies;
        }

        ClanData own = clanOpt.get();
        if (own.getId() == null) {
            return allies;
        }

        for (ClanData ally : clanAPI.getAlliances(own.getId())) {
            if (ally == null) {
                continue;
            }
            String tag = ally.getTagNoColor();
            if (tag == null || tag.trim().isEmpty()) {
                tag = ally.getTag();
            }
            if (tag != null && !tag.trim().isEmpty()) {
                allies.add(tag.trim());
            }
        }
        return allies;
    }

    public void invalidatePlayer(UUID playerId) {
        if (playerId != null) {
            clanCache.remove(playerId);
        }
    }

    public void clearCache() {
        clanCache.clear();
    }

    private String resolveClanName(UUID playerId) {
        Optional<ClanData> clanDataOptional = playerAPI.getPlayerClan(playerId);
        if (!clanDataOptional.isPresent()) {
            return null;
        }

        ClanData clanData = clanDataOptional.get();
        String noColorTag = clanData.getTagNoColor();
        if (noColorTag != null && !noColorTag.trim().isEmpty()) {
            return noColorTag;
        }

        String tag = clanData.getTag();
        if (tag != null && !tag.trim().isEmpty()) {
            return tag;
        }
        return null;
    }

    private static final class CacheEntry {
        private final String clanName;
        private final long expiresAt;

        private CacheEntry(String clanName, long expiresAt) {
            this.clanName = clanName;
            this.expiresAt = expiresAt;
        }
    }
}
