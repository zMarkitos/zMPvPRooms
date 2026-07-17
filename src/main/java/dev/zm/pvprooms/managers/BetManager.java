package dev.zm.pvprooms.managers;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.database.SQLiteDatabase;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.utils.CC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the betting system for rooms.
 *
 * <p>
 * Bets are placed against a specific player inside a room.
 * When the match ends,
 * {@link #processBetResults(String, Collection, Collection)} resolves all
 * active bets and dispatches the configured win/lose commands for each better.
 * </p>
 *
 * <p>
 * Active bets are kept in memory (fast, no DB round-trip during gameplay) and
 * also persisted to the database so they survive a mid-match server restart.
 * </p>
 */
public class BetManager {

    private final ZMPvPRooms plugin;

    /**
     * In-memory map: betterUUID → BetEntry.
     * A player can only have one active bet at a time across all rooms.
     */
    private final Map<UUID, BetEntry> activeBets = new HashMap<>();

    public BetManager(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    // GUI

    /**
     * Opens the betting GUI for {@code better}, showing the heads of all
     * players currently inside {@code room}.
     *
     * <p>
     * Pre-conditions (already validated by MainCommand):
     * </p>
     * <ul>
     * <li>Bets are globally enabled in config.</li>
     * <li>The room is in STARTING or PLAYING state.</li>
     * <li>The better is NOT a participant of this room.</li>
     * </ul>
     */
    public void openBetMenu(Player better, Room room) {
        List<UUID> roomPlayers = new ArrayList<>(room.getPlayers());
        if (roomPlayers.isEmpty()) {
            better.sendMessage(plugin.getConfigManager().getMessage("bets_not_enough_players"));
            return;
        }

        // Inventory size must be a multiple of 9, capped at 54
        int size = Math.min(54, ((roomPlayers.size() / 9) + 1) * 9);
        if (size < 9)
            size = 9;

        String title = CC.translate("§8Apuestas: §c" + room.getName());
        Inventory inv = Bukkit.createInventory(null, size, title);

        for (int i = 0; i < roomPlayers.size() && i < size; i++) {
            UUID uuid = roomPlayers.get(i);
            Player target = Bukkit.getPlayer(uuid);
            String name = target != null ? target.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null)
                name = uuid.toString().substring(0, 8);
            inv.setItem(i, buildPlayerHead(uuid, name));
        }

        // Register a pending BetEntry keyed by the better's UUID
        BetEntry entry = new BetEntry(better.getUniqueId(), room.getName());
        activeBets.put(better.getUniqueId(), entry);

        better.openInventory(inv);
    }

    // Bet confirmation

    /**
     * Confirms and registers a bet. Called from BetListener when the better
     * clicks a valid player head.
     *
     * @param better     The player placing the bet
     * @param targetUuid UUID of the player being bet on
     * @param targetName Display name of the target (for messages)
     */
    public void confirmBet(Player better, UUID targetUuid, String targetName) {
        BetEntry entry = activeBets.get(better.getUniqueId());
        if (entry == null)
            return;

        entry.targetUuid = targetUuid;
        entry.targetName = targetName;

        // Persist to database for restart resilience (async)
        if (plugin.getDatabase() != null) {
            final String betterUuid = better.getUniqueId().toString();
            final String betterName = better.getName();
            final String roomName = entry.roomName;
            final String tUuid = targetUuid.toString();
            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> plugin.getDatabase().saveBet(betterUuid, betterName, tUuid, roomName));
        }

        better.playSound(better.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        better.sendMessage(plugin.getConfigManager().getMessage("bets_placed")
                .replace("%player%", targetName)
                .replace("%room%", entry.roomName));
    }

    // Result processing

    /**
     * Processes all active bets for {@code roomName} once the match ends.
     * Dispatches win/lose commands and cleans up bet state.
     *
     * @param roomName Name of the room that just ended
     * @param winners  UUIDs of the winning team/players
     * @param losers   UUIDs of the losing team/players (may be empty on a draw)
     */
    public void processBetResults(String roomName, Collection<UUID> winners, Collection<UUID> losers) {
        if (roomName == null)
            return;

        boolean betsEnabled = plugin.getConfig().getBoolean("settings.bets-enabled", false);
        if (!betsEnabled)
            return;

        List<String> winCommands = plugin.getConfig().getStringList("bets.win-commands");
        List<String> loseCommands = plugin.getConfig().getStringList("bets.lose-commands");

        // Snapshot in-memory bets for this room, removing them from the live map
        List<BetEntry> toProcess = new ArrayList<>();
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, BetEntry> mapEntry : activeBets.entrySet()) {
            if (roomName.equalsIgnoreCase(mapEntry.getValue().roomName)) {
                toProcess.add(mapEntry.getValue());
                toRemove.add(mapEntry.getKey());
            }
        }
        toRemove.forEach(activeBets::remove);

        // Build a set of already-processed betterUUIDs to avoid DB duplicates
        Set<UUID> processedBetters = new HashSet<>();
        for (BetEntry e : toProcess) {
            processedBetters.add(e.betterUuid);
        }

        // Also load any persisted bets from DB (handles restart-survivors)
        if (plugin.getDatabase() != null) {
            List<SQLiteDatabase.BetRow> dbBets = plugin.getDatabase().getBetsByRoom(roomName);
            for (SQLiteDatabase.BetRow row : dbBets) {
                UUID betterUuid = parseUUID(row.betterUuid);
                if (betterUuid == null || processedBetters.contains(betterUuid))
                    continue;
                BetEntry extra = new BetEntry(betterUuid, roomName);
                extra.targetUuid = parseUUID(row.targetUuid);
                extra.targetName = row.betterName;
                toProcess.add(extra);
            }
            // Clean up DB bets for this room (async)
            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> plugin.getDatabase().removeBetsByRoom(roomName));
        }

        if (toProcess.isEmpty())
            return;

        Set<UUID> winnerSet = winners instanceof Set ? (Set<UUID>) winners
                : new HashSet<>(winners);

        boolean hasResult = !winners.isEmpty() || !losers.isEmpty();

        for (BetEntry bet : toProcess) {
            // Resolve the better player
            Player better = Bukkit.getPlayer(bet.betterUuid);
            String betterName = better != null ? better.getName()
                    : Bukkit.getOfflinePlayer(bet.betterUuid).getName();
            if (betterName == null)
                betterName = bet.betterUuid.toString().substring(0, 8);

            if (bet.targetUuid == null) {
                // No player was selected → treat as cancelled (no penalty)
                if (better != null) {
                    better.sendMessage(plugin.getConfigManager().getMessage("bets_cancelled"));
                }
                continue;
            }

            String targetDisplay = bet.targetName != null
                    ? bet.targetName
                    : bet.targetUuid.toString().substring(0, 8);

            boolean won = winnerSet.contains(bet.targetUuid);

            if (won) {
                if (better != null) {
                    better.sendMessage(plugin.getConfigManager().getMessage("bets_won")
                            .replace("%player%", targetDisplay));
                }
                executeCommands(betterName, winCommands);
            } else if (hasResult) {
                // Match had a clear result and the betted player didn't win
                if (better != null) {
                    better.sendMessage(plugin.getConfigManager().getMessage("bets_lost")
                            .replace("%player%", targetDisplay));
                }
                executeCommands(betterName, loseCommands);
            } else {
                // Draw — no winner, no loser
                if (better != null) {
                    better.sendMessage(plugin.getConfigManager().getMessage("bets_cancelled"));
                }
            }
        }
    }

    // Accessors used by BetListener and MainCommand

    /** Returns the active BetEntry for a player, or {@code null} if none. */
    public BetEntry getBetEntry(Player player) {
        return activeBets.get(player.getUniqueId());
    }

    /**
     * Removes the active BetEntry for a player (used when the GUI is discarded).
     */
    public void removeBetEntry(Player player) {
        activeBets.remove(player.getUniqueId());
    }

    /**
     * Legacy accessor kept for backwards compatibility.
     *
     * @deprecated Use {@link #getBetEntry(Player)} instead.
     */
    @Deprecated
    public BetData getBetData(Player player) {
        BetEntry entry = activeBets.get(player.getUniqueId());
        if (entry == null)
            return null;
        BetData data = new BetData();
        data.roomName = entry.roomName;
        data.targetPlayer = entry.targetUuid;
        return data;
    }

    /**
     * Legacy removal kept for backwards compatibility.
     *
     * @deprecated Use {@link #removeBetEntry(Player)} instead.
     */
    @Deprecated
    public void removeBetData(Player player) {
        removeBetEntry(player);
    }

    // Private helpers

    /**
     * Dispatches each command from the console, replacing {@code {player}} with
     * the better's name. Empty or null commands are silently skipped.
     */
    private void executeCommands(String betterName, List<String> commands) {
        if (commands == null || commands.isEmpty())
            return;
        for (String cmd : commands) {
            if (cmd == null || cmd.trim().isEmpty())
                continue;
            String resolved = cmd.replace("{player}", betterName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    private ItemStack buildPlayerHead(UUID uuid, String displayName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            meta.setDisplayName(CC.translate("&f" + displayName));
            List<String> lore = new ArrayList<>();
            lore.add(CC.translate("&7Click to bet on this player"));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static UUID parseUUID(String raw) {
        if (raw == null)
            return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Data classes

    /**
     * Internal state for an active bet, keyed by the better's UUID in the
     * {@link #activeBets} map. The betterUuid field is also stored here for
     * convenience when iterating the map values during result processing.
     */
    public static class BetEntry {
        /** UUID of the player who placed the bet. */
        public final UUID betterUuid;
        /** Name of the room this bet is for. */
        public final String roomName;
        /** UUID of the player being bet on (null until the better clicks a head). */
        public UUID targetUuid;
        /** Display name of the target player (for messages). */
        public String targetName;

        BetEntry(UUID betterUuid, String roomName) {
            this.betterUuid = betterUuid;
            this.roomName = roomName;
        }
    }

    /**
     * Legacy DTO kept for backwards API compatibility.
     *
     * @deprecated Migrate callers to {@link BetEntry}.
     */
    @Deprecated
    public static class BetData {
        public String roomName;
        public UUID targetPlayer;
        public List<ItemStack> itemsBet = new ArrayList<>();
    }
}
