package com.hidesun1372.deltasave.gui;

import com.hidesun1372.deltasave.DeltaSavePlugin;
import com.hidesun1372.deltasave.StorageManager;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StorageGui implements Listener {

    private static final int PREV_SLOT  = 18;
    private static final int NEXT_SLOT  = 24;
    private static final int BACK_SLOT  = 25;
    private static final int CLOSE_SLOT = 26;

    private final DeltaSavePlugin plugin;
    private final StorageManager storageManager;
    private SaveMenuGui saveMenuGui;

    public StorageGui(DeltaSavePlugin plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    public void setSaveMenuGui(SaveMenuGui gui) { this.saveMenuGui = gui; }

    // -------------------------------------------------------------------------
    // Open
    // -------------------------------------------------------------------------

    public void open(Player player, String locationName, int chapter, int page) {
        List<Integer> itemSlots = availableSlots(page, chapter);
        int offset = offsetForPage(page, chapter);
        int capacity = totalCapacity(chapter);
        ItemStack[] allItems = storageManager.loadStorage(player, capacity);

        Component title = Component.text("* Storage").color(NamedTextColor.DARK_PURPLE);
        if (chapter > 1) {
            title = title.append(
                    Component.text(" | " + page + "/" + chapter).color(NamedTextColor.GRAY));
        }

        StorageHolder holder = new StorageHolder(locationName, chapter, page);
        Inventory inv = Bukkit.createInventory(holder, 27, title);

        for (int i = 0; i < itemSlots.size(); i++) {
            int storageIdx = offset + i;
            if (storageIdx < capacity) {
                inv.setItem(itemSlots.get(i), allItems[storageIdx]);
            }
        }

        if (page > 1)          inv.setItem(PREV_SLOT,  navButton(false));
        if (page < chapter)    inv.setItem(NEXT_SLOT,  navButton(true));
        inv.setItem(BACK_SLOT,  backButton());
        inv.setItem(CLOSE_SLOT, closeButton());

        holder.setInventory(inv);
        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StorageHolder holder)) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getInventory())) return;

        int slot    = event.getSlot();
        int page    = holder.page;
        int chapter = holder.chapter;

        boolean isNext  = (page < chapter && slot == NEXT_SLOT);
        boolean isPrev  = (page > 1        && slot == PREV_SLOT);
        boolean isBack  = (slot == BACK_SLOT);
        boolean isClose = (slot == CLOSE_SLOT);

        if (isNext || isPrev || isBack || isClose) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            if (isNext || isPrev) {
                int newPage = isNext ? page + 1 : page - 1;
                Bukkit.getScheduler().runTask(plugin,
                        () -> open(player, holder.locationName, holder.chapter, newPage));
            } else if (isBack) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (saveMenuGui != null) saveMenuGui.open(player, holder.locationName, holder.chapter);
                });
            } else {
                player.closeInventory();
            }
        }
        // All other clicks (item slots) are allowed naturally
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof StorageHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        List<Integer> itemSlots = availableSlots(holder.page, holder.chapter);
        int offset   = offsetForPage(holder.page, holder.chapter);
        int capacity = totalCapacity(holder.chapter);

        ItemStack[] allItems = storageManager.loadStorage(player, capacity);
        for (int i = 0; i < itemSlots.size(); i++) {
            int storageIdx = offset + i;
            if (storageIdx < capacity) {
                allItems[storageIdx] = event.getInventory().getItem(itemSlots.get(i));
            }
        }
        storageManager.saveStorage(player, allItems);
    }

    // -------------------------------------------------------------------------
    // Slot helpers
    // -------------------------------------------------------------------------

    public static List<Integer> availableSlots(int page, int chapter) {
        Set<Integer> reserved = new HashSet<>();
        reserved.add(BACK_SLOT);
        reserved.add(CLOSE_SLOT);
        if (page > 1)       reserved.add(PREV_SLOT);
        if (page < chapter) reserved.add(NEXT_SLOT);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            if (!reserved.contains(i)) slots.add(i);
        }
        return slots;
    }

    public static int offsetForPage(int page, int chapter) {
        int offset = 0;
        for (int p = 1; p < page; p++) {
            offset += availableSlots(p, chapter).size();
        }
        return offset;
    }

    public static int totalCapacity(int chapter) {
        int total = 0;
        for (int p = 1; p <= chapter; p++) {
            total += availableSlots(p, chapter).size();
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private static ItemStack navButton(boolean next) {
        ItemStack item = new ItemStack(next ? Material.ARROW : Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        Component name = next
                ? Component.text("Next Page ▶").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                : Component.text("◀ Previous Page").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("BACK").color(NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Return to save menu.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("CLOSE").color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Close this menu.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Holder
    // -------------------------------------------------------------------------

    public static class StorageHolder implements InventoryHolder {
        public final String locationName;
        public final int chapter;
        public final int page;
        private Inventory inventory;

        public StorageHolder(String locationName, int chapter, int page) {
            this.locationName = locationName;
            this.chapter      = chapter;
            this.page         = page;
        }

        void setInventory(Inventory inv) { this.inventory = inv; }
        @Override public @NotNull Inventory getInventory() { return java.util.Objects.requireNonNull(inventory); }
    }
}
