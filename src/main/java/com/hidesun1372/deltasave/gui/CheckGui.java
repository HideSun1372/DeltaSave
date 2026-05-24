package com.hidesun1372.deltasave.gui;

import com.hidesun1372.deltasave.SaveManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class CheckGui implements Listener {

    private final SaveManager saveManager;
    private final SaveMenuGui saveMenuGui;

    public CheckGui(SaveManager saveManager, SaveMenuGui saveMenuGui) {
        this.saveManager = saveManager;
        this.saveMenuGui = saveMenuGui;
    }

    public void open(Player player, String locationName, int chapter) {
        FileConfiguration cfg = saveManager.getSaveConfig(player);
        if (cfg == null) {
            player.sendMessage(SaveManager.PREFIX + "§cNo save data found!");
            return;
        }

        CheckHolder holder = new CheckHolder(locationName, chapter);
        Inventory inv = Bukkit.createInventory(
                holder,
                9,
                Component.text("* ").color(NamedTextColor.DARK_PURPLE)
                        .append(Component.text("Check").color(NamedTextColor.LIGHT_PURPLE)));

        // -- HP --
        double health    = cfg.getDouble("health", 20.0);
        double maxHealth = cfg.getDouble("maxhealth", 20.0);
        inv.setItem(0, stat(Material.RED_DYE,
                Component.text(String.format("%.1f", health) + " / " + String.format("%.1f", maxHealth))
                        .color(NamedTextColor.RED),
                Component.text("Health").color(NamedTextColor.GRAY)));

        // -- Location --
        String world = cfg.getString("location.world", "?");
        String coords = String.format("%.1f, %.1f, %.1f",
                cfg.getDouble("location.x"),
                cfg.getDouble("location.y"),
                cfg.getDouble("location.z"));
        inv.setItem(1, stat(Material.COMPASS,
                Component.text(coords).color(NamedTextColor.WHITE),
                Component.text("Location").color(NamedTextColor.GRAY),
                Component.text(world).color(NamedTextColor.DARK_GRAY)));

        // -- Gamemode --
        String gamemode = cfg.getString("gamemode", "SURVIVAL");
        inv.setItem(2, stat(Material.CHAINMAIL_CHESTPLATE,
                Component.text(gamemode).color(NamedTextColor.WHITE),
                Component.text("Gamemode").color(NamedTextColor.GRAY)));

        // -- Playtime --
        long accumulated = cfg.getLong("playtime.accumulated", 0L);
        long start       = cfg.getLong("playtime.start", 0L);
        long totalMs     = accumulated + (start > 0 ? System.currentTimeMillis() - start : 0L);
        inv.setItem(3, stat(Material.CLOCK,
                Component.text(saveManager.formatPlaytime(totalMs)).color(NamedTextColor.WHITE),
                Component.text("Playtime").color(NamedTextColor.GRAY)));

        // -- Blocks placed --
        int savedPlaced   = cfg.getInt("save.blocks.placed.count", 0);
        int unsavedPlaced = saveManager.getUnsavedPlacedCount(player.getUniqueId());
        Component placedName = Component.text(String.valueOf(savedPlaced)).color(NamedTextColor.WHITE);
        if (unsavedPlaced > 0) {
            placedName = placedName.append(Component.text(" (+" + unsavedPlaced + " unsaved)").color(NamedTextColor.DARK_GRAY));
        }
        inv.setItem(4, stat(Material.GRASS_BLOCK, placedName,
                Component.text("Blocks Placed").color(NamedTextColor.GRAY)));

        // -- Blocks broken --
        int savedBroken   = cfg.getInt("save.blocks.broken.count", 0);
        int unsavedBroken = saveManager.getUnsavedBrokenCount(player.getUniqueId());
        Component brokenName = Component.text(String.valueOf(savedBroken)).color(NamedTextColor.WHITE);
        if (unsavedBroken > 0) {
            brokenName = brokenName.append(Component.text(" (+" + unsavedBroken + " unsaved)").color(NamedTextColor.DARK_GRAY));
        }
        inv.setItem(5, stat(Material.COBBLESTONE, brokenName,
                Component.text("Blocks Broken").color(NamedTextColor.GRAY)));

        // -- Filler --
        inv.setItem(6, filler());

        // -- Back --
        inv.setItem(7, button(Material.ARROW,
                Component.text("BACK").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                Component.text("Return to save menu.").color(NamedTextColor.GRAY)));

        // -- Close --
        inv.setItem(8, button(Material.BARRIER,
                Component.text("CLOSE").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.text("Close this menu.").color(NamedTextColor.GRAY)));

        holder.setInventory(inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CheckHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getInventory().equals(event.getClickedInventory())) return;

        switch (event.getSlot()) {
            case 7 -> saveMenuGui.open(player, holder.locationName, holder.chapter);
            case 8 -> player.closeInventory();
        }
    }

    private static ItemStack stat(Material material, Component value, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(value.decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore)
                    .map(c -> c.decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material material, Component name, Component... lore) {
        return stat(material, name, lore);
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    public static class CheckHolder implements InventoryHolder {
        public final String locationName;
        public final int chapter;
        private Inventory inventory;
        public CheckHolder(String locationName, int chapter) {
            this.locationName = locationName;
            this.chapter      = chapter;
        }
        void setInventory(Inventory inv) { this.inventory = inv; }
        @Override public @NotNull Inventory getInventory() { return java.util.Objects.requireNonNull(inventory); }
    }
}
