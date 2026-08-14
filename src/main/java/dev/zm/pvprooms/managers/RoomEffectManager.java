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
 * Manages the lifecycle of Room-applied potion effects.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Before applying Room effects, we snapshot any effects the player already has
 *       whose type conflicts with Room effects. When Room effects are removed, those
 *       snapshots are restored instead of simply deleting the effects.</li>
 *   <li>Effects with duration {@code Integer.MAX_VALUE} are treated as "permanent"
 *       (lasting the full match). They are always re-applied on every call to
 *       {@link #applyEffects} to ensure they do not expire prematurely.</li>
 *   <li>All operations are keyed by {@code UUID} so there is no per-Player object
 *       retained beyond the duration of a match.</li>
 * </ul>
 */
public class RoomEffectManager {

    /** Stores effects the player had BEFORE the Room applied its own, keyed by player UUID.
     *  Only types that the Room will override are stored here. */
    private final Map<UUID, Map<PotionEffectType, PotionEffect>> savedEffects = new HashMap<>();

    private final ZMPvPRooms plugin;

    public RoomEffectManager(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies all configured Room effects to a player.
     *
     * <p>Before applying, any pre-existing effects whose type matches a Room effect are
     * saved so they can be restored when the player leaves.
     *
     * @param player the target player
     * @param room   the room whose effects to apply
     */
    public void applyEffects(Player player, Room room) {
        if (player == null || room == null || room.getEffects().isEmpty()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // Snapshot conflicting pre-existing effects (only once per player-room association).
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

        // Apply Room effects (permanent = Integer.MAX_VALUE ticks, ambient = false, particles = true)
        for (PotionEffect effect : room.getEffects()) {
            // Force amplifier stored in the effect (0-indexed, so 0 = level I, 1 = level II …)
            player.addPotionEffect(new PotionEffect(
                    effect.getType(),
                    Integer.MAX_VALUE, // permanent while in room
                    effect.getAmplifier(),
                    false,   // not ambient
                    true,    // show particles
                    true     // show icon
            ), true); // override existing
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
            // Always clean up the snapshot even if room has no effects now
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
        // Also handle any spectators who received effects
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
