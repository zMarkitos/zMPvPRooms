package dev.zm.pvprooms.managers;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.models.enums.BetMode;
import dev.zm.pvprooms.models.enums.RoomState;
import dev.zm.pvprooms.utils.CC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BetManager {

    private final ZMPvPRooms plugin;
    private final Map<UUID, BetData> activeBets = new HashMap<>();

    public BetManager(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    public void openBetMenu(Player better, Room room) {
        better.sendMessage(plugin.getConfigManager().getMessage("bets_coming_soon"));
        return;
    }

    private ItemStack getPlayerHead(UUID uuid, String name, String loreLine) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            meta.setDisplayName(CC.translate(name));
            meta.setLore(List.of(CC.translate(loreLine)));
            head.setItemMeta(meta);
        }
        return head;
    }

    public BetData getBetData(Player player) {
        return activeBets.get(player.getUniqueId());
    }

    public void removeBetData(Player player) {
        activeBets.remove(player.getUniqueId());
    }

    public static class BetData {
        public String roomName;
        public UUID targetPlayer;
        public List<ItemStack> itemsBet = new ArrayList<>();
    }
}
