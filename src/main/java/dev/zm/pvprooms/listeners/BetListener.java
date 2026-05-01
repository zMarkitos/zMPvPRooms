package dev.zm.pvprooms.listeners;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.managers.BetManager;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.utils.CC;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Optional;

public class BetListener implements Listener {

    private final ZMPvPRooms plugin;

    public BetListener(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith(CC.translate("&8Apuestas: &c"))) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        BetManager.BetData betData = plugin.getBetManager().getBetData(player);
        if (betData == null) {
            return;
        }

        Optional<Room> roomOpt = plugin.getRoomManager().getRoom(betData.roomName);
        if (!roomOpt.isPresent()) {
            player.closeInventory();
            plugin.getBetManager().removeBetData(player);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            player.sendMessage(plugin.getConfigManager().getMessage("bets_items_disabled"));
            return;
        }

        if (event.getClickedInventory() == event.getView().getTopInventory() && clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                betData.targetPlayer = meta.getOwningPlayer().getUniqueId();
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                player.sendMessage(CC.translate("&aHas seleccionado apostar por: &e" + meta.getOwningPlayer().getName()));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().startsWith(CC.translate("&8Apuestas: &c"))) {
            return;
        }

        Player player = (Player) event.getPlayer();
        BetManager.BetData betData = plugin.getBetManager().getBetData(player);
        if (betData == null) {
            return;
        }

        if (betData.targetPlayer != null) {
            player.sendMessage(plugin.getConfigManager().getMessage("bets_selection_saved"));
        }

        plugin.getBetManager().removeBetData(player);
    }
}
