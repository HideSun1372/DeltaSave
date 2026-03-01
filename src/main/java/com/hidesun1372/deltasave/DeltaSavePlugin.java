package com.hidesun1372.deltasave;

import com.hidesun1372.deltasave.commands.*;
import org.bukkit.plugin.java.JavaPlugin;

public class DeltaSavePlugin extends JavaPlugin {

    private static DeltaSavePlugin instance;
    private SaveManager saveManager;

    @Override
    public void onEnable() {
        instance = this;
        saveManager = new SaveManager(this);

        getServer().getPluginManager().registerEvents(saveManager, this);

        getCommand("save").setExecutor(new SaveCommand(saveManager));
        getCommand("load").setExecutor(new LoadCommand(saveManager));
        getCommand("deletesave").setExecutor(new DeleteSaveCommand(saveManager));
        getCommand("saveinfo").setExecutor(new SaveInfoCommand(saveManager));
        getCommand("givesaveblock").setExecutor(new GiveSaveBlockCommand());

        getLogger().info("DeltaSave enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("DeltaSave disabled!");
    }

    public static DeltaSavePlugin getInstance() { return instance; }
    public SaveManager getSaveManager() { return saveManager; }
}