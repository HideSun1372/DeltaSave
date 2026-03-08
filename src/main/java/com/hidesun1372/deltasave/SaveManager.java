package com.hidesun1372.deltasave;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SaveManager implements Listener {

    public static final String PREFIX = "§6[SAVE] §r";

    // Default spawn — edit to match your server
    private String defaultSpawnWorld;
    private double defaultSpawnX, defaultSpawnY, defaultSpawnZ;
    private float defaultSpawnYaw, defaultSpawnPitch;

    private final DeltaSavePlugin plugin;

    // -------------------------------------------------------------------------
    // In-memory state — no disk reads/writes except at save, load, join
    // -------------------------------------------------------------------------

    /** Registered save block locations (persisted to saveblocks.yml) */
    private final Set<String> saveBlockLocations = new HashSet<>();

    /**
     * Players who have an active checkpoint set.
     * Populated on join (if save exists) and on saveGame().
     * Cleared on deleteSave().
     * Used by block tracking events so they never touch disk.
     */
    private final Set<UUID> checkpointSet = new HashSet<>();

    /**
     * Per-player block tracking buffers accumulated since the last save.
     * Only written to disk when saveGame() is called.
     * Cleared (and rolled back in-world) when loadGame() is called.
     */
    private final Map<UUID, List<BlockEntry>> placedBuffer = new HashMap<>();
    private final Map<UUID, List<BlockEntry>> brokenBuffer = new HashMap<>();

    /** Pending /deletesave confirmations */
    private final Map<UUID, Boolean> deleteConfirm = new HashMap<>();

    private boolean blockBeaconGui;

    // -------------------------------------------------------------------------
    // BlockEntry record
    // -------------------------------------------------------------------------

    record BlockEntry(String world, int x, int y, int z, String type) {}

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public SaveManager(DeltaSavePlugin plugin) {
        this.plugin = plugin;
        loadSaveBlocks();
        loadDefaultSpawn();
    }

    // -------------------------------------------------------------------------
    // Save-block persistence
    // -------------------------------------------------------------------------

    private File getSaveBlocksFile() {
        return new File(plugin.getDataFolder(), "saveblocks.yml");
    }

    private void loadSaveBlocks() {
        File file = getSaveBlocksFile();
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        saveBlockLocations.addAll(cfg.getStringList("locations"));
    }

    private void loadDefaultSpawn() {
        blockBeaconGui = plugin.getConfig().getBoolean("blockbeacongui", true);
        if (!plugin.getConfig().contains("defaultspawn")) return;
        FileConfiguration cfg = plugin.getConfig();
        defaultSpawnWorld = cfg.getString("defaultspawn.world");
        defaultSpawnX     = cfg.getDouble("defaultspawn.x");
        defaultSpawnY     = cfg.getDouble("defaultspawn.y");
        defaultSpawnZ     = cfg.getDouble("defaultspawn.z");
        defaultSpawnYaw   = (float) cfg.getDouble("defaultspawn.yaw");
        defaultSpawnPitch = (float) cfg.getDouble("defaultspawn.pitch");
    }

    public void setDefaultSpawn(Player player) {
        Location loc = player.getLocation();
        defaultSpawnWorld = loc.getWorld().getName();
        defaultSpawnX     = loc.getX();
        defaultSpawnY     = loc.getY();
        defaultSpawnZ     = loc.getZ();
        defaultSpawnYaw   = loc.getYaw();
        defaultSpawnPitch = loc.getPitch();
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("defaultspawn.world",  defaultSpawnWorld);
        cfg.set("defaultspawn.x",      defaultSpawnX);
        cfg.set("defaultspawn.y",      defaultSpawnY);
        cfg.set("defaultspawn.z",      defaultSpawnZ);
        cfg.set("defaultspawn.yaw",    (double) defaultSpawnYaw);
        cfg.set("defaultspawn.pitch",  (double) defaultSpawnPitch);
        plugin.saveConfig();
        player.sendMessage(PREFIX + "§aDefault spawn set!");
    }

    public void toggleBeaconGui(Player player) {
        blockBeaconGui = !blockBeaconGui;
        plugin.getConfig().set("blockbeacongui", blockBeaconGui);
        plugin.saveConfig();
        player.sendMessage(PREFIX + (blockBeaconGui ? "§aBeacon GUI blocked." : "§cBeacon GUI unblocked."));
    }

    private void saveSaveBlocks() {
        File file = getSaveBlocksFile();
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("locations", new ArrayList<>(saveBlockLocations));
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save save blocks!");
        }
    }

    private String locationToKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    // -------------------------------------------------------------------------
    // Save file helpers
    // -------------------------------------------------------------------------

    private File getSaveFile(Player player) {
        File dir = new File(plugin.getDataFolder(), "saves");
        dir.mkdirs();
        return new File(dir, player.getUniqueId() + ".yml");
    }

    public boolean hasSave(Player player) {
        File f = getSaveFile(player);
        if (!f.exists()) return false;
        return YamlConfiguration.loadConfiguration(f).getBoolean("exists", false);
    }

    private void trySave(FileConfiguration cfg, File f, Player player) {
        try {
            cfg.save(f);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data for " + player.getName());
        }
    }

    // -------------------------------------------------------------------------
    // saveGame — the ONLY place block tracking is written to disk
    // -------------------------------------------------------------------------

    public void saveGame(Player player) {
        // Restore HP & food first (mirrors Skript behaviour)
        Attribute maxHpAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        AttributeInstance attr = player.getAttribute(maxHpAttr);
        double maxHp = (attr != null) ? attr.getValue() : 20.0;
        player.setHealth(maxHp);
        player.setFoodLevel(20);
        player.setSaturation(20f);

        player.sendMessage(PREFIX + "§d* Your HP was maxed out.");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2f);

        File f = getSaveFile(player);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);

        // -- Playtime --
        long playtimeStart = cfg.getLong("playtime.start", System.currentTimeMillis());
        long accumulated   = cfg.getLong("playtime.accumulated", 0L)
                + (System.currentTimeMillis() - playtimeStart);
        cfg.set("playtime.accumulated", accumulated);
        cfg.set("playtime.start", System.currentTimeMillis());

        // -- Player state --
        cfg.set("exists", true);
        cfg.set("name", player.getName());
        cfg.set("gamemode", player.getGameMode().name());

        Location loc = player.getLocation();
        cfg.set("location.world",  loc.getWorld().getName());
        cfg.set("location.x",     loc.getX());
        cfg.set("location.y",     loc.getY());
        cfg.set("location.z",     loc.getZ());
        cfg.set("location.yaw",   (double) loc.getYaw());
        cfg.set("location.pitch", (double) loc.getPitch());

        cfg.set("health",     player.getHealth());
        cfg.set("maxhealth",  maxHp);
        cfg.set("food",       player.getFoodLevel());
        cfg.set("saturation", (double) player.getSaturation());

        // -- Inventory --
        for (int i = 0; i < 36; i++) {
            cfg.set("inventory." + i, player.getInventory().getItem(i));
        }
        cfg.set("armor.helmet",     player.getInventory().getHelmet());
        cfg.set("armor.chestplate", player.getInventory().getChestplate());
        cfg.set("armor.leggings",   player.getInventory().getLeggings());
        cfg.set("armor.boots",      player.getInventory().getBoots());

        // -- Potion effects --
        cfg.set("effects", null);
        int ei = 0;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            ei++;
            cfg.set("effects." + ei + ".type",      effect.getType().getKey().getKey());
            cfg.set("effects." + ei + ".duration",  effect.getDuration());
            cfg.set("effects." + ei + ".amplifier", effect.getAmplifier());
        }

        // -- Flush placed buffer to disk --
        UUID uuid = player.getUniqueId();
        List<BlockEntry> newPlaced = placedBuffer.getOrDefault(uuid, List.of());
        int prevPlaced = cfg.getInt("save.blocks.placed.count", 0);
        int curPlaced  = prevPlaced;
        for (BlockEntry e : newPlaced) {
            curPlaced++;
            cfg.set("save.blocks.placed." + curPlaced + ".world", e.world());
            cfg.set("save.blocks.placed." + curPlaced + ".x",     e.x());
            cfg.set("save.blocks.placed." + curPlaced + ".y",     e.y());
            cfg.set("save.blocks.placed." + curPlaced + ".z",     e.z());
            cfg.set("save.blocks.placed." + curPlaced + ".type",  e.type());
        }
        cfg.set("save.blocks.placed.count", curPlaced);
        placedBuffer.remove(uuid);

        // -- Flush broken buffer to disk --
        List<BlockEntry> newBroken = brokenBuffer.getOrDefault(uuid, List.of());
        int prevBroken = cfg.getInt("save.blocks.broken.count", 0);
        int curBroken  = prevBroken;
        for (BlockEntry e : newBroken) {
            curBroken++;
            cfg.set("save.blocks.broken." + curBroken + ".world", e.world());
            cfg.set("save.blocks.broken." + curBroken + ".x",     e.x());
            cfg.set("save.blocks.broken." + curBroken + ".y",     e.y());
            cfg.set("save.blocks.broken." + curBroken + ".z",     e.z());
            cfg.set("save.blocks.broken." + curBroken + ".type",  e.type());
        }
        cfg.set("save.blocks.broken.count", curBroken);
        brokenBuffer.remove(uuid);

        cfg.set("checkpoint.set", true);
        checkpointSet.add(uuid);

        trySave(cfg, f, player);

        player.sendMessage("");
        player.sendMessage(PREFIX + "§a§l✓ Game saved!");
        if (player.isOp()) {
            player.sendMessage("§8§o   " + loc);
            player.sendMessage("§8§o   Blocks placed: " + curPlaced
                    + " (+" + (curPlaced - prevPlaced) + ")"
                    + " | Blocks broken: " + curBroken
                    + " (+" + (curBroken - prevBroken) + ")");
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
    }

    // -------------------------------------------------------------------------
    // loadGame
    // -------------------------------------------------------------------------

    public void loadGame(Player player) {
        File f = getSaveFile(player);
        if (!f.exists() || !YamlConfiguration.loadConfiguration(f).getBoolean("exists", false)) {
            player.sendMessage(PREFIX + "§cNo save file found!");
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        player.sendMessage(PREFIX + "§eLoading your save...");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);

        // -- Teleport --
        String worldName = cfg.getString("location.world", defaultSpawnWorld);
        World world = Bukkit.getWorld(worldName);
        if (world == null) world = Bukkit.getWorlds().getFirst();
        Location loc = new Location(world,
                cfg.getDouble("location.x"),
                cfg.getDouble("location.y"),
                cfg.getDouble("location.z"),
                (float) cfg.getDouble("location.yaw"),
                (float) cfg.getDouble("location.pitch"));
        player.teleport(loc);

        // -- Stats --
        Attribute maxHpAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        AttributeInstance attr = player.getAttribute(maxHpAttr);
        if (attr != null) attr.setBaseValue(cfg.getDouble("maxhealth", 20.0));
        player.setHealth(Math.min(cfg.getDouble("health", 20.0), attr != null ? attr.getValue() : 20.0));
        player.setFoodLevel(cfg.getInt("food", 20));
        player.setSaturation((float) cfg.getDouble("saturation", 20.0));
        player.setGameMode(GameMode.valueOf(cfg.getString("gamemode", "SURVIVAL")));

        // -- Inventory --
        player.getInventory().clear();
        for (int i = 0; i < 36; i++) {
            ItemStack item = cfg.getItemStack("inventory." + i);
            if (item != null) player.getInventory().setItem(i, item);
        }
        player.getInventory().setHelmet(cfg.getItemStack("armor.helmet"));
        player.getInventory().setChestplate(cfg.getItemStack("armor.chestplate"));
        player.getInventory().setLeggings(cfg.getItemStack("armor.leggings"));
        player.getInventory().setBoots(cfg.getItemStack("armor.boots"));

        // -- Potion effects --
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        ConfigurationSection effects = cfg.getConfigurationSection("effects");
        if (effects != null) {
            for (String key : effects.getKeys(false)) {
                String typeName = effects.getString(key + ".type");
                int duration    = effects.getInt(key + ".duration");
                int amplifier   = effects.getInt(key + ".amplifier");
                PotionEffectType pet = Registry.EFFECT.get(NamespacedKey.minecraft(typeName));
                if (pet != null) player.addPotionEffect(new PotionEffect(pet, duration, amplifier));
            }
        }

        // -- Block rollback: only the in-memory buffer matters here.
        //    Anything already committed to disk was saved intentionally
        //    and should stay in the world. We only undo what happened
        //    since the last save. --
        UUID uuid = player.getUniqueId();

        List<BlockEntry> bufferedPlaced = placedBuffer.getOrDefault(uuid, List.of());
        for (BlockEntry e : bufferedPlaced) {
            World w = Bukkit.getWorld(e.world());
            if (w != null) w.getBlockAt(e.x(), e.y(), e.z()).setType(Material.AIR);
        }
        int rolledPlaced = bufferedPlaced.size();
        placedBuffer.remove(uuid);

        List<BlockEntry> bufferedBroken = brokenBuffer.getOrDefault(uuid, List.of());
        for (BlockEntry e : bufferedBroken) {
            World w = Bukkit.getWorld(e.world());
            Material mat = Material.matchMaterial(e.type());
            if (w != null && mat != null) w.getBlockAt(e.x(), e.y(), e.z()).setType(mat);
        }
        int rolledBroken = bufferedBroken.size();
        brokenBuffer.remove(uuid);

        // Reset playtime start for this session
        cfg.set("playtime.start", System.currentTimeMillis());
        trySave(cfg, f, player);

        checkpointSet.add(uuid);

        player.sendMessage(PREFIX + "§a§l✓ Save loaded!");
        if (player.isOp()) {
            player.sendMessage("§a§lRestored " + rolledPlaced
                    + " placed and " + rolledBroken + " broken blocks.");
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    // -------------------------------------------------------------------------
    // deleteSave
    // -------------------------------------------------------------------------

    public void deleteSave(Player target) {
        File f = getSaveFile(target);
        if (f.exists()) f.delete();
        UUID uuid = target.getUniqueId();
        checkpointSet.remove(uuid);
        placedBuffer.remove(uuid);
        brokenBuffer.remove(uuid);
        target.sendMessage(PREFIX + "§cYour save file has been deleted.");
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
    }

    // -------------------------------------------------------------------------
    // sendSaveInfo
    // -------------------------------------------------------------------------

    public void sendSaveInfo(Player sender, Player target) {
        File f = getSaveFile(target);
        if (!f.exists() || !YamlConfiguration.loadConfiguration(f).getBoolean("exists", false)) {
            sender.sendMessage(PREFIX + "§c" + target.getName() + " doesn't have a save file yet!");
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);

        long accumulated = cfg.getLong("playtime.accumulated", 0L);
        long start       = cfg.getLong("playtime.start", 0L);
        long totalMs     = accumulated + (start > 0 ? System.currentTimeMillis() - start : 0L);

        // Show saved counts plus any unsaved buffer so the numbers are live
        UUID uuid = target.getUniqueId();
        int unsavedPlaced = placedBuffer.getOrDefault(uuid, List.of()).size();
        int unsavedBroken = brokenBuffer.getOrDefault(uuid, List.of()).size();
        int savedPlaced   = cfg.getInt("save.blocks.placed.count", 0);
        int savedBroken   = cfg.getInt("save.blocks.broken.count", 0);

        sender.sendMessage(PREFIX + "§6Save Info for " + target.getName() + ":");
        sender.sendMessage("§7  Player: §f"   + cfg.getString("name", target.getName()));
        sender.sendMessage("§7  Location: §f"
                + cfg.getString("location.world") + " "
                + String.format("%.1f", cfg.getDouble("location.x")) + ", "
                + String.format("%.1f", cfg.getDouble("location.y")) + ", "
                + String.format("%.1f", cfg.getDouble("location.z")));
        sender.sendMessage("§7  Health: §f"
                + String.format("%.1f", cfg.getDouble("health"))
                + "/" + String.format("%.1f", cfg.getDouble("maxhealth")));
        sender.sendMessage("§7  Gamemode: §f" + cfg.getString("gamemode", "SURVIVAL"));
        sender.sendMessage("§7  Playtime: §f" + formatPlaytime(totalMs));
        sender.sendMessage("§7  Blocks placed: §f" + savedPlaced
                + (unsavedPlaced > 0 ? " §7(+" + unsavedPlaced + " unsaved)" : ""));
        sender.sendMessage("§7  Blocks broken: §f" + savedBroken
                + (unsavedBroken > 0 ? " §7(+" + unsavedBroken + " unsaved)" : ""));
    }

    private String formatPlaytime(long ms) {
        long seconds = ms / 1000;
        long hours   = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs    = seconds % 60;
        return hours + "h " + minutes + "m " + secs + "s";
    }

    // -------------------------------------------------------------------------
    // Confirmation map (used by DeleteSaveCommand)
    // -------------------------------------------------------------------------

    public Map<UUID, Boolean> getDeleteConfirm() {
        return deleteConfirm;
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onBeaconPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;
        Player player = event.getPlayer();
        if (!player.isOp()) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getItemMeta() == null || !held.getItemMeta().hasDisplayName()) return;
        String name = PlainTextComponentSerializer.plainText().serialize(held.getItemMeta().displayName());
        if (!name.equals("Save Block")) return;

        String key = locationToKey(event.getBlock().getLocation());
        saveBlockLocations.add(key);
        saveSaveBlocks();
        player.sendMessage(PREFIX + "§aSave Block registered at " + event.getBlock().getLocation() + "!");
    }

    @EventHandler
    public void onBeaconBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;
        String key = locationToKey(event.getBlock().getLocation());
        if (!saveBlockLocations.contains(key)) return;
        saveBlockLocations.remove(key);
        saveSaveBlocks();
        if (event.getPlayer().isOp()) {
            event.getPlayer().sendMessage(PREFIX + "§cSave Block unregistered.");
        }
    }

    @EventHandler
    public void onBeaconOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.BEACON) return;
        if (!blockBeaconGui) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBeaconClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.BEACON) return;
        if (event.getPlayer().isSneaking()) return;
        if (!saveBlockLocations.contains(locationToKey(clicked.getLocation()))) return;
        saveGame(event.getPlayer());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        // Zero disk I/O — just append to the in-memory buffer
        if (!checkpointSet.contains(player.getUniqueId())) return;
        Block b = event.getBlock();
        placedBuffer
                .computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>())
                .add(new BlockEntry(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), b.getType().name()));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // Zero disk I/O — just append to the in-memory buffer
        if (!checkpointSet.contains(player.getUniqueId())) return;
        Block b = event.getBlock();
        brokenBuffer
                .computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>())
                .add(new BlockEntry(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), b.getType().name()));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to   = event.getTo();
        Location from = event.getFrom();
        if (to == null) return;
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;

        Block standing = to.getBlock().getRelative(0, -1, 0);
        if (standing.getType() != Material.BEACON) return;
        if (!saveBlockLocations.contains(locationToKey(standing.getLocation()))) return;
        if (player.hasMetadata("savemsg_cooldown")) return;

        List<String> messages = List.of(
                "§d* The power of saving shines within you.",
                "§d* A power shines within... it's you!",
                "§d* Seeing such a friendly save point fills you with power.",
                "§d* You feel your sins crawling on your back...",
                "§d* But nobody came. (Just kidding, you're here!)"
        );
        player.sendMessage(messages.get(new Random().nextInt(messages.size())));
        player.setMetadata("savemsg_cooldown", new FixedMetadataValue(plugin, true));
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> player.removeMetadata("savemsg_cooldown", plugin), 140L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            File f = getSaveFile(player);
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);

            if (cfg.getBoolean("checkpoint.set", false) && cfg.getBoolean("exists", false)) {
                cfg.set("playtime.start", System.currentTimeMillis());
                trySave(cfg, f, player);
                checkpointSet.add(player.getUniqueId());
                loadGame(player);
                player.sendMessage(PREFIX + "§eWelcome back!");
            } else {
                player.sendMessage(PREFIX + "§7No checkpoint found. Find a Save Block to create your first save!");
                teleportToDefaultSpawn(player);
            }
        }, 5L);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (checkpointSet.contains(player.getUniqueId())) {
                loadGame(player);
                player.sendMessage(PREFIX + "§eRespawned at your last checkpoint!");
            } else {
                player.sendMessage(PREFIX + "§7No checkpoint set.");
                teleportToDefaultSpawn(player);
            }
        }, 5L);
    }

    private void teleportToDefaultSpawn(Player player) {
        World world = defaultSpawnWorld != null ? Bukkit.getWorld(defaultSpawnWorld) : null;
        if (world == null) world = Bukkit.getWorlds().getFirst();
        Location loc = defaultSpawnWorld != null
                ? new Location(world, defaultSpawnX, defaultSpawnY, defaultSpawnZ, defaultSpawnYaw, defaultSpawnPitch)
                : world.getSpawnLocation();
        player.teleport(loc);
    }
}