package dev.zm.pvprooms.listeners;

import dev.zm.pvprooms.ZMPvPRooms;
import dev.zm.pvprooms.managers.EditorManager;
import dev.zm.pvprooms.models.Room;
import dev.zm.pvprooms.models.enums.RoomType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Optional;
import java.util.List;

public class EditorListener implements Listener {

    private final ZMPvPRooms plugin;

    public EditorListener(ZMPvPRooms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTopInventory().getHolder() instanceof EditorManager.RoomsListHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            if (event.getCurrentItem() == null) {
                return;
            }
            if (event.getCurrentItem().getType() == Material.RED_WOOL) {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }
            if (event.getCurrentItem().getType() != Material.MAP || event.getCurrentItem().getItemMeta() == null) {
                return;
            }

            String roomName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
            List<String> lore = event.getCurrentItem().getItemMeta().getLore();
            if (lore != null) {
                for (String line : lore) {
                    String clean = ChatColor.stripColor(line);
                    if (clean == null) {
                        continue;
                    }
                    if (clean.startsWith("ID:") && clean.length() > 3) {
                        roomName = clean.substring(3).trim();
                        break;
                    }
                }
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            plugin.getEditorManager().openRoomMenu(player, roomName);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof EditorManager.RoomMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }

            EditorManager.RoomMenuHolder holder = (EditorManager.RoomMenuHolder) event.getView().getTopInventory().getHolder();
            String roomName = holder.getRoomName();
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
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof EditorManager.RoomEditorHolder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        EditorManager.RoomEditorHolder holder = (EditorManager.RoomEditorHolder) event.getView().getTopInventory().getHolder();
        String roomName = holder.getRoomName();
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
            case 10:
                room.setType(room.getType() == RoomType.NORMAL ? RoomType.CLAN : RoomType.NORMAL);
                changed = true;
                break;
            case 11:
            case 12:
                room.setKeepInventory(!room.isKeepInventory());
                changed = true;
                break;
            case 14:
                cycleDoorMaterial(room);
                changed = true;
                break;
            case 19:
                room.setPlayersPerTeam(leftClick ? room.getPlayersPerTeam() + 1 : room.getPlayersPerTeam() - 1);
                if (room.getMinPlayersToStart() > room.getCapacity()) {
                    room.setMinPlayersToStart(room.getCapacity());
                }
                changed = true;
                break;
            case 21:
                room.setMinPlayersToStart(leftClick ? room.getMinPlayersToStart() + 2 : room.getMinPlayersToStart() - 2);
                if (room.getMinPlayersToStart() > room.getCapacity()) {
                    room.setMinPlayersToStart(room.getCapacity());
                }
                changed = true;
                break;
            case 23:
                room.setDoorOpenDelay(leftClick ? room.getDoorOpenDelay() + 5 : room.getDoorOpenDelay() - 5);
                changed = true;
                break;
            case 16:
                if (event.isShiftClick()) {
                    room.setChatEnabled(!room.isChatEnabled());
                } else if (leftClick) {
                    room.setTitlesEnabled(!room.isTitlesEnabled());
                } else {
                    room.setActionBarEnabled(!room.isActionBarEnabled());
                }
                changed = true;
                break;
            case 25:
                cycleBetMode(room);
                changed = true;
                break;
            case 30:
                cycleMaxTime(room);
                changed = true;
                break;
            case 32:
                cyclePostMatchTime(room, leftClick);
                changed = true;
                break;
            case 49:
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                plugin.getEditorManager().openRoomMenu(player, room.getName());
                return;
            default:
                return;
        }

        if (changed) {
            plugin.getRoomManager().saveRooms();
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        plugin.getEditorManager().openEditor(player, room.getName());
    }

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
