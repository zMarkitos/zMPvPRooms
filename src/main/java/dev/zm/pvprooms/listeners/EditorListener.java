package dev.zm.pvprooms.listeners;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.managers.EditorManager;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.models.enums.RoomType;
import dev.zm.pvprooms.utils.CC;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EditorListener implements Listener {

    private final ZMPvPRooms plugin;

    public EditorListener(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    // Cancel block-select mode on quit

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getEditorManager().cancelBlockSelect(event.getPlayer().getUniqueId());
    }

    // Block-select interaction

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getEditorManager().isAwaitingBlockSelect(player.getUniqueId())) {
            return;
        }

        // Only right-click on a block triggers selection
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        event.setCancelled(true);

        Block clicked = event.getClickedBlock();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // Prefer the block in hand over the clicked block (allows placing unseen
        // blocks)
        Material selected = (hand != null && hand.getType().isBlock() && hand.getType().isSolid()
                && hand.getType() != Material.AIR)
                        ? hand.getType()
                        : (clicked != null && clicked.getType().isSolid() && clicked.getType() != Material.AIR
                                ? clicked.getType()
                                : null);

        if (selected == null || !selected.isBlock() || !selected.isSolid()) {
            player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("block_select_invalid")));
            return;
        }

        String roomName = plugin.getEditorManager().getBlockSelectRoom(player.getUniqueId());
        plugin.getEditorManager().cancelBlockSelect(player.getUniqueId());

        Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
        if (!optRoom.isPresent()) {
            player.sendMessage(CC.translate("&cLa sala ya no existe."));
            return;
        }

        Room room = optRoom.get();
        room.setDoorMaterial(selected);
        plugin.getRoomManager().saveRooms();

        player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("block_select_success")
                .replace("%material%", selected.name())));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);

        // Re-open editor
        plugin.getEditorManager().openEditor(player, roomName);
    }

    // Inventory click handler

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        Object holder = event.getView().getTopInventory().getHolder();

        // Rooms list
        if (holder instanceof EditorManager.RoomsListHolder) {
            handleRoomsListClick(event, player);
            return;
        }

        // Room menu
        if (holder instanceof EditorManager.RoomMenuHolder roomMenuHolder) {
            handleRoomMenuClick(event, player, roomMenuHolder.getRoomName());
            return;
        }

        // Room editor
        if (holder instanceof EditorManager.RoomEditorHolder roomEditorHolder) {
            handleRoomEditorClick(event, player, roomEditorHolder.getRoomName());
            return;
        }

        // Effects editor
        if (holder instanceof EditorManager.EffectsEditorHolder effectsHolder) {
            handleEffectsEditorClick(event, player, effectsHolder.getRoomName());
            return;
        }

        // Potion picker
        if (holder instanceof EditorManager.PotionPickerHolder pickerHolder) {
            handlePotionPickerClick(event, player, pickerHolder.getRoomName());
        }
    }

    // Rooms list handler

    private void handleRoomsListClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory())
            return;
        if (event.getCurrentItem() == null)
            return;

        if (event.getCurrentItem().getType() == Material.RED_WOOL) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }
        if (event.getCurrentItem().getType() != Material.MAP || event.getCurrentItem().getItemMeta() == null)
            return;

        String roomName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
        List<String> lore = event.getCurrentItem().getItemMeta().getLore();
        if (lore != null) {
            for (String line : lore) {
                String clean = ChatColor.stripColor(line);
                if (clean == null)
                    continue;
                if (clean.startsWith("ID:") && clean.length() > 3) {
                    roomName = clean.substring(3).trim();
                    break;
                }
            }
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        plugin.getEditorManager().openRoomMenu(player, roomName);
    }

    // Room menu handler

    private void handleRoomMenuClick(InventoryClickEvent event, Player player, String roomName) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory())
            return;

        Material clicked = event.getCurrentItem() != null ? event.getCurrentItem().getType() : Material.AIR;

        if (clicked == Material.ANVIL) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            plugin.getEditorManager().openEditor(player, roomName);
        } else if (clicked == Material.ENDER_PEARL) {
            plugin.getRoomManager().getRoom(roomName).ifPresent(room -> {
                if (room.getSpectatorSpawn() != null) {
                    player.teleport(room.getSpectatorSpawn());
                } else if (room.getArenaPos1() != null) {
                    player.teleport(room.getArenaPos1().clone().add(0.5, 1, 0.5));
                }
            });
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        } else if (clicked == Material.ARROW) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            plugin.getEditorManager().openRoomsList(player);
        }
    }

    // Room editor handler

    private void handleRoomEditorClick(InventoryClickEvent event, Player player, String roomName) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory())
            return;

        Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
        if (!optRoom.isPresent()) {
            player.closeInventory();
            return;
        }

        Room room = optRoom.get();
        int slot = event.getSlot();
        boolean leftClick = event.isLeftClick();
        boolean changed = false;

        switch (slot) {
            case 10 -> {
                room.setType(room.getType() == RoomType.NORMAL ? RoomType.CLAN : RoomType.NORMAL);
                changed = true;
            }
            case 11, 12 -> {
                room.setKeepInventory(!room.isKeepInventory());
                changed = true;
            }
            case 14 -> {
                cycleDoorMaterial(room);
                changed = true;
            }
            case 19 -> {
                room.setPlayersPerTeam(leftClick ? room.getPlayersPerTeam() + 1 : room.getPlayersPerTeam() - 1);
                if (room.getMinPlayersToStart() > room.getCapacity()) {
                    room.setMinPlayersToStart(room.getCapacity());
                }
                changed = true;
            }
            case 21 -> {
                room.setMinPlayersToStart(
                        leftClick ? room.getMinPlayersToStart() + 2 : room.getMinPlayersToStart() - 2);
                if (room.getMinPlayersToStart() > room.getCapacity()) {
                    room.setMinPlayersToStart(room.getCapacity());
                }
                changed = true;
            }
            case 23 -> {
                room.setDoorOpenDelay(leftClick ? room.getDoorOpenDelay() + 5 : room.getDoorOpenDelay() - 5);
                changed = true;
            }
            case 16 -> {
                if (event.isShiftClick()) {
                    room.setChatEnabled(!room.isChatEnabled());
                } else if (leftClick) {
                    room.setTitlesEnabled(!room.isTitlesEnabled());
                } else {
                    room.setActionBarEnabled(!room.isActionBarEnabled());
                }
                changed = true;
            }
            case 25 -> {
                cycleBetMode(room);
                changed = true;
            }
            case 28 -> {
                // Custom block selector: enter block-select mode
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                plugin.getEditorManager().beginBlockSelect(player, room.getName());
                return; // closes inventory inside beginBlockSelect
            }
            case 30 -> {
                cycleMaxTime(room);
                changed = true;
            }
            case 31 -> {
                room.setEnabled(!room.isEnabled());
                changed = true;
            }
            case 32 -> {
                cyclePostMatchTime(room, leftClick);
                changed = true;
            }
            case 34 -> {
                // Open effects editor
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                plugin.getEditorManager().openEffectsEditor(player, room.getName());
                return;
            }
            case 49 -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                plugin.getEditorManager().openRoomMenu(player, room.getName());
                return;
            }
            default -> {
                return;
            }
        }

        if (changed)
            plugin.getRoomManager().saveRooms();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        plugin.getEditorManager().openEditor(player, room.getName());
    }

    // Effects editor handler

    private void handleEffectsEditorClick(InventoryClickEvent event, Player player, String roomName) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory())
            return;

        Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
        if (!optRoom.isPresent()) {
            player.closeInventory();
            return;
        }

        Room room = optRoom.get();
        int slot = event.getSlot();

        // Back button
        if (slot == 49) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            plugin.getEditorManager().openEditor(player, roomName);
            return;
        }

        // Add effect button (slot 46)
        if (slot == 46) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            plugin.getEditorManager().openPotionPicker(player, roomName);
            return;
        }

        // Effect item: left click = cycle amplifier, right click = remove
        int effectIndex = plugin.getEditorManager().effectIndexForSlot(slot);
        if (effectIndex < 0 || effectIndex >= room.getEffects().size())
            return;

        List<PotionEffect> effects = new ArrayList<>(room.getEffects());
        PotionEffect existing = effects.get(effectIndex);

        if (event.isRightClick()) {
            // Remove immediately
            effects.remove(effectIndex);
        } else {
            // Cycle amplifier: 0 → 1 → 2 → remove
            int nextAmp = existing.getAmplifier() + 1;
            effects.remove(effectIndex);
            if (nextAmp <= 2) {
                effects.add(effectIndex, new PotionEffect(
                        existing.getType(), Integer.MAX_VALUE, nextAmp, false, true, true));
            }
            // if nextAmp > 2, effect is removed
        }

        room.clearEffects();
        for (PotionEffect e : effects)
            room.addEffect(e);

        plugin.getRoomManager().saveRooms();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        plugin.getEditorManager().openEffectsEditor(player, roomName);
    }

    // Potion picker handler

    private void handlePotionPickerClick(InventoryClickEvent event, Player player, String roomName) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory())
            return;

        int slot = event.getSlot();

        // Back button
        if (slot == 49) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            plugin.getEditorManager().openEffectsEditor(player, roomName);
            return;
        }

        PotionEffectType type = plugin.getEditorManager().effectTypeForPickerSlot(slot);
        if (type == null)
            return;

        Optional<Room> optRoom = plugin.getRoomManager().getRoom(roomName);
        if (!optRoom.isPresent()) {
            player.closeInventory();
            return;
        }

        Room room = optRoom.get();

        // If the effect type already exists, don't add a duplicate
        boolean alreadyExists = room.getEffects().stream()
                .anyMatch(e -> e.getType().equals(type));
        if (alreadyExists) {
            player.sendMessage(CC.translate(plugin.getConfigManager().getRawMessage("effects_already_added")));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            return;
        }

        // Add with amplifier 0 (level I) by default
        room.addEffect(new PotionEffect(type, Integer.MAX_VALUE, 0, false, true, true));
        plugin.getRoomManager().saveRooms();

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        plugin.getEditorManager().openEffectsEditor(player, roomName);
    }

    // Private helpers

    private void cycleDoorMaterial(Room room) {
        Material[] order = { Material.GLASS, Material.IRON_BLOCK, Material.OBSIDIAN, Material.BEDROCK };
        Material current = room.getDoorMaterial();
        for (int i = 0; i < order.length; i++) {
            if (current == order[i]) {
                room.setDoorMaterial(order[(i + 1) % order.length]);
                return;
            }
        }
        room.setDoorMaterial(Material.GLASS);
    }

    private void cycleBetMode(Room room) {
        dev.zm.pvprooms.models.enums.BetMode[] values = dev.zm.pvprooms.models.enums.BetMode.values();
        room.setBetMode(values[(room.getBetMode().ordinal() + 1) % values.length]);
    }

    private void cycleMaxTime(Room room) {
        int[] order = { 0, 60, 300, 600, 1800 };
        for (int i = 0; i < order.length; i++) {
            if (room.getMaxDuelTime() == order[i]) {
                room.setMaxDuelTime(order[(i + 1) % order.length]);
                return;
            }
        }
        room.setMaxDuelTime(0);
    }

    private void cyclePostMatchTime(Room room, boolean leftClick) {
        int next = room.getPostMatchTeleportDelay() + (leftClick ? 2 : -2);
        room.setPostMatchTeleportDelay(next);
    }
}
