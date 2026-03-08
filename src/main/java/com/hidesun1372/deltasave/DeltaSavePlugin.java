package com.hidesun1372.deltasave;

import com.hidesun1372.deltasave.commands.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;


public class DeltaSavePlugin extends JavaPlugin {

    private static DeltaSavePlugin instance;
    private SaveManager saveManager;

    @Override
    public void onEnable() {
        instance = this;
        Bukkit.getConsoleSender().sendMessage(Component.text("============================").color(NamedTextColor.GRAY));
        Bukkit.getConsoleSender().sendMessage(Component.text("* ").color(NamedTextColor.DARK_PURPLE).append(Component.text("The POWER is being restored...").color(NamedTextColor.LIGHT_PURPLE)));
        saveManager = new SaveManager(this);
        Bukkit.getConsoleSender().sendMessage(Component.text("  Save Manager:   ").color(NamedTextColor.YELLOW).append(Component.text("Loaded!").color(NamedTextColor.WHITE)));

        getServer().getPluginManager().registerEvents(saveManager, this);
        Bukkit.getConsoleSender().sendMessage(Component.text("  Listeners:      ").color(NamedTextColor.YELLOW).append(Component.text("Loaded!").color(NamedTextColor.WHITE)));

        getCommand("save").setExecutor(new SaveCommand(saveManager));
        getCommand("load").setExecutor(new LoadCommand(saveManager));
        getCommand("deletesave").setExecutor(new DeleteSaveCommand(saveManager));
        getCommand("saveinfo").setExecutor(new SaveInfoCommand(saveManager));
        getCommand("givesaveblock").setExecutor(new GiveSaveBlockCommand());
        getCommand("setdefaultspawn").setExecutor(new SetDefaultSpawnCommand(saveManager));
        getCommand("togglebeacongui").setExecutor(new ToggleBeaconGuiCommand(saveManager));
        Bukkit.getConsoleSender().sendMessage(Component.text("  Commands:       ").color(NamedTextColor.YELLOW).append(Component.text("Loaded!").color(NamedTextColor.WHITE)));

        Bukkit.getConsoleSender().sendMessage(Component.text("* ").color(NamedTextColor.DARK_PURPLE).append(Component.text("The PLUGIN has successfully initialized.").color(NamedTextColor.LIGHT_PURPLE)));
        Bukkit.getConsoleSender().sendMessage(Component.text("* ").color(NamedTextColor.DARK_PURPLE)
                .append(Component.text("DeltaSave ").color(NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("v1.1.1 ").color(NamedTextColor.GOLD)
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
                        .append(Component.text("v1.1.1 ").color(NamedTextColor.GOLD)
                                .append(Component.text("- Disabled. ").color(NamedTextColor.LIGHT_PURPLE)))));
        Bukkit.getConsoleSender().sendMessage(Component.text("============================").color(NamedTextColor.GRAY));
    }

    public static DeltaSavePlugin getInstance() { return instance; }
    public SaveManager getSaveManager() { return saveManager; }
}