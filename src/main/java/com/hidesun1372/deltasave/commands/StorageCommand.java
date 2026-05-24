// ---- StorageCommand.java ----
package com.hidesun1372.deltasave.commands;

import com.hidesun1372.deltasave.SaveManager;
import com.hidesun1372.deltasave.gui.StorageGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class StorageCommand implements CommandExecutor {

    private final StorageGui storageGui;

    public StorageCommand(StorageGui storageGui) {
        this.storageGui = storageGui;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }
        if (!p.isOp()) {
            p.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        Player target = args.length > 0 ? Bukkit.getPlayer(args[0]) : p;
        if (target == null) {
            p.sendMessage(SaveManager.PREFIX + "§cPlayer not found!");
            return true;
        }

        storageGui.open(target, target.getName() + "'s Storage", 1, 1);
        if (!target.equals(p)) {
            p.sendMessage(SaveManager.PREFIX + "§aOpened storage for §f" + target.getName() + "§a.");
        }
        return true;
    }
}
