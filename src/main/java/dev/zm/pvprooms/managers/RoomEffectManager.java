package dev.zm.pvprooms.managers;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.models.Room;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the lifecycle of room-applied potion effects. Conflicting effects are
 * snapshotted before applying room effects and restored when they are removed.
 */
public class RoomEffectManager {

    private final Map<UUID, Map<PotionEffectType, PotionEffect>> savedEffects = new HashMap<>();

    private final ZMPvPRooms plugin;

    public RoomEffectManager(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies all configured room effects to a player, saving conflicting effects
     * first so they can be restored later.
     */
    public void applyEffects(Player player, Room room) {
        if (player == null || room == null || room.getEffects().isEmpty()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (!savedEffects.containsKey(uuid)) {
            Map<PotionEffectType, PotionEffect> snapshot = new HashMap<>();
            for (PotionEffect roomEffect : room.getEffects()) {
                PotionEffect existing = player.getPotionEffect(roomEffect.getType());
                if (existing != null) {
                    snapshot.put(existing.getType(), existing);
                }
            }
            savedEffects.put(uuid, snapshot);
        }

        for (PotionEffect effect : room.getEffects()) {
            player.addPotionEffect(new PotionEffect(
                    effect.getType(),
                    Integer.MAX_VALUE,
                    effect.getAmplifier(),
                    false,
                    true,
                    true
            ), true);
        }
    }

    /**
     * Removes Room effects from a player and restores any pre-existing effects that
     * were snapshotted when Room effects were applied.
     *
     * @param player the target player
     * @param room   the room whose effects to remove
     */
    public void removeEffects(Player player, Room room) {
        if (player == null || room == null || room.getEffects().isEmpty()) {
            if (player != null) {
                savedEffects.remove(player.getUniqueId());
            }
            return;
        }

        UUID uuid = player.getUniqueId();
        Map<PotionEffectType, PotionEffect> snapshot = savedEffects.remove(uuid);

        for (PotionEffect roomEffect : room.getEffects()) {
            PotionEffect current = player.getPotionEffect(roomEffect.getType());
            if (current == null) {
                continue;
            }
            // Only remove if it matches the amplifier the Room applied — this avoids
            // accidentally removing a higher-tier effect the player got from another source
            // after entering the room.
            if (current.getAmplifier() == roomEffect.getAmplifier()) {
                player.removePotionEffect(roomEffect.getType());
            }

            // Restore the snapshot if the player had this effect type before the Room applied it
            if (snapshot != null) {
                PotionEffect saved = snapshot.get(roomEffect.getType());
                if (saved != null) {
                    player.addPotionEffect(saved, true);
                }
            }
        }
    }

    /**
     * Removes Room effects from every player currently in the given room.
     * Called when a match ends, room resets, or room is deleted.
     *
     * @param room the room to clean up
     */
    public void removeAllEffectsForRoom(Room room) {
        if (room == null) {
            return;
        }

        for (UUID uuid : room.getPlayers()) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                removeEffects(player, room);
            } else {
                // Player is offline — just clean up the snapshot so we don't leak memory.
                savedEffects.remove(uuid);
            }
        }
        for (UUID uuid : room.getSpectators()) {
            savedEffects.remove(uuid);
        }
    }

    /**
     * Removes the snapshot entry for a player without attempting to remove or restore
     * any effects. Used when the player is offline or when we know effects have already
     * been cleaned up externally (e.g. server restart clears all effects).
     *
     * @param uuid the player's UUID
     */
    public void clearSnapshot(UUID uuid) {
        savedEffects.remove(uuid);
    }

    /**
     * Clears all stored snapshots. Called on plugin disable to prevent memory leaks.
     */
    public void clearAll() {
        savedEffects.clear();
    }
}
