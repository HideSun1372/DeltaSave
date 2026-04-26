// ---- DeleteSaveCommand.java ----
package com.hidesun1372.deltasave.commands;

import com.hidesun1372.deltasave.DeltaSavePlugin;
import com.hidesun1372.deltasave.SaveManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class DeleteSaveCommand implements CommandExecutor {
    private final SaveManager saveManager;
    public DeleteSaveCommand(SaveManager saveManager) { this.saveManager = saveManager; }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }
        if (!p.isOp()) {
            p.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        // Admin deleting another player's save file
        if (args.length > 0) {
            if (!p.hasPermission("deltasave.admin")) {
                p.sendMessage("§cYou don't have permission to delete other players' saves!");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { p.sendMessage("§cPlayer not found!"); return true; }
            saveManager.deleteSave(target);
            p.sendMessage("§6[SAVE] §cDeleted save file for " + target.getName() + "!");
            target.sendMessage("§6[SAVE] §cYour save file was deleted by " + p.getName() + "!");
            return true;
        }

        // Self deletion with confirmation
        if (!saveManager.getDeleteConfirm().getOrDefault(p.getUniqueId(), false)) {
            long timeoutSecs = saveManager.getDeleteConfirmTimeoutCmd() / 20;
            p.sendMessage(SaveManager.PREFIX + "§cAre you sure? Run §l/deletesave§r§c again within " + timeoutSecs + " seconds to confirm.");
            saveManager.getDeleteConfirm().put(p.getUniqueId(), true);
            DeltaSavePlugin.getInstance().getServer().getScheduler().runTaskLater(
                    DeltaSavePlugin.getInstance(),
                    () -> saveManager.getDeleteConfirm().remove(p.getUniqueId()),
                    saveManager.getDeleteConfirmTimeoutCmd()
            );
        } else {
            saveManager.getDeleteConfirm().remove(p.getUniqueId());
            saveManager.deleteSave(p);
        }
        return true;
    }
}
