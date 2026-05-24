package com.hidesun1372.deltasave.gui;

import com.hidesun1372.deltasave.DeltaSavePlugin;
import com.hidesun1372.deltasave.SaveManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SaveMenuGui implements Listener {

    private final DeltaSavePlugin plugin;
    private final SaveManager saveManager;
    private final Set<UUID> pendingDelete = new HashSet<>();
    private final Map<UUID, BukkitTask> expiryTasks = new HashMap<>();
    private CheckGui checkGui;
    private StorageGui storageGui;

    public SaveMenuGui(DeltaSavePlugin plugin, SaveManager saveManager) {
        this.plugin = plugin;
        this.saveManager = saveManager;
    }

    public void setCheckGui(CheckGui gui)     { this.checkGui   = gui; }
    public void setStorageGui(StorageGui gui) { this.storageGui = gui; }

    public void open(Player player, String locationName, int chapter) {
        cancelExpiry(player.getUniqueId());

        player.sendActionBar(
                Component.text("* ").color(NamedTextColor.DARK_PURPLE)
                        .append(Component.text(locationName).color(NamedTextColor.LIGHT_PURPLE)));

        String titleName = locationName.length() > 30
                ? locationName.substring(0, 29) + "…"
                : locationName;

        SaveMenuHolder holder = new SaveMenuHolder(locationName, chapter);
        Inventory inv = Bukkit.createInventory(
                holder,
                9,
                Component.text("* ").color(NamedTextColor.DARK_PURPLE)
                        .append(Component.text(titleName).color(NamedTextColor.LIGHT_PURPLE)));

        inv.setItem(0, button(Material.NETHER_STAR,
                Component.text("SAVE").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                Component.text("Save your game here.").color(NamedTextColor.GRAY)));

        inv.setItem(1, button(Material.WRITTEN_BOOK,
                Component.text("CHECK").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                Component.text("View your save data.").color(NamedTextColor.GRAY)));

        inv.setItem(2, button(Material.ENDER_CHEST,
                Component.text("STORAGE").color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD),
                Component.text("Access your storage.").color(NamedTextColor.GRAY)));

        long timeoutSecs = saveManager.getDeleteConfirmTimeoutGui() / 20;
        inv.setItem(3, deleteButton(false, timeoutSecs));

        ItemStack filler = filler();
        for (int i = 4; i <= 7; i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(8, button(Material.BARRIER,
                Component.text("CLOSE").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.text("Close this menu.").color(NamedTextColor.GRAY)));

        holder.setInventory(inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SaveMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getInventory().equals(event.getClickedInventory())) return;

        switch (event.getSlot()) {
            case 0 -> {
                player.closeInventory();
                saveManager.saveGame(player, holder.locationName);
            }
            case 1 -> {
                if (checkGui != null) checkGui.open(player, holder.locationName, holder.chapter);
            }
            case 2 -> {
                if (storageGui != null) storageGui.open(player, holder.locationName, holder.chapter, 1);
            }
            case 3 -> {
                UUID uuid = player.getUniqueId();
                if (!pendingDelete.contains(uuid)) {
                    long timeoutSecs = saveManager.getDeleteConfirmTimeoutGui() / 20;
                    pendingDelete.add(uuid);
                    event.getInventory().setItem(3, deleteButton(true, timeoutSecs));
                    BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (pendingDelete.remove(uuid)) {
                            expiryTasks.remove(uuid);
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof SaveMenuHolder) {
                                p.getOpenInventory().getTopInventory().setItem(3, deleteButton(false, 0));
                            }
                        }
                    }, saveManager.getDeleteConfirmTimeoutGui());
                    expiryTasks.put(uuid, task);
                } else {
                    cancelExpiry(uuid);
                    player.closeInventory();
                    saveManager.deleteSave(player);
                }
            }
            case 8 -> player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SaveMenuHolder)) return;
        cancelExpiry(event.getPlayer().getUniqueId());
    }

    private void cancelExpiry(UUID uuid) {
        pendingDelete.remove(uuid);
        BukkitTask task = expiryTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private static ItemStack deleteButton(boolean warning, long timeoutSecs) {
        if (warning) {
            return button(Material.TNT,
                    Component.text("ARE YOU SURE?").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text("Click again within " + timeoutSecs + " seconds to delete.").color(NamedTextColor.GRAY),
                    Component.text("This cannot be undone!").color(NamedTextColor.DARK_RED));
        }
        return button(Material.TNT,
                Component.text("DELETE SAVE").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.text("Permanently delete your save file.").color(NamedTextColor.GRAY));
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore)
                    .map(c -> c.decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    public static class SaveMenuHolder implements InventoryHolder {
        public final String locationName;
        public final int chapter;
        private Inventory inventory;
        public SaveMenuHolder(String locationName, int chapter) {
            this.locationName = locationName;
            this.chapter      = chapter;
        }
        void setInventory(Inventory inv) { this.inventory = inv; }
        @Override public @NotNull Inventory getInventory() { return java.util.Objects.requireNonNull(inventory); }
    }
}
