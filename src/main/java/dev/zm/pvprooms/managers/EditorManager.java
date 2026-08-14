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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class EditorManager {

    private final ZMPvPRooms plugin;

    /**
     * Players who are in "block-select" mode: clicking a block in the world will set
     * that block as the door material for the given room name.
     */
    private final Map<UUID, String> pendingBlockSelect = new HashMap<>();

    private static final int[] ROOM_LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    // ─── Inventory Holders ────────────────────────────────────────────────────

    public static class RoomsListHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
    }

    public static class RoomMenuHolder implements InventoryHolder {
        private final String roomName;
        private Inventory inventory;
        public RoomMenuHolder(String roomName) { this.roomName = roomName; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public String getRoomName() { return roomName; }
    }

    public static class RoomEditorHolder implements InventoryHolder {
        private final String roomName;
        private Inventory inventory;
        public RoomEditorHolder(String roomName) { this.roomName = roomName; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public String getRoomName() { return roomName; }
    }

    /** GUI for managing the potion effects of a room. */
    public static class EffectsEditorHolder implements InventoryHolder {
        private final String roomName;
        private Inventory inventory;
        public EffectsEditorHolder(String roomName) { this.roomName = roomName; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public String getRoomName() { return roomName; }
    }

    /** Picker GUI for selecting a new potion effect type to add. */
    public static class PotionPickerHolder implements InventoryHolder {
        private final String roomName;
        private Inventory inventory;
        public PotionPickerHolder(String roomName) { this.roomName = roomName; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public String getRoomName() { return roomName; }
    }

    // ─── Constructor ──────────────────────────────────────────────────────────

    public EditorManager(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    // ─── Block-select mode ────────────────────────────────────────────────────

    /** Returns true if the player is waiting to select a block for a room. */
    public boolean isAwaitingBlockSelect(UUID uuid) {
        return pendingBlockSelect.containsKey(uuid);
    }

    /** Returns the room name that the player is selecting a block for, or null. */
    public String getBlockSelectRoom(UUID uuid) {
        return pendingBlockSelect.get(uuid);
    }

    /** Removes the player from block-select mode. */
    public void cancelBlockSelect(UUID uuid) {
        pendingBlockSelect.remove(uuid);
    }

    /**
     * Puts the player into "block-select" mode and closes their inventory so they
     * can right-click any block to set it as the door material.
     */
    public void beginBlockSelect(Player player, String roomName) {
        pendingBlockSelect.put(player.getUniqueId(), roomName);
        player.closeInventory();
        player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("block_select_prompt")));
    }

    // ─── Rooms List GUI ───────────────────────────────────────────────────────

    public void openRoomsList(Player player) {
        RoomsListHolder holder = new RoomsListHolder();
        Inventory gui = Bukkit.createInventory(holder, 54,
                plugin.getConfigManager().getRawMessage("guis.rooms_list_title"));
        holder.setInventory(gui);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, glass);

        int slotIndex = 0;
        for (Room room : plugin.getRoomManager().getRooms().values()) {
            if (slotIndex >= ROOM_LIST_SLOTS.length) break;

            List<String> lore = getGuiLore("guis.room_item_lore")
                    .stream()
                    .map(line -> line.replace("%type%", room.getType().name()))
                    .map(line -> line.replace("%state%", room.getState().name()))
                    .toList();
            java.util.ArrayList<String> loreWithId = new java.util.ArrayList<>(lore);
            loreWithId.add("\u00268ID:" + room.getName().toLowerCase());
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

    // ─── Room Menu GUI ────────────────────────────────────────────────────────

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
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, glass);

        ItemStack config = createItem(Material.ANVIL,
                plugin.getConfigManager().getRawMessage("guis.menu_config_name"),
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
        ItemStack info = createItem(Material.BOOK,
                plugin.getConfigManager().getRawMessage("guis.menu_info_name"), infoLore);

        ItemStack tp = createItem(Material.ENDER_PEARL,
                plugin.getConfigManager().getRawMessage("guis.menu_tp_name"),
                getGuiLore("guis.menu_tp_lore"));
        ItemStack back = createItem(Material.ARROW,
                plugin.getConfigManager().getRawMessage("guis.menu_back_name"),
                getGuiLore("guis.menu_back_lore"));

        gui.setItem(11, config);
        gui.setItem(13, info);
        gui.setItem(15, tp);
        gui.setItem(22, back);

        player.openInventory(gui);
    }

    // ─── Room Editor GUI ──────────────────────────────────────────────────────

    public void openEditor(Player player, String roomName) {
        Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
        if (!optRoom.isPresent()) return;

        Room room = optRoom.get();
        RoomEditorHolder holder = new RoomEditorHolder(room.getName());
        Inventory gui = Bukkit.createInventory(holder, 54,
                plugin.getConfigManager().getRawMessage("guis.editor_title").replace("%room%", room.getName()));
        holder.setInventory(gui);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, glass);

        String enabled = plugin.getConfigManager().getRawMessage("general.enabled");
        String disabled = plugin.getConfigManager().getRawMessage("general.disabled");

        ItemStack typeItem = createItem(Material.NAME_TAG,
                plugin.getConfigManager().getRawMessage("guis.editor_type_name"),
                getGuiLore("guis.editor_type_lore").stream()
                        .map(line -> line.replace("%type%", room.getType().name())).toList());

        ItemStack keepInvItem = createItem(Material.CHEST,
                plugin.getConfigManager().getRawMessage("guis.editor_keepinv_name"),
                getGuiLore("guis.editor_keepinv_lore").stream()
                        .map(line -> line.replace("%status%", room.isKeepInventory() ? enabled : disabled)).toList());

        // Door material with custom block selection hint
        List<String> doorLore = new ArrayList<>(getGuiLore("guis.editor_doors_lore").stream()
                .map(line -> line.replace("%material%", room.getDoorMaterial().name())).toList());
        ItemStack doorMatItem = createItem(room.getDoorMaterial(),
                plugin.getConfigManager().getRawMessage("guis.editor_doors_name"), doorLore);

        // Custom block select item
        List<String> blockLore = new ArrayList<>(getGuiLore("guis.editor_block_lore").stream()
                .map(line -> line.replace("%material%", room.getDoorMaterial().name())).toList());
        ItemStack blockItem = createItem(Material.WRITABLE_BOOK,
                plugin.getConfigManager().getRawMessage("guis.editor_block_name"), blockLore);

        ItemStack teamSizeItem = createItem(Material.ARMOR_STAND,
                plugin.getConfigManager().getRawMessage("guis.editor_teamsize_name"),
                getGuiLore("guis.editor_teamsize_lore").stream()
                        .map(line -> line.replace("%size%", String.valueOf(room.getPlayersPerTeam()))
                                .replace("%capacity%", String.valueOf(room.getCapacity()))).toList());

        ItemStack minPlayersItem = createItem(Material.TOTEM_OF_UNDYING,
                plugin.getConfigManager().getRawMessage("guis.editor_minplayers_name"),
                getGuiLore("guis.editor_minplayers_lore").stream()
                        .map(line -> line.replace("%min_players%", String.valueOf(room.getMinPlayersToStart()))
                                .replace("%capacity%", String.valueOf(room.getCapacity()))).toList());

        ItemStack delayItem = createItem(Material.CLOCK,
                plugin.getConfigManager().getRawMessage("guis.editor_delay_name"),
                getGuiLore("guis.editor_delay_lore").stream()
                        .map(line -> line.replace("%delay%", String.valueOf(room.getDoorOpenDelay()))).toList());

        ItemStack statusItem = createItem(Material.REDSTONE_TORCH,
                plugin.getConfigManager().getRawMessage("guis.editor_status_name"),
                getGuiLore("guis.editor_status_lore").stream()
                        .map(line -> line.replace("%status%", room.isEnabled() ? enabled : disabled)).toList());

        ItemStack actionItem = createItem(Material.PAPER,
                plugin.getConfigManager().getRawMessage("guis.editor_action_name"),
                getGuiLore("guis.editor_action_lore").stream()
                        .map(line -> line
                                .replace("%titles%", room.isTitlesEnabled() ? enabled : disabled)
                                .replace("%actionbar%", room.isActionBarEnabled() ? enabled : disabled)
                                .replace("%chat%", room.isChatEnabled() ? enabled : disabled)).toList());

        ItemStack betItem = createItem(Material.GOLD_INGOT,
                plugin.getConfigManager().getRawMessage("guis.editor_bet_name"),
                getGuiLore("guis.editor_bet_lore").stream()
                        .map(line -> line.replace("%mode%", room.getBetMode().name())).toList());

        String noLimit = plugin.getConfigManager().getRawMessage("general.no_limit");
        String maxTimeStr = room.getMaxDuelTime() == 0 ? noLimit : room.getMaxDuelTime() + "s";
        ItemStack timeItem = createItem(Material.COMPASS,
                plugin.getConfigManager().getRawMessage("guis.editor_maxtime_name"),
                getGuiLore("guis.editor_maxtime_lore").stream()
                        .map(line -> line.replace("%time%", maxTimeStr)).toList());

        ItemStack postTimeItem = createItem(Material.CLOCK,
                plugin.getConfigManager().getRawMessage("guis.editor_posttime_name"),
                getGuiLore("guis.editor_posttime_lore").stream()
                        .map(line -> line.replace("%time%", room.getPostMatchTeleportDelay() + "s")).toList());

        // Effects item: shows how many effects are configured
        String effectCount = String.valueOf(room.getEffects().size());
        ItemStack effectsItem = createItem(Material.BREWING_STAND,
                plugin.getConfigManager().getRawMessage("guis.editor_effects_name"),
                getGuiLore("guis.editor_effects_lore").stream()
                        .map(line -> line.replace("%count%", effectCount)).toList());

        ItemStack backItem = createItem(Material.ARROW,
                plugin.getConfigManager().getRawMessage("guis.editor_back_name"),
                getGuiLore("guis.editor_back_lore"));

        // Row 1 (slots 10-16)
        gui.setItem(10, typeItem);
        gui.setItem(12, keepInvItem);
        gui.setItem(14, doorMatItem);
        gui.setItem(16, actionItem);
        // Row 2 (slots 19-25)
        gui.setItem(19, teamSizeItem);
        gui.setItem(21, minPlayersItem);
        gui.setItem(23, delayItem);
        gui.setItem(25, betItem);
        // Row 3 (slots 28-34)
        gui.setItem(28, blockItem);      // Custom block selector
        gui.setItem(30, timeItem);
        gui.setItem(31, statusItem);     // Room status toggle
        gui.setItem(32, postTimeItem);
        gui.setItem(34, effectsItem);    // Effects manager

        gui.setItem(49, backItem);

        player.openInventory(gui);
    }

    // ─── Effects Editor GUI ───────────────────────────────────────────────────

    /**
     * Opens the effects management GUI for a room.
     * Slots 10-43 show current effects (up to 21 max).
     * Slot 46 = Add effect, Slot 52 = Back.
     */
    public void openEffectsEditor(Player player, String roomName) {
        Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
        if (!optRoom.isPresent()) return;

        Room room = optRoom.get();
        EffectsEditorHolder holder = new EffectsEditorHolder(room.getName());
        Inventory gui = Bukkit.createInventory(holder, 54,
                plugin.getConfigManager().getRawMessage("guis.effects_editor_title")
                        .replace("%room%", room.getName()));
        holder.setInventory(gui);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, glass);

        // Display existing effects in the center slots
        int[] effectSlots = {10, 11, 12, 13, 14, 15, 16,
                             19, 20, 21, 22, 23, 24, 25,
                             28, 29, 30, 31, 32, 33, 34};
        List<PotionEffect> effects = room.getEffects();
        for (int i = 0; i < effectSlots.length && i < effects.size(); i++) {
            PotionEffect effect = effects.get(i);
            gui.setItem(effectSlots[i], buildEffectItem(effect));
        }

        // Add effect button
        ItemStack addItem = createItem(Material.NETHER_STAR,
                plugin.getConfigManager().getRawMessage("guis.effects_add_name"),
                getGuiLore("guis.effects_add_lore"));
        gui.setItem(46, addItem);

        // Back button
        ItemStack backItem = createItem(Material.ARROW,
                plugin.getConfigManager().getRawMessage("guis.editor_back_name"),
                getGuiLore("guis.editor_back_lore"));
        gui.setItem(49, backItem);

        player.openInventory(gui);
    }

    /**
     * Builds a display item for a single PotionEffect.
     * Left-click = cycle amplifier (0→1→2→remove), Right-click = remove immediately.
     */
    private ItemStack buildEffectItem(PotionEffect effect) {
        Material mat = getPotionMaterial(effect.getType());
        String levelLabel = getRomanLevel(effect.getAmplifier());
        String typeName = effect.getType().getName().replace("_", " ");

        List<String> lore = getGuiLore("guis.effect_item_lore").stream()
                .map(line -> line
                        .replace("%effect%", typeName)
                        .replace("%level%", levelLabel)
                        .replace("%amplifier%", String.valueOf(effect.getAmplifier() + 1)))
                .toList();
        return createItem(mat, "&b" + typeName + " &7" + levelLabel, lore);
    }

    // ─── Potion Picker GUI ────────────────────────────────────────────────────

    /**
     * Opens a GUI for the player to pick a potion effect type to add to the room.
     * Only a curated subset of useful PvP effects is shown.
     */
    public void openPotionPicker(Player player, String roomName) {
        PotionPickerHolder holder = new PotionPickerHolder(roomName);
        Inventory gui = Bukkit.createInventory(holder, 54,
                plugin.getConfigManager().getRawMessage("guis.potion_picker_title"));
        holder.setInventory(gui);

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < gui.getSize(); i++) gui.setItem(i, glass);

        PotionEffectType[] types = PICKER_EFFECTS;
        int[] slots = {10, 11, 12, 13, 14, 15, 16,
                       19, 20, 21, 22, 23, 24, 25,
                       28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < slots.length && i < types.length; i++) {
            PotionEffectType type = types[i];
            if (type == null) continue;
            String name = type.getName().replace("_", " ");
            List<String> lore = getGuiLore("guis.potion_picker_item_lore").stream()
                    .map(line -> line.replace("%effect%", name))
                    .toList();
            ItemStack item = createItem(getPotionMaterial(type), "&b" + name, lore);
            gui.setItem(slots[i], item);
        }

        ItemStack backItem = createItem(Material.ARROW,
                plugin.getConfigManager().getRawMessage("guis.editor_back_name"),
                getGuiLore("guis.editor_back_lore"));
        gui.setItem(49, backItem);

        player.openInventory(gui);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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

    private static String getRomanLevel(int amplifier) {
        return switch (amplifier) {
            case 0 -> "I";
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            case 4 -> "V";
            default -> String.valueOf(amplifier + 1);
        };
    }

    /** Returns a representative Material to display for a PotionEffectType. */
    private static Material getPotionMaterial(PotionEffectType type) {
        if (type == null) return Material.POTION;
        String name = type.getName();
        return switch (name) {
            case "SPEED"            -> Material.SUGAR;
            case "SLOW"             -> Material.ICE;
            case "FAST_DIGGING"     -> Material.GOLDEN_PICKAXE;
            case "SLOW_DIGGING"     -> Material.WOODEN_PICKAXE;
            case "INCREASE_DAMAGE"  -> Material.DIAMOND_SWORD;
            case "HEAL"             -> Material.GOLDEN_APPLE;
            case "HARM"             -> Material.FERMENTED_SPIDER_EYE;
            case "JUMP"             -> Material.FEATHER;
            case "CONFUSION"        -> Material.NETHER_WART;
            case "REGENERATION"     -> Material.GLISTERING_MELON_SLICE;
            case "DAMAGE_RESISTANCE"-> Material.IRON_CHESTPLATE;
            case "FIRE_RESISTANCE"  -> Material.MAGMA_CREAM;
            case "WATER_BREATHING"  -> Material.PUFFERFISH;
            case "INVISIBILITY"     -> Material.GLASS;
            case "BLINDNESS"        -> Material.INK_SAC;
            case "NIGHT_VISION"     -> Material.GOLDEN_CARROT;
            case "HUNGER"           -> Material.ROTTEN_FLESH;
            case "WEAKNESS"         -> Material.BONE;
            case "POISON"           -> Material.SPIDER_EYE;
            case "WITHER"           -> Material.WITHER_SKELETON_SKULL;
            case "HEALTH_BOOST"     -> Material.NETHER_STAR;
            case "ABSORPTION"       -> Material.ENCHANTED_GOLDEN_APPLE;
            case "SATURATION"       -> Material.COOKED_BEEF;
            case "GLOWING"          -> Material.GLOWSTONE_DUST;
            case "LEVITATION"       -> Material.PHANTOM_MEMBRANE;
            case "LUCK"             -> Material.EMERALD;
            case "UNLUCK"           -> Material.COAL;
            default                 -> Material.POTION;
        };
    }

    /** Curated list of PotionEffectTypes shown in the picker. */
    private static final PotionEffectType[] PICKER_EFFECTS = {
            PotionEffectType.SPEED,
            PotionEffectType.SLOW,
            PotionEffectType.INCREASE_DAMAGE,
            PotionEffectType.DAMAGE_RESISTANCE,
            PotionEffectType.REGENERATION,
            PotionEffectType.HEAL,
            PotionEffectType.HEALTH_BOOST,
            PotionEffectType.ABSORPTION,
            PotionEffectType.SATURATION,
            PotionEffectType.JUMP,
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.WATER_BREATHING,
            PotionEffectType.NIGHT_VISION,
            PotionEffectType.INVISIBILITY,
            PotionEffectType.FAST_DIGGING,
            PotionEffectType.SLOW_DIGGING,
            PotionEffectType.BLINDNESS,
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.WEAKNESS,
            PotionEffectType.LUCK,
    };

    /** Returns the index in room.getEffects() for an item in the effects editor at the given slot, or -1. */
    public int effectIndexForSlot(int slot) {
        int[] effectSlots = {10, 11, 12, 13, 14, 15, 16,
                             19, 20, 21, 22, 23, 24, 25,
                             28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < effectSlots.length; i++) {
            if (effectSlots[i] == slot) return i;
        }
        return -1;
    }

    /** Returns the PotionEffectType at a given slot of the picker GUI, or null. */
    public PotionEffectType effectTypeForPickerSlot(int slot) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16,
                       19, 20, 21, 22, 23, 24, 25,
                       28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return (i < PICKER_EFFECTS.length) ? PICKER_EFFECTS[i] : null;
            }
        }
        return null;
    }
}
