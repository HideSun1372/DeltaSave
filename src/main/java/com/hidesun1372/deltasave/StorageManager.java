package com.hidesun1372.deltasave;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;

public class StorageManager {

    private final DeltaSavePlugin plugin;

    public StorageManager(DeltaSavePlugin plugin) {
        this.plugin = plugin;
    }

    private File getStorageFile(Player player) {
        File dir = new File(plugin.getDataFolder(), "storage");
        if (!dir.mkdirs() && !dir.exists()) {
            plugin.getLogger().severe("Could not create storage directory!");
        }
        return new File(dir, player.getUniqueId() + ".yml");
    }

    public ItemStack[] loadStorage(Player player, int capacity) {
        ItemStack[] items = new ItemStack[capacity];
        File f = getStorageFile(player);
        if (!f.exists()) return items;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        for (int i = 0; i < capacity; i++) {
            items[i] = cfg.getItemStack("slot." + i);
        }
        return items;
    }

    public void saveStorage(Player player, ItemStack[] items) {
        File f = getStorageFile(player);
        FileConfiguration cfg = new YamlConfiguration();
        for (int i = 0; i < items.length; i++) {
            cfg.set("slot." + i, items[i]);
        }
        try {
            cfg.save(f);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save storage for " + player.getName());
        }
    }
}
