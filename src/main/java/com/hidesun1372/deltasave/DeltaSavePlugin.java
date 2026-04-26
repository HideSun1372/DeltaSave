package com.hidesun1372.deltasave;

import com.hidesun1372.deltasave.commands.*;
import com.hidesun1372.deltasave.gui.CheckGui;
import com.hidesun1372.deltasave.gui.SaveMenuGui;
import com.hidesun1372.deltasave.gui.StorageGui;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import java.util.Objects;


public class DeltaSavePlugin extends JavaPlugin {

    private static DeltaSavePlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Bukkit.getConsoleSender().sendMessage(Component.text("============================").color(NamedTextColor.GRAY));
        Bukkit.getConsoleSender().sendMessage(Component.text("* ").color(NamedTextColor.DARK_PURPLE).append(Component.text("The POWER is being restored...").color(NamedTextColor.LIGHT_PURPLE)));
        SaveManager saveManager = new SaveManager(this);
        saveManager.startScanTask();
        Bukkit.getConsoleSender().sendMessage(Component.text("  Save Manager:   ").color(NamedTextColor.YELLOW).append(Component.text("Loaded!").color(NamedTextColor.WHITE)));

        StorageManager storageManager = new StorageManager(this);
        SaveMenuGui saveMenuGui = new SaveMenuGui(this, saveManager);
        CheckGui checkGui       = new CheckGui(saveManager, saveMenuGui);
        StorageGui storageGui   = new StorageGui(this, storageManager);
        saveMenuGui.setCheckGui(checkGui);
        saveMenuGui.setStorageGui(storageGui);
        storageGui.setSaveMenuGui(saveMenuGui);
        saveManager.setSaveMenuGui(saveMenuGui);
        getServer().getPluginManager().registerEvents(saveManager, this);
        getServer().getPluginManager().registerEvents(saveMenuGui, this);
        getServer().getPluginManager().registerEvents(checkGui, this);
        getServer().getPluginManager().registerEvents(storageGui, this);
        Bukkit.getConsoleSender().sendMessage(Component.text("  Listeners:      ").color(NamedTextColor.YELLOW).append(Component.text("Loaded!").color(NamedTextColor.WHITE)));

        Objects.requireNonNull(getCommand("save")).setExecutor(new SaveCommand(saveManager));
        Objects.requireNonNull(getCommand("load")).setExecutor(new LoadCommand(saveManager));
        Objects.requireNonNull(getCommand("deletesave")).setExecutor(new DeleteSaveCommand(saveManager));
        Objects.requireNonNull(getCommand("saveinfo")).setExecutor(new SaveInfoCommand(saveManager));
        Objects.requireNonNull(getCommand("givesaveblock")).setExecutor(new GiveSaveBlockCommand(this));
        Objects.requireNonNull(getCommand("setdefaultspawn")).setExecutor(new SetDefaultSpawnCommand(saveManager));
        Objects.requireNonNull(getCommand("togglebeacongui")).setExecutor(new ToggleBeaconGuiCommand(saveManager));
        Objects.requireNonNull(getCommand("storage")).setExecutor(new StorageCommand(storageGui));
        Objects.requireNonNull(getCommand("scannow")).setExecutor(new ScanNowCommand(saveManager));
        Bukkit.getConsoleSender().sendMessage(Component.text("  Commands:       ").color(NamedTextColor.YELLOW).append(Component.text("Loaded!").color(NamedTextColor.WHITE)));

        Bukkit.getConsoleSender().sendMessage(Component.text("* ").color(NamedTextColor.DARK_PURPLE).append(Component.text("The PLUGIN has successfully initialized.").color(NamedTextColor.LIGHT_PURPLE)));
        Bukkit.getConsoleSender().sendMessage(Component.text("* ").color(NamedTextColor.DARK_PURPLE)
                .append(Component.text("DeltaSave ").color(NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("v1.2 ").color(NamedTextColor.GOLD)
                                .append(Component.text("- Ready!").color(NamedTextColor.LIGHT_PURPLE)))));

        Bukkit.getConsoleSender().sendMessage(Component.text("============================").color(NamedTextColor.GRAY));

    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(Component.text("============================").color(NamedTextColor.GRAY));
        Bukkit.getConsoleSender().sendMessage(Component.text("* ").color(NamedTextColor.DARK_PURPLE).append(Component.text("Saving... ").color(NamedTextColor.LIGHT_PURPLE)));
        Bukkit.getConsoleSender().sendMessage(Component.text("* ").color(NamedTextColor.DARK_PURPLE).append(Component.text("Your progress has been kept. ").color(NamedTextColor.LIGHT_PURPLE)));
        Bukkit.getConsoleSender().sendMessage(Component.text("* ").color(NamedTextColor.DARK_PURPLE).append(Component.text("See you again. ").color(NamedTextColor.LIGHT_PURPLE)));
        Bukkit.getConsoleSender().sendMessage(Component.text("* ").color(NamedTextColor.DARK_PURPLE)
                .append(Component.text("DeltaSave ").color(NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("v1.2 ").color(NamedTextColor.GOLD)
                                .append(Component.text("- Disabled. ").color(NamedTextColor.LIGHT_PURPLE)))));
        Bukkit.getConsoleSender().sendMessage(Component.text("============================").color(NamedTextColor.GRAY));
    }

    public static DeltaSavePlugin getInstance() { return instance; }
}