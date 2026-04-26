// ---- GiveSaveBlockCommand.java ----
package com.hidesun1372.deltasave.commands;

import com.hidesun1372.deltasave.DeltaSavePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class GiveSaveBlockCommand implements CommandExecutor {

    private final DeltaSavePlugin plugin;

    public GiveSaveBlockCommand(DeltaSavePlugin plugin) {
        this.plugin = plugin;
    }

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

        String joined = String.join(" ", args);
        int openQuote = joined.indexOf('"');
        int closeQuote = joined.indexOf('"', openQuote + 1);
        if (openQuote != 0 || closeQuote == -1) {
            p.sendMessage("§cUsage: /givesaveblock \"<location name>\" <chapter>");
            p.sendMessage("§7Example: /givesaveblock \"??? - The Circus\" 1");
            return true;
        }
        String locationName = joined.substring(1, closeQuote).trim();
        if (locationName.isEmpty()) {
            p.sendMessage("§cLocation name cannot be empty!");
            return true;
        }

        String remaining = joined.substring(closeQuote + 1).trim();
        int chapter;
        try {
            chapter = Integer.parseInt(remaining);
            if (chapter < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            p.sendMessage("§cUsage: /givesaveblock \"<location name>\" <chapter>");
            p.sendMessage("§7Example: /givesaveblock \"??? - The Circus\" 1");
            return true;
        }

        NamespacedKey nameKey    = new NamespacedKey(plugin, "save_block_location");
        NamespacedKey chapterKey = new NamespacedKey(plugin, "save_block_chapter");
        ItemStack saveBlock = new ItemStack(Material.BEACON);
        ItemMeta meta = saveBlock.getItemMeta();
        meta.displayName(Component.text("Save Block").color(NamedTextColor.DARK_PURPLE));
        meta.lore(List.of(
                Component.text(locationName).color(NamedTextColor.GRAY),
                Component.text("Chapter " + chapter).color(NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(nameKey,    PersistentDataType.STRING,  locationName);
        meta.getPersistentDataContainer().set(chapterKey, PersistentDataType.INTEGER, chapter);
        saveBlock.setItemMeta(meta);
        p.getInventory().addItem(saveBlock);

        p.sendMessage("§6[SAVE] §aGave a Save Block for \"§f" + locationName + "§a\" (ch. " + chapter + ")!");
        return true;
    }
}
