// ---- SaveInfoCommand.java ----
package com.hidesun1372.deltasave.commands;

import com.hidesun1372.deltasave.SaveManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class SaveInfoCommand implements CommandExecutor {
    private final SaveManager saveManager;
    public SaveInfoCommand(SaveManager saveManager) { this.saveManager = saveManager; }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }
        Player target = args.length > 0 ? Bukkit.getPlayer(args[0]) : p;
        if (target == null) { p.sendMessage("§cPlayer not found!"); return true; }
        saveManager.sendSaveInfo(p, target);
        return true;
    }
}
