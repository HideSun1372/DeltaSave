package com.hidesun1372.deltasave;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SaveManager implements Listener {

    private final DeltaSavePlugin plugin;
    private final Set<String> saveBlockLocations = new HashSet<>();
    private final Map<UUID, Boolean> deleteConfirm = new HashMap<>();

    private static final String PREFIX = "§6[SAVE] §r";
    private static final Location DEFAULT_SPAWN = new Location(Bukkit.getWorld("world"), -91, -60, -40);

    public SaveManager(DeltaSavePlugin plugin) {
        this.plugin = plugin;
        loadSaveBlocks();
    }

    // --- Save Block Registration ---
    @EventHandler
    public void onBeaconPlace(BlockPlaceEvent e) {
        if (e.getBlock().getType() != Material.BEACON) return;
        if (!e.getPlayer().isOp()) return;
        ItemStack held = e.getPlayer().getInventory().getItemInMainHand();
        if (held.getItemMeta() == null) return;
        String name = held.getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(held.getItemMeta().displayName())
                : "";
        if (!name.equals("Save Block")) return;
        String locKey = locationToKey(e.getBlock().getLocation());
        saveBlockLocations.add(locKey);
        saveSaveBlocks();
        e.getPlayer().sendMessage(PREFIX + "§aSave Block registered at " + locKey + "!");
    }

    @EventHandler
    public void onBeaconBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.BEACON) return;
        String locKey = locationToKey(e.getBlock().getLocation());
        if (!saveBlockLocations.contains(locKey)) return;
        saveBlockLocations.remove(locKey);
        saveSaveBlocks();
        if (e.getPlayer().isOp())
            e.getPlayer().sendMessage(PREFIX + "§cSave Block unregistered.");
    }

    @EventHandler
    public void onBeaconClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.BEACON) return;
        if (e.getPlayer().isSneaking()) return;
        String locKey = locationToKey(e.getClickedBlock().getLocation());
        if (!saveBlockLocations.contains(locKey)) return;
        saveGame(e.getPlayer());
    }

    // --- Walking on Beacon ---
    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (!e.getTo().getBlock().getType().equals(Material.BEACON) &&
                !e.getFrom().getBlock().getType().equals(Material.BEACON)) return;
        Player p = e.getPlayer();
        if (p.hasMetadata("savemsg_cooldown")) return;
        String locKey = locationToKey(e.getTo().getBlock().getLocation());
        if (!saveBlockLocations.contains(locKey)) return;

        List<String> messages = Arrays.asList(
                "§d* The power of saving shines within you.",
                "§d* A power shines within... it's you!",
                "§d* Seeing such a friendly save point fills you with power.",
                "§d* You feel your sins crawling on your back...",
                "§d* But nobody came. (Just kidding, you're here!)"
        );
        p.sendMessage(messages.get(new Random().nextInt(messages.size())));
        p.setMetadata("savemsg_cooldown", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                p.removeMetadata("savemsg_cooldown", plugin), 140L);
    }

    // --- Join / Respawn ---
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (hasSave(p)) {
                loadGame(p);
                p.sendMessage(PREFIX + "§eWelcome back!");
            } else {
                p.sendMessage(PREFIX + "§7No checkpoint found. Find a Save Block to create your first save!");
                p.teleport(DEFAULT_SPAWN);
            }
        }, 5L);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (hasSave(p)) {
                loadGame(p);
                p.sendMessage(PREFIX + "§eRespawned at your last checkpoint!");
            } else {
                p.sendMessage(PREFIX + "§7No checkpoint set.");
                p.teleport(DEFAULT_SPAWN);
            }
        }, 5L);
    }

    // --- Core Save / Load ---
    public void saveGame(Player p) {
        // Heal and fill food
        var maxHealthAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        if (maxHealthAttr != null) {
            var attr = p.getAttribute(maxHealthAttr);
            if (attr != null) p.setHealth(attr.getValue());
        }
        p.setFoodLevel(10);
        p.setSaturation(10);
        p.sendMessage(PREFIX + "§d* Your HP was maxed out.");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2f);

        // Save data
        File file = getSaveFile(p);
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        config.set("exists", true);
        config.set("name", p.getName());
        config.set("location.world", p.getWorld().getName());
        config.set("location.x", p.getLocation().getX());
        config.set("location.y", p.getLocation().getY());
        config.set("location.z", p.getLocation().getZ());
        config.set("location.yaw", p.getLocation().getYaw());
        config.set("location.pitch", p.getLocation().getPitch());
        config.set("health", p.getHealth());
        config.set("maxhealth", maxHealthAttr != null && p.getAttribute(maxHealthAttr) != null
                ? p.getAttribute(maxHealthAttr).getValue() : 20.0);
        config.set("food", p.getFoodLevel());
        config.set("saturation", p.getSaturation());
        config.set("gamemode", p.getGameMode().name());

        // Inventory
        for (int i = 0; i < 36; i++)
            config.set("inventory." + i, p.getInventory().getItem(i));
        config.set("armor.helmet", p.getInventory().getHelmet());
        config.set("armor.chestplate", p.getInventory().getChestplate());
        config.set("armor.leggings", p.getInventory().getLeggings());
        config.set("armor.boots", p.getInventory().getBoots());

        // Potion effects
        config.set("effects", null);
        int i = 0;
        for (PotionEffect effect : p.getActivePotionEffects()) {
            config.set("effects." + i + ".type", effect.getType().getKey().getKey());
            config.set("effects." + i + ".duration", effect.getDuration());
            config.set("effects." + i + ".amplifier", effect.getAmplifier());
            i++;
        }

        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save data for " + p.getName());
        }

        p.sendMessage("");
        p.sendMessage(PREFIX + "§a§l✓ Game saved!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
    }

    public void loadGame(Player p) {
        File file = getSaveFile(p);
        if (!file.exists()) {
            p.sendMessage(PREFIX + "§cNo save file found!");
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.getBoolean("exists")) {
            p.sendMessage(PREFIX + "§cNo save file found!");
            return;
        }

        p.sendMessage(PREFIX + "§eLoading your save...");

        // Teleport
        World world = Bukkit.getWorld(config.getString("location.world", "world"));
        if (world == null) world = Bukkit.getWorlds().get(0);
        Location loc = new Location(world,
                config.getDouble("location.x"),
                config.getDouble("location.y"),
                config.getDouble("location.z"),
                (float) config.getDouble("location.yaw"),
                (float) config.getDouble("location.pitch"));
        p.teleport(loc);

        // Stats
        var maxHealthAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        if (maxHealthAttr != null) {
            var attr = p.getAttribute(maxHealthAttr);
            if (attr != null) attr.setBaseValue(config.getDouble("maxhealth", 20.0));
        }
        p.setHealth(config.getDouble("health", 20.0));
        p.setFoodLevel(config.getInt("food", 20));
        p.setSaturation((float) config.getDouble("saturation", 5.0));
        p.setGameMode(GameMode.valueOf(config.getString("gamemode", "SURVIVAL")));

        // Inventory
        p.getInventory().clear();
        for (int i = 0; i < 36; i++) {
            ItemStack item = config.getItemStack("inventory." + i);
            if (item != null) p.getInventory().setItem(i, item);
        }
        p.getInventory().setHelmet(config.getItemStack("armor.helmet"));
        p.getInventory().setChestplate(config.getItemStack("armor.chestplate"));
        p.getInventory().setLeggings(config.getItemStack("armor.leggings"));
        p.getInventory().setBoots(config.getItemStack("armor.boots"));

        // Potion effects
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        if (config.isConfigurationSection("effects")) {
            for (String key : config.getConfigurationSection("effects").getKeys(false)) {
                String typeName = config.getString("effects." + key + ".type");
                int duration = config.getInt("effects." + key + ".duration");
                int amplifier = config.getInt("effects." + key + ".amplifier");
                var effectType = org.bukkit.potion.PotionEffectType.getByName(typeName);
                if (effectType != null)
                    p.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
            }
        }

        p.sendMessage(PREFIX + "§a§l✓ Save loaded!");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    public void deleteSave(Player p) {
        File file = getSaveFile(p);
        if (file.exists()) {
            file.delete();
            p.sendMessage(PREFIX + "§cYour save file has been deleted.");
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
        } else {
            p.sendMessage(PREFIX + "§cYou don't have a save file!");
        }
    }

    public boolean hasSave(Player p) {
        File file = getSaveFile(p);
        if (!file.exists()) return false;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getBoolean("exists", false);
    }

    public void sendSaveInfo(Player sender, Player target) {
        File file = getSaveFile(target);
        if (!file.exists()) {
            sender.sendMessage(PREFIX + "§c" + target.getName() + " doesn't have a save file yet!");
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.getBoolean("exists")) {
            sender.sendMessage(PREFIX + "§c" + target.getName() + " doesn't have a save file yet!");
            return;
        }
        sender.sendMessage(PREFIX + "§6Save Info for " + target.getName() + ":");
        sender.sendMessage("§7  Location: §f" + config.getString("location.world")
                + " " + (int) config.getDouble("location.x")
                + " " + (int) config.getDouble("location.y")
                + " " + (int) config.getDouble("location.z"));
        sender.sendMessage("§7  Health: §f" + config.getDouble("health") + "/" + config.getDouble("maxhealth"));
        sender.sendMessage("§7  Gamemode: §f" + config.getString("gamemode"));
    }

    public Map<UUID, Boolean> getDeleteConfirm() { return deleteConfirm; }

    // --- Helpers ---
    private File getSaveFile(Player p) {
        File dir = new File(plugin.getDataFolder(), "saves");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, p.getUniqueId() + ".yml");
    }

    private String locationToKey(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    private void saveSaveBlocks() {
        File file = new File(plugin.getDataFolder(), "saveblocks.yml");
        FileConfiguration config = new YamlConfiguration();
        config.set("locations", new ArrayList<>(saveBlockLocations));
        try { config.save(file); } catch (IOException e) { plugin.getLogger().severe("Failed to save save blocks!"); }
    }

    private void loadSaveBlocks() {
        File file = new File(plugin.getDataFolder(), "saveblocks.yml");
        if (!file.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> locs = config.getStringList("locations");
        saveBlockLocations.addAll(locs);
    }
}
