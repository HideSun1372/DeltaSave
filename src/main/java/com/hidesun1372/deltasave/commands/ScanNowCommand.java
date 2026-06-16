// ---- ScanNowCommand.java ----
package com.hidesun1372.deltasave.commands;

import com.hidesun1372.deltasave.SaveManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

public class ScanNowCommand implements CommandExecutor {
    private final SaveManager saveManager;

    public ScanNowCommand(SaveManager saveManager) {
        this.saveManager = saveManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("deltasave.admin")) {
            sender.sendMessage(SaveManager.PREFIX + "§cYou don't have permission to do that.");
            return true;
        }
        if (saveManager.scanSaveBlocks() == 0) {
            sender.sendMessage(SaveManager.PREFIX + "§aScan complete. All registered save blocks are present.");
        }
        return true;
    }
}
