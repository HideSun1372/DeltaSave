// ---- SaveCommand.java ----
package com.hidesun1372.deltasave.commands;

import com.hidesun1372.deltasave.SaveManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SaveCommand implements CommandExecutor {
    private final SaveManager saveManager;
    public SaveCommand(SaveManager saveManager) { this.saveManager = saveManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }
        if (!p.isOp()) {
            p.sendMessage("§cYou can't use that command.");
            return true;
        }
        if (args.length > 0 && p.hasPermission("deltasave.admin")) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { p.sendMessage("§cPlayer not found!"); return true; }
            saveManager.saveGame(target);
            p.sendMessage("§6[SAVE] §aSaved game for " + target.getName() + "!");
            target.sendMessage("§6[SAVE] §aYour game was saved by " + p.getName() + "!");
        } else {
            saveManager.saveGame(p);
        }
        return true;
    }
}
