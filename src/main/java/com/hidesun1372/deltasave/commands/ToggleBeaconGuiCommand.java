// ---- ToggleBeaconGuiCommand.java ----
package com.hidesun1372.deltasave.commands;

import com.hidesun1372.deltasave.SaveManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class ToggleBeaconGuiCommand implements CommandExecutor {
    private final SaveManager saveManager;

    public ToggleBeaconGuiCommand(SaveManager saveManager) {
        this.saveManager = saveManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(SaveManager.PREFIX + "§cYou don't have permission to do that.");
            return true;
        }
        saveManager.toggleBeaconGui(player);
        return true;
    }
}