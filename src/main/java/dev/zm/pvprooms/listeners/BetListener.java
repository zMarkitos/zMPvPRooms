package dev.zm.pvprooms.listeners;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.managers.BetManager;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.utils.CC;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles player interaction inside the betting GUI.
 *
 * <p>The GUI title always starts with {@code §8Apuestas: §c<roomName>}.</p>
 */
public class BetListener implements Listener {

    /** Translated prefix used to identify a bet inventory by title. */
    private static final String GUI_PREFIX = CC.translate("§8Apuestas: §c");

    private final ZMPvPRooms plugin;

    public BetListener(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    // Inventory click

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_PREFIX)) {
            return;
        }

        // Always cancel to prevent item theft
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player better = (Player) event.getWhoClicked();
        BetManager.BetEntry entry = plugin.getBetManager().getBetEntry(better);
        if (entry == null) {
            better.closeInventory();
            return;
        }

        // Validate the room is still active
        Optional<Room> roomOpt = plugin.getRoomManager().getRoom(entry.roomName);
        if (!roomOpt.isPresent()) {
            better.closeInventory();
            plugin.getBetManager().removeBetEntry(better);
            better.sendMessage(plugin.getConfigManager().getMessage("room_not_found")
                    .replace("%room%", entry.roomName));
            return;
        }

        // Only handle clicks inside the top inventory (the bet GUI itself)
        if (event.getClickedInventory() == null
                || event.getClickedInventory() == event.getView().getBottomInventory()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) {
            return;
        }

        SkullMeta meta = (SkullMeta) clicked.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) {
            return;
        }

        UUID targetUuid = meta.getOwningPlayer().getUniqueId();
        String targetName = meta.getOwningPlayer().getName();
        if (targetName == null) {
            targetName = targetUuid.toString().substring(0, 8);
        }

        // Check the target is still in the room
        if (!roomOpt.get().getPlayers().contains(targetUuid)) {
            better.sendMessage(plugin.getConfigManager().getMessage("bets_player_not_in_room"));
            return;
        }

        // Confirm the bet and close the GUI
        plugin.getBetManager().confirmBet(better, targetUuid, targetName);
        better.closeInventory();
    }

    // Inventory close

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_PREFIX)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player better = (Player) event.getPlayer();
        BetManager.BetEntry entry = plugin.getBetManager().getBetEntry(better);
        if (entry == null) {
            return;
        }

        if (entry.targetUuid == null) {
            // Player closed without selecting anyone → discard the pending entry
            plugin.getBetManager().removeBetEntry(better);
        }
        // If targetUuid is set, confirmBet() already cleaned up and sent the message.
        // Nothing else to do on close.
    }
}
