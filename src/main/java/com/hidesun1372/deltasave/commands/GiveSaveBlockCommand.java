// ---- GiveSaveBlockCommand.java ----
package com.hidesun1372.deltasave.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GiveSaveBlockCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }
        if (!p.isOp()) {
            p.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        Player target = args.length > 0 ? Bukkit.getPlayer(args[0]) : p;
        if (target == null) { p.sendMessage("§cPlayer not found!"); return true; }

        ItemStack saveBlock = new ItemStack(Material.BEACON);
        ItemMeta meta = saveBlock.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Save Block")
                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
        saveBlock.setItemMeta(meta);
        target.getInventory().addItem(saveBlock);

        p.sendMessage("§6[SAVE] §aGave a Save Block to " + target.getName() + "!");
        if (!target.equals(p)) target.sendMessage("§6[SAVE] §aYou received a Save Block!");
        return true;
    }
}
