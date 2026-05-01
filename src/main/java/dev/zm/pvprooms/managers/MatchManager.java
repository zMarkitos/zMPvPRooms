package dev.zm.pvprooms.managers;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.hooks.worldguard.WorldGuardHook;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.models.enums.RoomState;
import dev.zm.pvprooms.models.enums.RoomType;
import dev.zm.pvprooms.utils.ArenaBoundaryScanner;
import dev.zm.pvprooms.utils.CC;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;


public class MatchManager {

    private final ZMPvPRooms plugin;
    private final Map<String, BukkitTask> countdownTasks = new HashMap<>();
    private final Map<String, BukkitTask> timeLimitTasks = new HashMap<>();
    private final Map<String, Map<UUID, Integer>> roomTeams = new HashMap<>();
    private final Map<String, List<PlacedDoorBlock>> placedDoorBlocks = new HashMap<>();
    private final Map<UUID, GameMode> spectatorModes = new HashMap<>();
    private final Set<UUID> spawnOnJoin = new HashSet<>();
    private final Map<String, BossBar> duelBossBars = new HashMap<>();
    private final Map<String, BukkitTask> duelBossBarTasks = new HashMap<>();
    private final Map<String, Integer> roomTimeRemaining = new HashMap<>();
    private final Map<String, ClanLobby> clanLobbies = new HashMap<>();
    private final Set<UUID> pendingRespawnTeleport = new HashSet<>();

    public MatchManager(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    public boolean joinRoom(Player player, Room room) {
        return joinRoomInternal(player, room, false);
    }

    public boolean forceJoinRoom(Player player, Room room) {
        return joinRoomInternal(player, room, true);
    }

    private boolean joinRoomInternal(Player player, Room room, boolean bypassClanValidation) {
        if (plugin.getRoomManager().getRoomByPlayer(player).isPresent()) {
            player.sendMessage(plugin.getConfigManager().getMessage("already_in_room"));
            return false;
        }

        if (room.getState() != RoomState.WAITING) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_not_waiting"));
            return false;
        }

        if (room.isFull()) {
            player.sendMessage(plugin.getConfigManager().getMessage("room_full"));
            return false;
        }

        if (!bypassClanValidation && room.getType() == RoomType.CLAN) {
            if (!validateClanJoin(player, room)) {
                return false;
            }
        }

        room.addPlayer(player);
        sendJoinFeedback(player, room);

        if (room.isChatEnabled()) {
            if (room.getType() == RoomType.CLAN && plugin.getClanProvider() != null) {
                String clanName = plugin.getClanProvider().getClanName(player);
                room.broadcast(plugin.getConfigManager().getMessage("clan_room_joined_broadcast")
                        .replace("%player%", player.getName())
                        .replace("%clan%", safeClanName(clanName))
                        .replace("%room%", room.getName())
                        .replace("%current%", String.valueOf(room.getPlayers().size()))
                        .replace("%max%", String.valueOf(room.getCapacity())));
            } else {
                room.broadcast(plugin.getConfigManager().getMessage("room_joined_broadcast")
                        .replace("%player%", player.getName())
                        .replace("%current%", String.valueOf(room.getPlayers().size()))
                        .replace("%max%", String.valueOf(room.getCapacity())));
            }
        }

        boolean canStart = room.hasEnoughPlayersToStart();
        if (canStart && room.getType() == RoomType.CLAN) {
            ClanLobby lobby = clanLobbies.get(room.getName().toLowerCase());
            canStart = lobby != null && lobby.opponentClan != null;
        }
        if (canStart) {
            startCountdown(room, false);
        }

        return true;
    }

    public void startClanMatch(Player initiator, Room room) {
        if (room.getType() != RoomType.CLAN) {
            initiator.sendMessage(plugin.getConfigManager().getMessage("clan_only_room"));
            return;
        }

        if (room.getState() != RoomState.WAITING) {
            initiator.sendMessage(plugin.getConfigManager().getMessage("room_not_waiting"));
            return;
        }

        if (plugin.getClanProvider() == null) {
            initiator.sendMessage(plugin.getConfigManager().getMessage("clan_system_disabled"));
            return;
        }

        String clanName = plugin.getClanProvider().getClanName(initiator);
        if (clanName == null || clanName.isEmpty()) {
            initiator.sendMessage(plugin.getConfigManager().getMessage("no_clan"));
            return;
        }
        if (!plugin.getClanProvider().isClanLeader(initiator)) {
            initiator.sendMessage(plugin.getConfigManager().getMessage("clan_only_leader_start"));
            return;
        }

        int required = room.getPlayersPerTeam();
        int availableOwn = plugin.getClanProvider().getOnlineMembersCount(initiator);
        int availableAllies = 0;
        for (String ally : plugin.getClanProvider().getAlliedClanNames(initiator)) {
            availableAllies += countOnlineByClan(ally);
        }
        if ((availableOwn + availableAllies) < required) {
            initiator.sendMessage(plugin.getConfigManager().getMessage("clan_not_enough_members")
                    .replace("%required%", String.valueOf(required))
                    .replace("%own%", String.valueOf(availableOwn))
                    .replace("%allys%", String.valueOf(availableAllies)));
            return;
        }

        ClanLobby existing = clanLobbies.get(room.getName().toLowerCase());
        if (existing != null) {
            initiator.sendMessage(plugin.getConfigManager().getMessage("clan_lobby_already_open")
                    .replace("%room%", room.getName()));
            return;
        }

        ClanLobby lobby = new ClanLobby(initiator.getUniqueId(), clanName);
        clanLobbies.put(room.getName().toLowerCase(), lobby);
        scheduleClanLobbyTimeout(room);
        if (!room.getPlayers().contains(initiator.getUniqueId())) {
            forceJoinRoom(initiator, room);
        }

        if (room.isChatEnabled()) {
            String msg = plugin.getConfigManager().getMessage("clan_challenge")
                    .replace("%clan1%", clanName)
                    .replace("%room%", room.getName());
            Bukkit.broadcastMessage(CC.translate(msg));
        }
        initiator.sendMessage(plugin.getConfigManager().getMessage("clan_lobby_started")
                .replace("%room%", room.getName())
                .replace("%clan%", safeClanName(clanName)));
    }

    public void inviteClan(Player inviter, Room room, String clanName) {
        ClanLobby lobby = clanLobbies.get(room.getName().toLowerCase());
        if (lobby == null || room.getState() != RoomState.WAITING) {
            inviter.sendMessage(plugin.getConfigManager().getMessage("clan_lobby_missing"));
            return;
        }
        if (!lobby.owner.equals(inviter.getUniqueId())) {
            inviter.sendMessage(plugin.getConfigManager().getMessage("clan_only_owner_manage"));
            return;
        }
        if (lobby.opponentClan != null) {
            inviter.sendMessage(plugin.getConfigManager().getMessage("clan_enemy_already_selected")
                    .replace("%clan%", safeClanName(lobby.opponentClan)));
            return;
        }
        String normalized = normalizeClan(clanName);
        if (normalized.equals(normalizeClan(lobby.ownerClan))) {
            inviter.sendMessage(plugin.getConfigManager().getMessage("clan_cannot_invite_self"));
            return;
        }
        lobby.invitedClans.add(normalized);
        inviter.sendMessage(plugin.getConfigManager().getMessage("clan_invite_sent")
                .replace("%target_clan%", clanName)
                .replace("%room%", room.getName()));
        notifyInvitedClan(clanName, room.getName());
        room.broadcast(plugin.getConfigManager().getMessage("clan_invite_broadcast")
                .replace("%owner_clan%", safeClanName(lobby.ownerClan))
                .replace("%target_clan%", clanName)
                .replace("%room%", room.getName()));
    }

    public void acceptClanInvite(Player player, Room room) {
        ClanLobby lobby = clanLobbies.get(room.getName().toLowerCase());
        if (lobby == null || room.getState() != RoomState.WAITING) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_lobby_missing"));
            return;
        }
        if (lobby.opponentClan != null) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_enemy_already_selected")
                    .replace("%clan%", safeClanName(lobby.opponentClan)));
            return;
        }
        String clan = plugin.getClanProvider() == null ? null : plugin.getClanProvider().getClanName(player);
        if (clan == null || clan.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_clan"));
            return;
        }
        String normalized = normalizeClan(clan);
        if (!lobby.invitedClans.contains(normalized)) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_not_invited"));
            return;
        }
        lobby.opponentClan = clan;
        cancelClanLobbyTimeout(room.getName());
        room.broadcast(plugin.getConfigManager().getMessage("clan_enemy_selected")
                .replace("%clan%", safeClanName(clan))
                .replace("%room%", room.getName()));
    }

    public void stopClanLobby(Player player, Room room) {
        ClanLobby lobby = clanLobbies.get(room.getName().toLowerCase());
        if (lobby == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_lobby_missing"));
            return;
        }
        if (!lobby.owner.equals(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_only_owner_manage"));
            return;
        }
        cancelClanLobby(room, true);
    }

    public void leaveRoom(Player player, Room room, boolean manual) {
        if (room.getSpectators().contains(player.getUniqueId())) {
            removeSpectator(player, room, true);
            if (manual) {
                player.sendMessage(plugin.getConfigManager().getMessage("spectate_left"));
            }
            return;
        }
        handleQuit(player, room);
        if (manual) {
            player.teleport(plugin.getReturnSpawn());
        }
    }

    public void forceStart(Room room) {
        if (room.getState() != RoomState.WAITING) {
            return;
        }
        if (room.getPlayers().isEmpty()) {
            return;
        }

        startCountdown(room, true);
    }

    public void handleDeath(Player victim, Player killer, Room room) {
        if (room.getState() != RoomState.PLAYING) {
            return;
        }

        room.removePlayer(victim.getUniqueId());
        updateKillDeathStats(killer, victim, room.getType());

        if (room.isChatEnabled()) {
            room.broadcast(plugin.getConfigManager().getMessage("match_player_eliminated")
                    .replace("%player%", victim.getName()));
        }

        // Inform the dying player of their options (spectate or leave)
        if (victim.isOnline()) {
            victim.sendMessage(plugin.getConfigManager().getMessage("death_spectate_prompt")
                    .replace("%room%", room.getName()));
        }

        // Queue a respawn teleport to return spawn
        pendingRespawnTeleport.add(victim.getUniqueId());

        evaluateMatchEnd(room);
    }

    public void handleDeathAsSpectator(Player victim, Player killer, Room room) {
        if (room.getState() != RoomState.PLAYING) {
            return;
        }

        room.removePlayer(victim.getUniqueId());
        updateKillDeathStats(killer, victim, room.getType());

        if (room.isChatEnabled()) {
            room.broadcast(plugin.getConfigManager().getMessage("match_player_eliminated")
                    .replace("%player%", victim.getName()));
        }

        // Add as spectator after respawn (keepInventory mode)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.isOnline()) {
                addSpectator(victim, room);
            }
        });

        evaluateMatchEnd(room);
    }

    public void handleQuit(Player player, Room room) {
        room.removePlayer(player.getUniqueId());
        room.getSpectators().remove(player.getUniqueId());

        if (room.getState() == RoomState.WAITING || room.getState() == RoomState.STARTING) {
            ClanLobby lobby = clanLobbies.get(room.getName().toLowerCase());
            if (lobby != null && lobby.owner.equals(player.getUniqueId()) && room.getType() == RoomType.CLAN) {
                cancelClanLobby(room, true);
                return;
            }
            if (room.isChatEnabled()) {
                room.broadcast(plugin.getConfigManager().getMessage("room_left_broadcast")
                        .replace("%player%", player.getName())
                        .replace("%current%", String.valueOf(room.getPlayers().size()))
                        .replace("%max%", String.valueOf(room.getCapacity())));
            }
            sendLeaveFeedback(player, room);

            if (room.getState() == RoomState.STARTING && !room.hasEnoughPlayersToStart()) {
                room.setState(RoomState.WAITING);
                cancelCountdown(room.getName());
                setDoors(room, false);
                if (room.isChatEnabled()) {
                    room.broadcast(plugin.getConfigManager().getMessage("match_countdown_cancelled"));
                }
            }
            return;
        }

        if (room.getState() == RoomState.PLAYING) {
            if (room.isChatEnabled()) {
                room.broadcast(plugin.getConfigManager().getMessage("match_player_left_lose")
                        .replace("%player%", player.getName()));
            }
            resetStreak(player);
            evaluateMatchEnd(room);
        }
    }

    public boolean addSpectator(Player player, Room room) {
        if (player == null || room == null) {
            return false;
        }
        if (room.getSpectators().contains(player.getUniqueId())) {
            return true;
        }

        spectatorModes.put(player.getUniqueId(), player.getGameMode());
        room.getSpectators().add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);

        Location target = resolveValidSpectatorTarget(room);
        if (target == null) {
            return false;
        }
        player.teleport(target);
        return true;
    }

    public void handleRespawnTeleport(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!pendingRespawnTeleport.remove(playerId)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> player.teleport(plugin.getReturnSpawn()));
    }

    public boolean areSameTeam(Room room, UUID first, UUID second) {
        if (room == null || first == null || second == null) {
            return false;
        }
        Map<UUID, Integer> teams = roomTeams.get(room.getName().toLowerCase());
        if (teams == null) {
            return false;
        }
        Integer firstTeam = teams.get(first);
        Integer secondTeam = teams.get(second);
        return firstTeam != null && firstTeam.equals(secondTeam);
    }

    public void removeSpectator(Player player, Room room, boolean teleportToReturnSpawn) {
        if (player == null || room == null) {
            return;
        }

        room.getSpectators().remove(player.getUniqueId());
        GameMode previous = spectatorModes.remove(player.getUniqueId());
        if (previous != null) {
            player.setGameMode(previous);
        } else if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        if (teleportToReturnSpawn) {
            player.teleport(plugin.getReturnSpawn());
        }
    }

    public void queueReturnSpawnOnJoin(UUID playerId) {
        if (playerId != null) {
            spectatorModes.remove(playerId);
            spawnOnJoin.add(playerId);
        }
    }

    public void handleJoinReturnSpawn(Player player) {
        if (player == null) {
            return;
        }
        if (!spawnOnJoin.remove(player.getUniqueId())) {
            return;
        }

        player.teleport(plugin.getReturnSpawn());
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private boolean validateClanJoin(Player player, Room room) {
        if (plugin.getClanProvider() == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_system_disabled"));
            return false;
        }

        String playerClan = plugin.getClanProvider().getClanName(player);
        if (playerClan == null || playerClan.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_clan"));
            return false;
        }

        ClanLobby lobby = clanLobbies.get(room.getName().toLowerCase());
        if (lobby == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_lobby_missing"));
            return false;
        }
        if (lobby.opponentClan == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_waiting_enemy_accept"));
            return false;
        }

        String ownerClan = normalizeClan(lobby.ownerClan);
        String opponentClan = normalizeClan(lobby.opponentClan);
        String joiningClan = normalizeClan(playerClan);

        if (!joiningClan.equals(ownerClan) && !joiningClan.equals(opponentClan)) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_room_two_clans_only"));
            return false;
        }

        Set<String> clansInRoom = new HashSet<>();
        for (UUID uuid : room.getPlayers()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null) {
                continue;
            }
            String clan = plugin.getClanProvider().getClanName(member);
            if (clan != null && !clan.isEmpty()) {
                clansInRoom.add(normalizeClan(clan));
            }
        }
        clansInRoom.add(normalizeClan(playerClan));
        if (clansInRoom.size() > 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_room_two_clans_only"));
            return false;
        }

        int joinedClanCount = countPlayersInClan(room, joiningClan);
        if (joinedClanCount >= room.getPlayersPerTeam()) {
            player.sendMessage(plugin.getConfigManager().getMessage("clan_team_full")
                    .replace("%clan%", safeClanName(playerClan))
                    .replace("%limit%", String.valueOf(room.getPlayersPerTeam())));
            return false;
        }

        return true;
    }

    private void sendJoinFeedback(Player player, Room room) {
        if (room.isTitlesEnabled()) {
            player.sendTitle(
                    plugin.getConfigManager().getRawMessage("match_join_welcome_title"),
                    plugin.getConfigManager().getRawMessage("match_join_welcome_subtitle").replace("%room%", room.getName()),
                    10,
                    40,
                    10);
        }

        if (room.isActionBarEnabled()) {
            sendActionBar(player, plugin.getConfigManager().getRawMessage("match_join_actionbar")
                    .replace("%room%", room.getName()));
        }

        if (room.isChatEnabled()) {
            player.sendMessage(plugin.getConfigManager().getMessage("match_join_chat").replace("%room%", room.getName()));
        }

        if (room.getType() == RoomType.CLAN) {
            playClanJoinEffects(player, room);
        }
    }

    private void sendLeaveFeedback(Player player, Room room) {
        if (room.isTitlesEnabled()) {
            player.sendTitle(
                    plugin.getConfigManager().getRawMessage("room_leave_title"),
                    plugin.getConfigManager().getRawMessage("room_leave_subtitle").replace("%room%", room.getName()),
                    10,
                    40,
                    10);
        }

        if (room.isActionBarEnabled()) {
            sendActionBar(player, plugin.getConfigManager().getRawMessage("room_leave_actionbar")
                    .replace("%room%", room.getName()));
        }
    }

    private void startCountdown(Room room, boolean forced) {
        if (room.getState() != RoomState.WAITING && room.getState() != RoomState.STARTING) {
            return;
        }

        if (!forced && !room.hasEnoughPlayersToStart()) {
            return;
        }
        if (room.getType() == RoomType.CLAN && !forced && !isValidClanTeamComposition(room)) {
            return;
        }

        String roomKey = room.getName().toLowerCase();
        if (countdownTasks.containsKey(roomKey)) {
            return;
        }

        room.setState(RoomState.STARTING);
        setDoors(room, true);

        int initialTime = Math.max(1, room.getDoorOpenDelay());
        if (room.isChatEnabled()) {
            room.broadcast(plugin.getConfigManager().getMessage("match_countdown_start")
                    .replace("%time%", String.valueOf(initialTime)));
        }

        BukkitTask task = new BukkitRunnable() {
            int time = initialTime;

            @Override
            public void run() {
                if (room.getState() != RoomState.STARTING) {
                    cancelCountdown(room.getName());
                    setDoors(room, false);
                    cancel();
                    return;
                }

                if (!forced && !room.hasEnoughPlayersToStart()) {
                    room.setState(RoomState.WAITING);
                    cancelCountdown(room.getName());
                    setDoors(room, false);
                    if (room.isChatEnabled()) {
                        room.broadcast(plugin.getConfigManager().getMessage("match_countdown_cancelled"));
                    }
                    cancel();
                    return;
                }
                if (room.getType() == RoomType.CLAN) {
                    ClanLobby lobby = clanLobbies.get(room.getName().toLowerCase());
                    if (lobby == null || lobby.opponentClan == null) {
                        room.setState(RoomState.WAITING);
                        cancelCountdown(room.getName());
                        setDoors(room, false);
                        cancel();
                        return;
                    }
                    if (!isValidClanTeamComposition(room)) {
                        room.setState(RoomState.WAITING);
                        cancelCountdown(room.getName());
                        setDoors(room, false);
                        if (room.isChatEnabled()) {
                            room.broadcast(plugin.getConfigManager().getMessage("match_countdown_cancelled"));
                        }
                        cancel();
                        return;
                    }
                }

                if (time <= 0) {
                    cancelCountdown(room.getName());
                    startMatch(room);
                    cancel();
                    return;
                }

                for (UUID uuid : room.getPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) {
                        continue;
                    }

                    if (room.isActionBarEnabled()) {
                        sendActionBar(p, plugin.getConfigManager().getRawMessage("match_starting_actionbar")
                                .replace("%time%", String.valueOf(time)));
                    }

                    if (room.isTitlesEnabled() && time <= 5) {
                        p.sendTitle(CC.translate("&e" + time), "", 0, 21, 0);
                    }
                }

                if (room.isChatEnabled() && (time <= 5 || time % 10 == 0)) {
                    room.broadcast(plugin.getConfigManager().getMessage("match_starting_chat")
                            .replace("%time%", String.valueOf(time)));
                }

                if (time <= 5) {
                    for (UUID uuid : room.getPlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                        }
                    }
                }

                time--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        countdownTasks.put(roomKey, task);
    }

    private void startMatch(Room room) {
        if (room.getState() != RoomState.STARTING) {
            return;
        }

        if (!validateRoomConfiguration(room)) {
            room.setState(RoomState.WAITING);
            setDoors(room, false);
            if (room.isChatEnabled()) {
                room.broadcast(plugin.getConfigManager().getMessage("room_not_configured"));
            }
            return;
        }
        if (room.getType() == RoomType.CLAN && !isValidClanTeamComposition(room)) {
            room.setState(RoomState.WAITING);
            setDoors(room, false);
            if (room.isChatEnabled()) {
                room.broadcast(plugin.getConfigManager().getMessage("clan_invalid_composition"));
            }
            return;
        }

        assignTeams(room);
        teleportTeams(room);
        room.setState(RoomState.PLAYING);

        if (room.isTitlesEnabled()) {
            for (UUID uuid : room.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendTitle(plugin.getConfigManager().getRawMessage("match_started_title"),
                            plugin.getConfigManager().getRawMessage("match_started_subtitle"), 10, 40, 10);
                }
            }
        }

        if (room.isChatEnabled()) {
            room.broadcast(plugin.getConfigManager().getMessage("match_started_broadcast"));
        }

        for (UUID uuid : room.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                continue;
            }
            for (PotionEffect effect : room.getEffects()) {
                p.addPotionEffect(effect);
            }
        }

        if (room.getMaxDuelTime() > 0) {
            scheduleTimeLimit(room);
        }
        startDuelBossBar(room);
    }

    private void assignTeams(Room room) {
        Map<UUID, Integer> teams = new HashMap<>();
        List<UUID> players = new ArrayList<>(room.getPlayers());

        if (room.getType() == RoomType.CLAN && plugin.getClanProvider() != null) {
            Map<String, List<UUID>> clanMap = new HashMap<>();
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                String clan = player == null ? null : plugin.getClanProvider().getClanName(player);
                if (clan == null || clan.trim().isEmpty()) {
                    continue;
                }
                clanMap.computeIfAbsent(clan.toLowerCase().trim(), ignored -> new ArrayList<>()).add(uuid);
            }

            List<List<UUID>> groups = new ArrayList<>(clanMap.values());
            if (groups.size() < 2) {
                Collections.shuffle(players);
                splitByCapacity(players, teams, room.getPlayersPerTeam());
            } else if (groups.size() == 2) {
                List<UUID> team1 = groups.get(0);
                List<UUID> team2 = groups.get(1);
                for (UUID uuid : team1) {
                    teams.put(uuid, 1);
                }
                for (UUID uuid : team2) {
                    teams.put(uuid, 2);
                }
            } else {
                Collections.shuffle(players);
                splitByCapacity(players, teams, room.getPlayersPerTeam());
            }
        } else {
            Collections.shuffle(players);
            splitByCapacity(players, teams, room.getPlayersPerTeam());
        }

        roomTeams.put(room.getName().toLowerCase(), teams);

        if (room.getType() == RoomType.NORMAL && room.isChatEnabled()) {
            String redName = plugin.getConfig().getString("teams.red-name", "&cRed Team");
            String blueName = plugin.getConfig().getString("teams.blue-name", "&9Blue Team");
            for (Map.Entry<UUID, Integer> entry : teams.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null) {
                    continue;
                }
                String teamName = entry.getValue() == 1 ? redName : blueName;
                p.sendMessage(plugin.getConfigManager().getMessage("match_team_assigned")
                        .replace("%team%", CC.translate(teamName)));
            }
        }
    }

    private void splitByCapacity(List<UUID> players, Map<UUID, Integer> teams, int playersPerTeam) {
        int team1 = 0;
        int team2 = 0;
        for (UUID uuid : players) {
            if ((team1 <= team2 && team1 < playersPerTeam) || team2 >= playersPerTeam) {
                teams.put(uuid, 1);
                team1++;
            } else {
                teams.put(uuid, 2);
                team2++;
            }
        }
    }

    private void teleportTeams(Room room) {
        Map<UUID, Integer> teams = roomTeams.get(room.getName().toLowerCase());
        if (teams == null) {
            return;
        }

        Location spawn1 = room.getSpawn1();
        Location spawn2 = room.getSpawn2();

        for (Map.Entry<UUID, Integer> entry : teams.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Location target = entry.getValue() == 1 ? spawn1 : spawn2;
            if (target != null) {
                player.teleport(target);
            }
        }
    }

    private void evaluateMatchEnd(Room room) {
        Map<UUID, Integer> teams = roomTeams.get(room.getName().toLowerCase());
        if (teams == null || teams.isEmpty()) {
            endMatch(room, null, List.of(), List.of(), 0);
            return;
        }

        int aliveTeam1 = 0;
        int aliveTeam2 = 0;

        for (UUID uuid : room.getPlayers()) {
            Integer team = teams.get(uuid);
            if (team == null) {
                continue;
            }
            if (team == 1) {
                aliveTeam1++;
            } else {
                aliveTeam2++;
            }
        }

        if (aliveTeam1 > 0 && aliveTeam2 > 0) {
            return;
        }

        if (aliveTeam1 == 0 && aliveTeam2 == 0) {
            endMatch(room, null, List.of(), List.of(), 0);
            return;
        }

        int winnerTeam = aliveTeam1 > 0 ? 1 : 2;
        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();

        for (Map.Entry<UUID, Integer> entry : teams.entrySet()) {
            if (entry.getValue() == winnerTeam) {
                winners.add(entry.getKey());
            } else {
                losers.add(entry.getKey());
            }
        }

        Player winnerPlayer = resolveAnyOnlinePlayer(winners);

        endMatch(room, winnerPlayer, winners, losers, winnerTeam);
    }

    private void endMatch(Room room, Player winner, List<UUID> winners, List<UUID> losers, int winnerTeam) {
        if (room.getState() == RoomState.ENDING) {
            return;
        }

        room.setState(RoomState.ENDING);
        cancelCountdown(room.getName());
        cancelTimeLimit(room.getName());
        roomTimeRemaining.remove(room.getName().toLowerCase());
        stopDuelBossBar(room.getName());

        if (room.isChatEnabled()) {
            room.broadcast(plugin.getConfigManager().getMessage("match_ended_broadcast"));
            if (winner != null) {
                if (room.getType() == RoomType.CLAN) {
                    String winnerClan = resolvePrimaryClanName(winners);
                    room.broadcast(plugin.getConfigManager().getMessage("clan_match_winner_broadcast")
                            .replace("%winner%", winner.getName())
                            .replace("%clan%", safeClanName(winnerClan)));
                } else {
                    room.broadcast(plugin.getConfigManager().getMessage("match_winner_broadcast")
                            .replace("%winner%", winner.getName()));
                }
            }
            broadcastDetailedWinnerMessage(room, winner, winners, losers, winnerTeam);
        }

        playWinnerCelebration(room, winner, winners, losers, winnerTeam);
        for (UUID uuid : winners) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                increaseStreak(p);
            }
        }
        for (UUID uuid : losers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                resetStreak(p);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (UUID uuid : winners) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    updateStats(player, room.getType(), true);
                }
            }
            for (UUID uuid : losers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    updateStats(player, room.getType(), false);
                }
            }
        });

        if (winner == null && winners.isEmpty()) {
            Set<UUID> everyone = new HashSet<>(room.getPlayers());
            everyone.addAll(room.getSpectators());
            teleportToSpawn(new ArrayList<>(everyone));
            cleanupRoom(room);
            return;
        }

        teleportToSpawn(losers);
        teleportToSpawn(new ArrayList<>(room.getSpectators()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            teleportToSpawn(winners);
            cleanupRoom(room);
        }, Math.max(20L, room.getPostMatchTeleportDelay() * 20L));
    }

    private boolean validateRoomConfiguration(Room room) {
        if (room.getSpawn1() == null || room.getSpawn2() == null) {
            return false;
        }

        if (room.getSpawn1().getWorld() == null || room.getSpawn2().getWorld() == null) {
            return false;
        }

        Optional<WorldGuardHook.RegionBounds> bounds = plugin.getRoomManager().getArenaBounds(room);
        if (!bounds.isPresent()) {
            return false;
        }

        String world = bounds.get().getWorldName();
        return world.equals(room.getSpawn1().getWorld().getName())
                && world.equals(room.getSpawn2().getWorld().getName());
    }

    private void scheduleTimeLimit(Room room) {
        String key = room.getName().toLowerCase();
        cancelTimeLimit(room.getName());
        roomTimeRemaining.put(key, room.getMaxDuelTime());

        BukkitTask task = new BukkitRunnable() {
            int timeLeft = room.getMaxDuelTime();

            @Override
            public void run() {
                if (room.getState() != RoomState.PLAYING) {
                    roomTimeRemaining.remove(room.getName().toLowerCase());
                    cancelTimeLimit(room.getName());
                    cancel();
                    return;
                }

                if (timeLeft <= 0) {
                    if (room.isChatEnabled()) {
                        room.broadcast(plugin.getConfigManager().getMessage("match_draw_broadcast"));
                    }
                    endMatch(room, null, List.of(), List.of(), 0);
                    roomTimeRemaining.remove(room.getName().toLowerCase());
                    cancelTimeLimit(room.getName());
                    cancel();
                    return;
                }

                roomTimeRemaining.put(room.getName().toLowerCase(), timeLeft);
                timeLeft--;
            }
        }.runTaskTimer(plugin, 20L, 20L);

        timeLimitTasks.put(key, task);
    }

    private void setDoors(Room room, boolean place) {
        String roomKey = room.getName().toLowerCase();
        Optional<WorldGuardHook.RegionBounds> bounds = plugin.getRoomManager().getArenaBounds(room);

        if (place) {
            List<PlacedDoorBlock> snapshot = new ArrayList<>();
            Set<String> touched = new HashSet<>();
            Material material = room.getDoorMaterial();

            if (bounds.isPresent()) {
                World world = Bukkit.getWorld(bounds.get().getWorldName());
                if (world != null) {
                    List<ArenaBoundaryScanner.BlockPoint> entries = ArenaBoundaryScanner.detectEntranceGaps(world, bounds.get());
                    room.setDetectedEntranceBlocks(entries.size());
                    for (ArenaBoundaryScanner.BlockPoint point : entries) {
                        Block block = world.getBlockAt(point.getX(), point.getY(), point.getZ());
                        closeSingleBlock(block, material, snapshot, touched);
                    }
                }
            } else {
                List<Room.DoorRegion> regions = room.getDoorRegions();
                if (!regions.isEmpty()) {
                    for (Room.DoorRegion region : regions) {
                        closeDoorRegion(region, material, snapshot, touched);
                    }
                } else {
                    closeLegacyDoorRegion(room.getBarrierPos1(), room.getBarrierPos2(), material, snapshot, touched);
                }
            }

            placedDoorBlocks.put(roomKey, snapshot);
            return;
        }

        List<PlacedDoorBlock> snapshot = placedDoorBlocks.remove(roomKey);
        if (snapshot != null && !snapshot.isEmpty()) {
            for (PlacedDoorBlock placed : snapshot) {
                World world = Bukkit.getWorld(placed.worldName);
                if (world == null) {
                    continue;
                }
                world.getBlockAt(placed.x, placed.y, placed.z).setType(placed.previousType, false);
            }
            return;
        }

        // Fallback for old data or plugin reload during countdown
        List<Room.DoorRegion> regions = room.getDoorRegions();
        if (!regions.isEmpty()) {
            for (Room.DoorRegion region : regions) {
                setDoorRegion(region.getPos1(), region.getPos2(), Material.AIR);
            }
        } else {
            setDoorRegion(room.getBarrierPos1(), room.getBarrierPos2(), Material.AIR);
        }
    }

    private void setDoorRegion(Location pos1, Location pos2, Material material) {
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null) {
            return;
        }

        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return;
        }

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos1.getWorld().getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private void closeDoorRegion(Room.DoorRegion region, Material material, List<PlacedDoorBlock> snapshot, Set<String> touched) {
        Location pos1 = region.getPos1();
        Location pos2 = region.getPos2();
        if (pos1.getWorld() == null || pos2.getWorld() == null || !pos1.getWorld().equals(pos2.getWorld())) {
            return;
        }

        if (region.hasBlockCoordinates()) {
            World world = pos1.getWorld();
            for (Room.BlockCoordinate coordinate : region.getBlockCoordinates()) {
                Block block = world.getBlockAt(coordinate.getX(), coordinate.getY(), coordinate.getZ());
                closeSingleBlock(block, material, snapshot, touched);
            }
            return;
        }

        closeLegacyDoorRegion(pos1, pos2, material, snapshot, touched);
    }

    private void closeLegacyDoorRegion(Location pos1, Location pos2, Material material, List<PlacedDoorBlock> snapshot, Set<String> touched) {
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos2.getWorld() == null || !pos1.getWorld().equals(pos2.getWorld())) {
            return;
        }

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        World world = pos1.getWorld();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isDoorGapCandidate(block)) {
                        continue;
                    }
                    closeSingleBlock(block, material, snapshot, touched);
                }
            }
        }
    }

    private void closeSingleBlock(Block block, Material material, List<PlacedDoorBlock> snapshot, Set<String> touched) {
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (touched.contains(key)) {
            return;
        }

        touched.add(key);
        Material previousType = block.getType();
        if (previousType == material) {
            return;
        }

        snapshot.add(new PlacedDoorBlock(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), previousType));
        block.setType(material, false);
    }

    private boolean isDoorGapCandidate(Block block) {
        return ArenaBoundaryScanner.isGapCandidate(block);
    }

    private static class PlacedDoorBlock {
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;
        private final Material previousType;

        private PlacedDoorBlock(String worldName, int x, int y, int z, Material previousType) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.previousType = previousType;
        }
    }

    private void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(CC.translate(text)));
    }

    private void startDuelBossBar(Room room) {
        if (!plugin.getConfig().getBoolean("duel-bossbar.enabled", true)) {
            return;
        }

        String roomKey = room.getName().toLowerCase();
        stopDuelBossBar(room.getName());

        BarColor color = parseBarColor(plugin.getConfig().getString("duel-bossbar.color", "RED"));
        BarStyle style = parseBarStyle(plugin.getConfig().getString("duel-bossbar.style", "SOLID"));
        BossBar bar = Bukkit.createBossBar("", color, style);
        duelBossBars.put(roomKey, bar);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (room.getState() != RoomState.PLAYING) {
                    stopDuelBossBar(room.getName());
                    cancel();
                    return;
                }

                int max = room.getMaxDuelTime();
                String title;
                double progress;

                if (max > 0) {
                    int remaining = getRemainingTime(room.getName());
                    title = CC.translate(resolveBossbarTemplate(room, true)
                            .replace("%time%", String.valueOf(Math.max(0, remaining)))
                            .replace("%room%", room.getName())
                            .replace("%alive1%", String.valueOf(getAliveByTeam(room, 1)))
                            .replace("%alive2%", String.valueOf(getAliveByTeam(room, 2))));
                    progress = Math.max(0.0D, Math.min(1.0D, remaining / (double) max));
                } else {
                    title = CC.translate(resolveBossbarTemplate(room, false)
                            .replace("%room%", room.getName())
                            .replace("%alive1%", String.valueOf(getAliveByTeam(room, 1)))
                            .replace("%alive2%", String.valueOf(getAliveByTeam(room, 2))));
                    progress = 1.0D;
                }

                bar.setTitle(title);
                bar.setProgress(progress);

                Set<UUID> viewers = new HashSet<>(room.getPlayers());
                viewers.addAll(room.getSpectators());

                bar.removeAll();
                for (UUID uuid : viewers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        bar.addPlayer(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        duelBossBarTasks.put(roomKey, task);
    }

    private String resolveBossbarTemplate(Room room, boolean withLimit) {
        String keyByType = room.getType() == RoomType.CLAN
                ? (withLimit ? "duel-bossbar.clan.with-limit" : "duel-bossbar.clan.no-limit")
                : (withLimit ? "duel-bossbar.normal.with-limit" : "duel-bossbar.normal.no-limit");
        String fallback = withLimit ? "&cTiempo restante: &f%time%s" : "&eSin limite: hasta que uno muera";
        return plugin.getConfig().getString(keyByType,
                plugin.getConfig().getString(withLimit ? "duel-bossbar.with-limit" : "duel-bossbar.no-limit", fallback));
    }

    private int getAliveByTeam(Room room, int team) {
        if (room == null) {
            return 0;
        }
        Map<UUID, Integer> teams = roomTeams.get(room.getName().toLowerCase());
        if (teams == null || teams.isEmpty()) {
            return 0;
        }
        int alive = 0;
        for (UUID uuid : room.getPlayers()) {
            Integer assigned = teams.get(uuid);
            if (assigned != null && assigned == team) {
                alive++;
            }
        }
        return alive;
    }

    private BarColor parseBarColor(String value) {
        if (value == null) {
            return BarColor.RED;
        }
        try {
            return BarColor.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarColor.RED;
        }
    }

    private BarStyle parseBarStyle(String value) {
        if (value == null) {
            return BarStyle.SOLID;
        }
        try {
            return BarStyle.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarStyle.SOLID;
        }
    }

    private int getRemainingTime(String roomName) {
        return roomTimeRemaining.getOrDefault(roomName.toLowerCase(), 0);
    }

    private void stopDuelBossBar(String roomName) {
        String roomKey = roomName.toLowerCase();
        BukkitTask task = duelBossBarTasks.remove(roomKey);
        if (task != null) {
            task.cancel();
        }

        BossBar bar = duelBossBars.remove(roomKey);
        if (bar != null) {
            bar.removeAll();
        }
        roomTimeRemaining.remove(roomKey);
    }

    // -------------------------------------------------------------------------
    // Stats helpers — all write operations delegated to SQLiteDatabase
    // -------------------------------------------------------------------------

    /**
     * Increments kill/death columns for the given participants.
     * Must be called from an async thread.
     */
    private void updateKillDeathStats(Player killer, Player victim, RoomType type) {
        if (plugin.getDatabase() == null || victim == null) return;

        String deathCol = type == RoomType.CLAN ? "clan_deaths" : "normal_deaths";
        plugin.getDatabase().incrementStat(
                victim.getUniqueId().toString(), victim.getName(), deathCol);

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            String killCol = type == RoomType.CLAN ? "clan_kills" : "normal_kills";
            plugin.getDatabase().incrementStat(
                    killer.getUniqueId().toString(), killer.getName(), killCol);
        }
    }

    /** Resets the win streak to 0. Must be called from an async thread. */
    private void resetStreak(Player player) {
        if (player == null || plugin.getDatabase() == null) return;
        plugin.getDatabase().resetStreak(player.getUniqueId().toString(), player.getName());
    }

    /** Increments the win streak by 1. Must be called from an async thread. */
    private void increaseStreak(Player player) {
        if (player == null || plugin.getDatabase() == null) return;
        plugin.getDatabase().incrementStreak(player.getUniqueId().toString(), player.getName());
    }

    /**
     * Increments win or loss counter for the player.
     * Must be called from an async thread.
     */
    private void updateStats(Player player, RoomType type, boolean isWin) {
        if (player == null || plugin.getDatabase() == null) return;

        String col = isWin
                ? (type == RoomType.CLAN ? "clan_wins"   : "normal_wins")
                : (type == RoomType.CLAN ? "clan_losses" : "normal_losses");

        plugin.getDatabase().incrementStat(player.getUniqueId().toString(), player.getName(), col);
    }

    private void cancelCountdown(String roomName) {
        BukkitTask task = countdownTasks.remove(roomName.toLowerCase());
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelTimeLimit(String roomName) {
        BukkitTask task = timeLimitTasks.remove(roomName.toLowerCase());
        if (task != null) {
            task.cancel();
        }
    }

    private void broadcastDetailedWinnerMessage(Room room, Player winner, List<UUID> winners, List<UUID> losers, int winnerTeam) {
        if (winner == null || winners.isEmpty()) {
            return;
        }

        if (room.getType() == RoomType.CLAN) {
            String winnerClan = resolvePrimaryClanName(winners);
            String loserClan = resolvePrimaryClanName(losers);
            room.broadcast(plugin.getConfigManager().getMessage("clan_match_result_broadcast")
                    .replace("%winner_clan%", safeClanName(winnerClan))
                    .replace("%loser_clan%", safeClanName(loserClan))
                    .replace("%room%", room.getName()));
            return;
        }

        if (room.getPlayersPerTeam() <= 1 && winners.size() == 1 && losers.size() == 1) {
            Player loser = Bukkit.getPlayer(losers.get(0));
            if (loser == null) {
                return;
            }

            room.broadcast(plugin.getConfigManager().getMessage("match_winner_duel_broadcast")
                    .replace("%winner%", winner.getName())
                    .replace("%loser%", loser.getName()));
            return;
        }

        String redName = CC.translate(plugin.getConfig().getString("teams.red-name", "&cEquipo Rojo"));
        String blueName = CC.translate(plugin.getConfig().getString("teams.blue-name", "&9Equipo Azul"));
        String winnerTeamName = winnerTeam == 1 ? redName : blueName;
        String loserTeamName = winnerTeam == 1 ? blueName : redName;

        room.broadcast(plugin.getConfigManager().getMessage("match_winner_team_broadcast")
                .replace("%winner_team%", winnerTeamName)
                .replace("%loser_team%", loserTeamName));
    }

    private String resolvePrimaryClanName(List<UUID> players) {
        if (players == null || players.isEmpty() || plugin.getClanProvider() == null) {
            return null;
        }

        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> original = new HashMap<>();

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            String clan = plugin.getClanProvider().getClanName(player);
            if (clan == null || clan.trim().isEmpty()) {
                continue;
            }
            String normalized = clan.toLowerCase().trim();
            counts.put(normalized, counts.getOrDefault(normalized, 0) + 1);
            original.putIfAbsent(normalized, clan);
        }

        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }

        return best == null ? null : original.get(best);
    }

    private String safeClanName(String clanName) {
        if (clanName == null || clanName.trim().isEmpty()) {
            return "-";
        }
        return clanName;
    }

    private void playWinnerCelebration(Room room, Player winner, List<UUID> winners, List<UUID> losers, int winnerTeam) {
        if (winner == null) {
            return;
        }

        String winnerDisplay = winner.getName();
        if (room.getPlayersPerTeam() > 1 || winners.size() > 1) {
            String redName = CC.translate(plugin.getConfig().getString("teams.red-name", "&cEquipo Rojo"));
            String blueName = CC.translate(plugin.getConfig().getString("teams.blue-name", "&9Equipo Azul"));
            winnerDisplay = winnerTeam == 1 ? redName : blueName;
        }

        for (UUID uuid : winners) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendTitle(
                        plugin.getConfigManager().getRawMessage("match_win_title"),
                        plugin.getConfigManager().getRawMessage("match_win_subtitle")
                                .replace("%room%", room.getName())
                                .replace("%winner%", winnerDisplay),
                        10, 50, 10);
            }
        }
        for (UUID uuid : losers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendTitle(
                        plugin.getConfigManager().getRawMessage("match_lose_title"),
                        plugin.getConfigManager().getRawMessage("match_lose_subtitle")
                                .replace("%room%", room.getName())
                                .replace("%winner%", winnerDisplay),
                        10, 50, 10);
            }
        }

        if (room.getType() == RoomType.NORMAL && room.getPlayersPerTeam() == 1 && winners.size() == 1) {
            Player winnerPlayer = Bukkit.getPlayer(winners.get(0));
            if (winnerPlayer != null) {
                playConfiguredWinnerEffects(winnerPlayer);
                sendNearbyWinnerTitle(winnerPlayer.getLocation(), winnerDisplay, room.getName());
            }
        }

        if (room.getType() == RoomType.CLAN && plugin.getConfig().getBoolean("celebration.firework.clan-enabled", true)) {
            for (UUID winnerId : winners) {
                Player winnerPlayer = Bukkit.getPlayer(winnerId);
                if (winnerPlayer != null && winnerPlayer.isOnline()) {
                    playConfiguredWinnerEffects(winnerPlayer);
                }
            }
        }
    }

    private void playConfiguredWinnerEffects(Player winner) {
        if (winner == null || winner.getWorld() == null) {
            return;
        }

        if (plugin.getConfig().getBoolean("celebration.sound.enabled", true)) {
            String soundName = plugin.getConfig().getString("celebration.sound.name", "ENTITY_FIREWORK_ROCKET_LAUNCH");
            float volume = (float) plugin.getConfig().getDouble("celebration.sound.volume", 1.0D);
            float pitch = (float) plugin.getConfig().getDouble("celebration.sound.pitch", 1.0D);
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                winner.getWorld().playSound(winner.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (!plugin.getConfig().getBoolean("celebration.firework.enabled", true)) {
            return;
        }

        int count = Math.max(1, plugin.getConfig().getInt("celebration.firework.count", 3));
        int power = Math.max(0, Math.min(4, plugin.getConfig().getInt("celebration.firework.power", 1)));
        boolean flicker = plugin.getConfig().getBoolean("celebration.firework.flicker", true);
        boolean trail = plugin.getConfig().getBoolean("celebration.firework.trail", true);

        List<Color> colors = new ArrayList<>();
        for (String colorName : plugin.getConfig().getStringList("celebration.firework.colors")) {
            Color color = parseColor(colorName);
            if (color != null) {
                colors.add(color);
            }
        }
        if (colors.isEmpty()) {
            colors.add(Color.ORANGE);
            colors.add(Color.YELLOW);
            colors.add(Color.WHITE);
        }

        for (int i = 0; i < count; i++) {
            Location spawn = winner.getLocation().clone().add((Math.random() - 0.5D) * 2.0D, 0.2D, (Math.random() - 0.5D) * 2.0D);
            Firework firework = winner.getWorld().spawn(spawn, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.clearEffects();
            meta.setPower(power);
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(colors)
                    .flicker(flicker)
                    .trail(trail)
                    .build());
            firework.setFireworkMeta(meta);
        }
    }

    private void sendNearbyWinnerTitle(Location center, String winnerName, String roomName) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("celebration.nearby-title.enabled", true)) {
            return;
        }

        double radius = Math.max(1.0D, plugin.getConfig().getDouble("celebration.nearby-title.radius", 10.0D));
        int fadeIn = plugin.getConfig().getInt("celebration.nearby-title.fade-in", 10);
        int stay = plugin.getConfig().getInt("celebration.nearby-title.stay", 40);
        int fadeOut = plugin.getConfig().getInt("celebration.nearby-title.fade-out", 10);
        String title = CC.translate(plugin.getConfig().getString("celebration.nearby-title.title", "&6%winner% gano!"));
        String subtitle = CC.translate(plugin.getConfig().getString("celebration.nearby-title.subtitle", "&eEn %room%"));

        for (Player nearby : center.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(center) > radius * radius) {
                continue;
            }
            if (plugin.getRoomManager().getRoomByPlayer(nearby).isPresent()) {
                continue;
            }
            nearby.sendTitle(title.replace("%winner%", winnerName).replace("%room%", roomName),
                    subtitle.replace("%winner%", winnerName).replace("%room%", roomName),
                    fadeIn, stay, fadeOut);
        }
    }

    private Color parseColor(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toUpperCase();
        switch (normalized) {
            case "AQUA":
                return Color.AQUA;
            case "BLACK":
                return Color.BLACK;
            case "BLUE":
                return Color.BLUE;
            case "FUCHSIA":
                return Color.FUCHSIA;
            case "GRAY":
                return Color.GRAY;
            case "GREEN":
                return Color.GREEN;
            case "LIME":
                return Color.LIME;
            case "MAROON":
                return Color.MAROON;
            case "NAVY":
                return Color.NAVY;
            case "OLIVE":
                return Color.OLIVE;
            case "ORANGE":
                return Color.ORANGE;
            case "PURPLE":
                return Color.PURPLE;
            case "RED":
                return Color.RED;
            case "SILVER":
                return Color.SILVER;
            case "TEAL":
                return Color.TEAL;
            case "WHITE":
                return Color.WHITE;
            case "YELLOW":
                return Color.YELLOW;
            default:
                return null;
        }
    }

    private int countOnlineByClan(String clanName) {
        if (clanName == null || clanName.trim().isEmpty() || plugin.getClanProvider() == null) {
            return 0;
        }
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            String onlineClan = plugin.getClanProvider().getClanName(online);
            if (onlineClan != null && normalizeClan(onlineClan).equals(normalizeClan(clanName))) {
                count++;
            }
        }
        return count;
    }

    private void scheduleClanLobbyTimeout(Room room) {
        cancelClanLobbyTimeout(room.getName());
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> cancelClanLobby(room, false), 20L * 60L);
        ClanLobby lobby = clanLobbies.get(room.getName().toLowerCase());
        if (lobby != null) {
            lobby.timeoutTask = task;
        }
    }

    private void cancelClanLobbyTimeout(String roomName) {
        ClanLobby lobby = clanLobbies.get(roomName.toLowerCase());
        if (lobby != null && lobby.timeoutTask != null) {
            lobby.timeoutTask.cancel();
            lobby.timeoutTask = null;
        }
    }

    private void cancelClanLobby(Room room, boolean byOwner) {
        ClanLobby lobby = clanLobbies.remove(room.getName().toLowerCase());
        if (lobby != null && lobby.timeoutTask != null) {
            lobby.timeoutTask.cancel();
        }

        // Broadcast cancellation before clearing players
        room.broadcast(plugin.getConfigManager().getMessage(byOwner ? "clan_lobby_cancelled_owner" : "clan_lobby_cancelled_timeout")
                .replace("%room%", room.getName()));

        // Teleport all players back to spawn before clearing the room
        teleportToSpawn(new java.util.ArrayList<>(room.getPlayers()));
        teleportToSpawn(new java.util.ArrayList<>(room.getSpectators()));

        room.getPlayers().clear();
        room.getSpectators().clear();
        room.setState(RoomState.WAITING);
        setDoors(room, false);
    }

    private String normalizeClan(String clan) {
        return clan == null ? "" : clan.toLowerCase().trim();
    }

    private int countPlayersInClan(Room room, String normalizedClan) {
        if (room == null || normalizedClan == null || normalizedClan.isEmpty() || plugin.getClanProvider() == null) {
            return 0;
        }
        int count = 0;
        for (UUID uuid : room.getPlayers()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null) {
                continue;
            }
            String memberClan = plugin.getClanProvider().getClanName(member);
            if (normalizeClan(memberClan).equals(normalizedClan)) {
                count++;
            }
        }
        return count;
    }

    private boolean isValidClanTeamComposition(Room room) {
        if (room == null || room.getType() != RoomType.CLAN || plugin.getClanProvider() == null) {
            return true;
        }
        ClanLobby lobby = clanLobbies.get(room.getName().toLowerCase());
        if (lobby == null || lobby.opponentClan == null) {
            return false;
        }
        String ownerClan = normalizeClan(lobby.ownerClan);
        String opponentClan = normalizeClan(lobby.opponentClan);
        int ownerCount = countPlayersInClan(room, ownerClan);
        int opponentCount = countPlayersInClan(room, opponentClan);
        int max = room.getPlayersPerTeam();

        if (ownerCount > max || opponentCount > max) {
            return false;
        }
        for (UUID uuid : room.getPlayers()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null) {
                continue;
            }
            String memberClan = normalizeClan(plugin.getClanProvider().getClanName(member));
            if (!memberClan.equals(ownerClan) && !memberClan.equals(opponentClan)) {
                return false;
            }
        }
        return ownerCount == opponentCount && ownerCount == max;
    }

    private void playClanJoinEffects(Player player, Room room) {
        if (player == null || room == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("clan-join-effects.enabled", true)) {
            return;
        }

        String clan = plugin.getClanProvider() == null ? null : plugin.getClanProvider().getClanName(player);
        String safeClan = safeClanName(clan);

        if (plugin.getConfig().getBoolean("clan-join-effects.title.enabled", true)) {
            String title = CC.translate(plugin.getConfig().getString("clan-join-effects.title.text", "&6&lCLAN ROOM"));
            String subtitle = CC.translate(plugin.getConfig().getString("clan-join-effects.title.subtitle", "&f%room% &7- &6%clan%"));
            int fadeIn = Math.max(0, plugin.getConfig().getInt("clan-join-effects.title.fade-in", 10));
            int stay = Math.max(1, plugin.getConfig().getInt("clan-join-effects.title.stay", 40));
            int fadeOut = Math.max(0, plugin.getConfig().getInt("clan-join-effects.title.fade-out", 10));
            player.sendTitle(title.replace("%room%", room.getName()).replace("%clan%", safeClan),
                    subtitle.replace("%room%", room.getName()).replace("%clan%", safeClan),
                    fadeIn, stay, fadeOut);
        }

        if (plugin.getConfig().getBoolean("clan-join-effects.actionbar.enabled", true)) {
            String actionbar = CC.translate(plugin.getConfig().getString("clan-join-effects.actionbar.text", "&6Clan: &f%clan% &8| &eRoom: &f%room%"));
            sendActionBar(player, actionbar.replace("%room%", room.getName()).replace("%clan%", safeClan));
        }

        if (plugin.getConfig().getBoolean("clan-join-effects.sound.enabled", true)) {
            String soundName = plugin.getConfig().getString("clan-join-effects.sound.name", "BLOCK_BEACON_ACTIVATE");
            float volume = (float) plugin.getConfig().getDouble("clan-join-effects.sound.volume", 1.0D);
            float pitch = (float) plugin.getConfig().getDouble("clan-join-effects.sound.pitch", 1.15D);
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private Player resolveAnyOnlinePlayer(List<UUID> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (UUID uuid : candidates) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                return player;
            }
        }
        return null;
    }

    private void notifyInvitedClan(String clanName, String roomName) {
        if (plugin.getClanProvider() == null || clanName == null || clanName.trim().isEmpty()) {
            return;
        }
        String normalized = normalizeClan(clanName);
        for (Player online : Bukkit.getOnlinePlayers()) {
            String onlineClan = plugin.getClanProvider().getClanName(online);
            if (normalizeClan(onlineClan).equals(normalized)) {
                online.sendMessage(plugin.getConfigManager().getMessage("clan_invite_notify")
                        .replace("%room%", roomName)
                        .replace("%clan%", clanName));
            }
        }
    }
    public void forceEndMatchOnShutdown(Room room) {
        cancelCountdown(room.getName());
        cancelTimeLimit(room.getName());
        roomTimeRemaining.remove(room.getName().toLowerCase());
        stopDuelBossBar(room.getName());

        teleportToSpawn(new ArrayList<>(room.getPlayers()));
        teleportToSpawn(new ArrayList<>(room.getSpectators()));
        cleanupRoom(room);
    }


    private void teleportToSpawn(List<UUID> players) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (p.isDead() || p.getHealth() <= 0.0D) {
                    pendingRespawnTeleport.add(uuid);
                } else {
                    p.teleport(plugin.getReturnSpawn());
                }
                p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    GameMode previous = spectatorModes.remove(uuid);
                    p.setGameMode(previous != null ? previous : GameMode.SURVIVAL);
                }
            } else {
                spawnOnJoin.add(uuid);
                spectatorModes.remove(uuid);
            }
        }
    }

    private void cleanupRoom(Room room) {
        room.getPlayers().clear();
        room.getSpectators().clear();
        roomTeams.remove(room.getName().toLowerCase());
        clanLobbies.remove(room.getName().toLowerCase());
        room.setState(RoomState.WAITING);
        setDoors(room, false);
    }

    private static final class ClanLobby {
        private final UUID owner;
        private final String ownerClan;
        private final Set<String> invitedClans = new HashSet<>();
        private String opponentClan;
        private BukkitTask timeoutTask;

        private ClanLobby(UUID owner, String ownerClan) {
            this.owner = owner;
            this.ownerClan = ownerClan;
        }
    }

    private Location resolveValidSpectatorTarget(Room room) {
        if (room == null) {
            return null;
        }
        Location spectator = room.getSpectatorSpawn();
        if (isInsideArena(room, spectator)) {
            return spectator;
        }
        Location fallback1 = room.getSpawn1();
        if (isInsideArena(room, fallback1)) {
            return fallback1;
        }
        Location fallback2 = room.getSpawn2();
        if (isInsideArena(room, fallback2)) {
            return fallback2;
        }
        return null;
    }

    private boolean isInsideArena(Room room, Location location) {
        if (room == null || location == null || location.getWorld() == null) {
            return false;
        }
        Optional<WorldGuardHook.RegionBounds> bounds = plugin.getRoomManager().getArenaBounds(room);
        if (!bounds.isPresent()) {
            return room.isInArena(location);
        }
        WorldGuardHook.RegionBounds region = bounds.get();
        if (!location.getWorld().getName().equalsIgnoreCase(region.getWorldName())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int minX = Math.min(region.getMinX(), region.getMaxX());
        int maxX = Math.max(region.getMinX(), region.getMaxX());
        int minY = Math.min(region.getMinY(), region.getMaxY());
        int maxY = Math.max(region.getMinY(), region.getMaxY());
        int minZ = Math.min(region.getMinZ(), region.getMaxZ());
        int maxZ = Math.max(region.getMinZ(), region.getMaxZ());
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
