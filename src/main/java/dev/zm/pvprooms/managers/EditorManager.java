package dev.zm.pvprooms.managers;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.utils.CC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;

public class EditorManager {

    private final ZMPvPRooms plugin;
    private static final int[] ROOM_LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public static class RoomsListHolder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public Inventory getInventory() {
            return inventory;
        }
        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class RoomMenuHolder implements InventoryHolder {
        private final String roomName;
        private Inventory inventory;

        public RoomMenuHolder(String roomName) {
            this.roomName = roomName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public String getRoomName() {
            return roomName;
        }
    }

    public static class RoomEditorHolder implements InventoryHolder {
        private final String roomName;
        private Inventory inventory;

        public RoomEditorHolder(String roomName) {
            this.roomName = roomName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public String getRoomName() {
            return roomName;
        }
    }

    public EditorManager(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    public void openRoomsList(Player player) {
        RoomsListHolder holder = new RoomsListHolder();
        Inventory gui = Bukkit.createInventory(holder, 54,
                plugin.getConfigManager().getRawMessage("guis.rooms_list_title"));
        holder.setInventory(gui);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, glass);
        }

        int slotIndex = 0;
        for (Room room : plugin.getRoomManager().getRooms().values()) {
            if (slotIndex >= ROOM_LIST_SLOTS.length) {
                break;
            }

            List<String> lore = getGuiLore("guis.room_item_lore")
                    .stream()
                    .map(line -> line.replace("%type%", room.getType().name()))
                    .map(line -> line.replace("%state%", room.getState().name()))
                    .toList();
            java.util.ArrayList<String> loreWithId = new java.util.ArrayList<>(lore);
            loreWithId.add("&8ID:" + room.getName().toLowerCase());
            String itemName = getGuiText("guis.room_item_name", "&e%room%")
                    .replace("%room%", room.getName());
            ItemStack item = createItem(Material.MAP, itemName, loreWithId);
            gui.setItem(ROOM_LIST_SLOTS[slotIndex++], item);
        }

        ItemStack close = createItem(Material.RED_WOOL,
                plugin.getConfigManager().getRawMessage("guis.rooms_list_close_name"),
                getGuiLore("guis.rooms_list_close_lore"));
        gui.setItem(49, close);
        player.openInventory(gui);
    }

    public void openRoomMenu(Player player, String roomName) {
        Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
        if (!optRoom.isPresent()) {
            player.sendMessage(CC.translate("&cLa sala no existe."));
            return;
        }
        Room room = optRoom.get();
        boolean hasArena = plugin.getRoomManager().hasArena(room);
        boolean hasSpawn1 = room.getSpawn1() != null;
        boolean hasSpawn2 = room.getSpawn2() != null;
        boolean hasSpec = room.getSpectatorSpawn() != null;
        boolean configured = plugin.getRoomManager().isConfigured(room);
        String ok = plugin.getConfigManager().getRawMessage("general.enabled");
        String no = plugin.getConfigManager().getRawMessage("general.disabled");
        String regionValue = room.getArenaRegionName() == null ? "-" : room.getArenaRegionName();

        RoomMenuHolder holder = new RoomMenuHolder(room.getName());
        Inventory gui = Bukkit.createInventory(holder, 27,
                plugin.getConfigManager().getRawMessage("guis.room_menu_title").replace("%room%", room.getName()));
        holder.setInventory(gui);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, glass);
        }

        ItemStack config = createItem(Material.ANVIL, plugin.getConfigManager().getRawMessage("guis.menu_config_name"),
                getGuiLore("guis.menu_config_lore"));

        List<String> infoLore = getGuiLore("guis.menu_info_lore")
                .stream()
                .map(line -> line.replace("%state%", room.getState().name())
                        .replace("%players%", String.valueOf(room.getPlayers().size()))
                        .replace("%capacity%", String.valueOf(room.getCapacity()))
                        .replace("%min_players%", String.valueOf(room.getMinPlayersToStart()))
                        .replace("%configured%", configured ? ok : no)
                        .replace("%region%", regionValue)
                        .replace("%arena%", hasArena ? ok : no)
                        .replace("%spawn1%", hasSpawn1 ? ok : no)
                        .replace("%spawn2%", hasSpawn2 ? ok : no)
                        .replace("%spectator%", hasSpec ? ok : no)
                        .replace("%entries%", String.valueOf(room.getDetectedEntranceBlocks())))
                .toList();
        ItemStack info = createItem(Material.BOOK, plugin.getConfigManager().getRawMessage("guis.menu_info_name"),
                infoLore);

        ItemStack tp = createItem(Material.ENDER_PEARL, plugin.getConfigManager().getRawMessage("guis.menu_tp_name"),
                getGuiLore("guis.menu_tp_lore"));
        ItemStack back = createItem(Material.ARROW, plugin.getConfigManager().getRawMessage("guis.menu_back_name"),
                getGuiLore("guis.menu_back_lore"));

        gui.setItem(11, config);
        gui.setItem(13, info);
        gui.setItem(15, tp);
        gui.setItem(22, back);

        player.openInventory(gui);
    }

    public void openEditor(Player player, String roomName) {
        Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
        if (!optRoom.isPresent()) {
            return;
        }

        Room room = optRoom.get();
        RoomEditorHolder holder = new RoomEditorHolder(room.getName());
        Inventory gui = Bukkit.createInventory(holder, 54,
                plugin.getConfigManager().getRawMessage("guis.editor_title").replace("%room%", room.getName()));
        holder.setInventory(gui);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, glass);
        }

        String enabled = plugin.getConfigManager().getRawMessage("general.enabled");
        String disabled = plugin.getConfigManager().getRawMessage("general.disabled");

        ItemStack typeItem = createItem(Material.NAME_TAG,
                plugin.getConfigManager().getRawMessage("guis.editor_type_name"),
                getGuiLore("guis.editor_type_lore")
                        .stream()
                        .map(line -> line.replace("%type%", room.getType().name()))
                        .toList());

        ItemStack keepInvItem = createItem(Material.CHEST,
                plugin.getConfigManager().getRawMessage("guis.editor_keepinv_name"),
                getGuiLore("guis.editor_keepinv_lore")
                        .stream()
                        .map(line -> line.replace("%status%", room.isKeepInventory() ? enabled : disabled))
                        .toList());

        ItemStack doorMatItem = createItem(room.getDoorMaterial(),
                plugin.getConfigManager().getRawMessage("guis.editor_doors_name"),
                getGuiLore("guis.editor_doors_lore")
                        .stream()
                        .map(line -> line.replace("%material%", room.getDoorMaterial().name()))
                        .toList());

        ItemStack teamSizeItem = createItem(Material.ARMOR_STAND,
                plugin.getConfigManager().getRawMessage("guis.editor_teamsize_name"),
                getGuiLore("guis.editor_teamsize_lore")
                        .stream()
                        .map(line -> line.replace("%size%", String.valueOf(room.getPlayersPerTeam()))
                                .replace("%capacity%", String.valueOf(room.getCapacity())))
                        .toList());

        ItemStack minPlayersItem = createItem(Material.TOTEM_OF_UNDYING,
                plugin.getConfigManager().getRawMessage("guis.editor_minplayers_name"),
                getGuiLore("guis.editor_minplayers_lore")
                        .stream()
                        .map(line -> line.replace("%min_players%", String.valueOf(room.getMinPlayersToStart()))
                                .replace("%capacity%", String.valueOf(room.getCapacity())))
                        .toList());

        ItemStack delayItem = createItem(Material.CLOCK,
                plugin.getConfigManager().getRawMessage("guis.editor_delay_name"),
                getGuiLore("guis.editor_delay_lore")
                        .stream()
                        .map(line -> line.replace("%delay%", String.valueOf(room.getDoorOpenDelay())))
                        .toList());

        ItemStack actionItem = createItem(Material.PAPER,
                plugin.getConfigManager().getRawMessage("guis.editor_action_name"),
                getGuiLore("guis.editor_action_lore")
                        .stream()
                        .map(line -> line
                                .replace("%titles%", room.isTitlesEnabled() ? enabled : disabled)
                                .replace("%actionbar%", room.isActionBarEnabled() ? enabled : disabled)
                                .replace("%chat%", room.isChatEnabled() ? enabled : disabled))
                        .toList());

        ItemStack betItem = createItem(Material.GOLD_INGOT,
                plugin.getConfigManager().getRawMessage("guis.editor_bet_name"),
                getGuiLore("guis.editor_bet_lore")
                        .stream()
                        .map(line -> line.replace("%mode%", room.getBetMode().name()))
                        .toList());

        String noLimit = plugin.getConfigManager().getRawMessage("general.no_limit");
        String maxTimeStr = room.getMaxDuelTime() == 0 ? noLimit : room.getMaxDuelTime() + "s";
        ItemStack timeItem = createItem(Material.COMPASS,
                plugin.getConfigManager().getRawMessage("guis.editor_maxtime_name"),
                getGuiLore("guis.editor_maxtime_lore")
                        .stream()
                        .map(line -> line.replace("%time%", maxTimeStr))
                        .toList());
        ItemStack postTimeItem = createItem(Material.CLOCK,
                plugin.getConfigManager().getRawMessage("guis.editor_posttime_name"),
                getGuiLore("guis.editor_posttime_lore")
                        .stream()
                        .map(line -> line.replace("%time%", room.getPostMatchTeleportDelay() + "s"))
                        .toList());

        ItemStack backItem = createItem(Material.ARROW,
                plugin.getConfigManager().getRawMessage("guis.editor_back_name"),
                getGuiLore("guis.editor_back_lore"));

        gui.setItem(10, typeItem);
        gui.setItem(12, keepInvItem);
        gui.setItem(14, doorMatItem);
        gui.setItem(16, actionItem);
        gui.setItem(19, teamSizeItem);
        gui.setItem(21, minPlayersItem);
        gui.setItem(23, delayItem);
        gui.setItem(25, betItem);
        gui.setItem(30, timeItem);
        gui.setItem(32, postTimeItem);

        gui.setItem(49, backItem);

        player.openInventory(gui);
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(CC.translate(name));
            if (!lore.isEmpty()) {
                meta.setLore(CC.translate(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> getGuiLore(String path) {
        return plugin.getConfigManager().getFlexibleMessageList(path);
    }

    private String getGuiText(String path, String fallback) {
        if (plugin.getConfigManager().getLang().isString(path)) {
            return plugin.getConfigManager().getRawMessage(path);
        }
        return fallback;
    }
}
