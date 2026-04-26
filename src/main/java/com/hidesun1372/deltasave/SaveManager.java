package com.hidesun1372.deltasave;

import com.hidesun1372.deltasave.gui.SaveMenuGui;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.persistence.PersistentDataType;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SaveManager implements Listener {

    public static String PREFIX = "§6[SAVE] §r";

    // Default spawn — set via /setdefaultspawn or config.yml
    private String defaultSpawnWorld;
    private double defaultSpawnX, defaultSpawnY, defaultSpawnZ;
    private float defaultSpawnYaw, defaultSpawnPitch;

    private final DeltaSavePlugin plugin;

    // -------------------------------------------------------------------------
    // In-memory state — no disk reads/writes except at save, load, join
    // -------------------------------------------------------------------------

    record SaveBlockData(String name, int chapter) {}

    /** Registered save block locations mapped to their name and chapter (persisted to saveblocks.yml) */
    private final Map<String, SaveBlockData> saveBlockLocations = new HashMap<>();

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
    private long scanInterval;
    private long beaconMsgCooldown;
    private long deleteConfirmTimeoutGui;
    private long deleteConfirmTimeoutCmd;
    private List<String> beaconMessages = List.of(
            "§d* The power of saving shines within you.",
            "§d* A power shines within... it's you!",
            "§d* Seeing such a friendly save point fills you with power.",
            "§d* You feel your sins crawling on your back...",
            "§d* But nobody came. (Just kidding, you're here!)"
    );

    private SaveMenuGui saveMenuGui;

    public void setSaveMenuGui(SaveMenuGui gui) { this.saveMenuGui = gui; }

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
        loadConfig();
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
        for (Map<?, ?> m : cfg.getMapList("blocks")) {
            String key  = (String) m.get("key");
            String name = (String) m.get("name");
            int chapter = m.get("chapter") instanceof Number n ? n.intValue() : 1;
            if (key != null && name != null) saveBlockLocations.put(key, new SaveBlockData(name, chapter));
        }
        // Migrate old format (plain location list with no names)
        for (String key : cfg.getStringList("locations")) {
            saveBlockLocations.putIfAbsent(key, new SaveBlockData("Unknown Location", 1));
        }
    }

    private void loadConfig() {
        FileConfiguration cfg = plugin.getConfig();
        PREFIX                = cfg.getString("prefix", "§6[SAVE] §r");
        blockBeaconGui        = cfg.getBoolean("block-beacon-gui", true);
        scanInterval          = cfg.getLong("scan-interval", 1200L);
        beaconMsgCooldown     = cfg.getLong("beacon-message-cooldown", 140L);
        deleteConfirmTimeoutGui = cfg.getLong("delete-confirm-timeout-gui", 160L);
        deleteConfirmTimeoutCmd = cfg.getLong("delete-confirm-timeout-command", 160L);
        List<String> fromCfg  = cfg.getStringList("beacon-messages");
        if (!fromCfg.isEmpty()) beaconMessages = fromCfg;
        if (!cfg.contains("defaultspawn")) return;
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
        plugin.getConfig().set("block-beacon-gui", blockBeaconGui);
        plugin.saveConfig();
        player.sendMessage(PREFIX + (blockBeaconGui ? "§aBeacon GUI blocked." : "§cBeacon GUI unblocked."));
    }

    private String msg(String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    private void playConfigSound(Player player, String key) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("sounds." + key);
        if (s == null) return;
        String soundName = s.getString("sound", "");
        float volume = (float) s.getDouble("volume", 1.0);
        float pitch  = (float) s.getDouble("pitch", 1.0);
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound '" + soundName + "' for sounds." + key);
        }
    }

    public long getDeleteConfirmTimeoutGui() { return deleteConfirmTimeoutGui; }
    public long getDeleteConfirmTimeoutCmd() { return deleteConfirmTimeoutCmd; }

    private void saveSaveBlocks() {
        File file = getSaveBlocksFile();
        FileConfiguration cfg = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, SaveBlockData> entry : saveBlockLocations.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key",     entry.getKey());
            m.put("name",    entry.getValue().name());
            m.put("chapter", entry.getValue().chapter());
            list.add(m);
        }
        cfg.set("blocks", list);
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
        if (!dir.mkdirs() && !dir.exists()) {
            plugin.getLogger().severe("Could not create saves directory!");
        }
        return new File(dir, player.getUniqueId() + ".yml");
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
        saveGame(player, null);
    }

    public void saveGame(Player player, String locationName) {
        if (locationName != null) {
            player.sendMessage(PREFIX + "§d* " + locationName);
        }

        // Restore HP & food first so that full health is written to the save
        Attribute maxHpAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        AttributeInstance attr = maxHpAttr != null ? player.getAttribute(maxHpAttr) : null;
        double maxHp = (attr != null) ? attr.getValue() : 20.0;
        player.setHealth(maxHp);
        player.setFoodLevel(20);
        player.setSaturation(20f);

        player.sendMessage(PREFIX + msg("save-hp-restored"));
        playConfigSound(player, "save");

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
        player.sendMessage(PREFIX + msg("save-success"));
        if (player.isOp()) {
            player.sendMessage("§8§o   " + loc);
            player.sendMessage("§8§o   Blocks placed: " + curPlaced
                    + " (+" + (curPlaced - prevPlaced) + ")"
                    + " | Blocks broken: " + curBroken
                    + " (+" + (curBroken - prevBroken) + ")");
        }
        playConfigSound(player, "save-orb");
    }

    // -------------------------------------------------------------------------
    // loadGame
    // -------------------------------------------------------------------------

    public void loadGame(Player player) {
        File f = getSaveFile(player);
        if (!f.exists() || !YamlConfiguration.loadConfiguration(f).getBoolean("exists", false)) {
            player.sendMessage(PREFIX + msg("no-save"));
            playConfigSound(player, "no-save");
            return;
        }

        player.sendMessage(PREFIX + msg("load-loading"));
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
        AttributeInstance attr = maxHpAttr != null ? player.getAttribute(maxHpAttr) : null;
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
                if (typeName == null) continue;
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

        player.sendMessage(PREFIX + msg("load-success"));
        if (player.isOp()) {
            player.sendMessage("§a§lRestored " + rolledPlaced
                    + " placed and " + rolledBroken + " broken blocks.");
        }
        playConfigSound(player, "load");
    }

    // -------------------------------------------------------------------------
    // deleteSave
    // -------------------------------------------------------------------------

    public void deleteSave(Player target) {
        File f = getSaveFile(target);
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("Could not delete save file for " + target.getName());
        }
        UUID uuid = target.getUniqueId();
        checkpointSet.remove(uuid);
        placedBuffer.remove(uuid);
        brokenBuffer.remove(uuid);

        target.getInventory().clear();
        target.getInventory().setHelmet(null);
        target.getInventory().setChestplate(null);
        target.getInventory().setLeggings(null);
        target.getInventory().setBoots(null);
        target.getActivePotionEffects().forEach(e -> target.removePotionEffect(e.getType()));
        Attribute maxHpAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        AttributeInstance attr = maxHpAttr != null ? target.getAttribute(maxHpAttr) : null;
        double maxHp = attr != null ? attr.getValue() : 20.0;
        target.setHealth(maxHp);
        target.setFoodLevel(20);
        target.setSaturation(20f);
        teleportToDefaultSpawn(target);

        target.sendMessage(PREFIX + msg("delete-success"));
        playConfigSound(target, "delete");
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

    public FileConfiguration getSaveConfig(Player player) {
        File f = getSaveFile(player);
        if (!f.exists()) return null;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        return cfg.getBoolean("exists", false) ? cfg : null;
    }

    public int getUnsavedPlacedCount(UUID uuid) {
        return placedBuffer.getOrDefault(uuid, List.of()).size();
    }

    public int getUnsavedBrokenCount(UUID uuid) {
        return brokenBuffer.getOrDefault(uuid, List.of()).size();
    }

    public String formatPlaytime(long ms) {
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
        ItemMeta heldMeta = held.getItemMeta();
        if (heldMeta == null || !heldMeta.hasDisplayName()) return;
        var nameComponent = heldMeta.displayName();
        if (nameComponent == null) return;
        String displayName = PlainTextComponentSerializer.plainText().serialize(nameComponent);
        if (!displayName.equals("Save Block")) return;

        NamespacedKey nameKey    = new NamespacedKey(plugin, "save_block_location");
        NamespacedKey chapterKey = new NamespacedKey(plugin, "save_block_chapter");
        String locationName = heldMeta.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
        Integer chapter     = heldMeta.getPersistentDataContainer().get(chapterKey, PersistentDataType.INTEGER);
        if (locationName == null || chapter == null) {
            player.sendMessage(PREFIX + "§cThis Save Block is missing data. Use /givesaveblock to get a valid one.");
            event.setCancelled(true);
            return;
        }

        String key = locationToKey(event.getBlock().getLocation());
        saveBlockLocations.put(key, new SaveBlockData(locationName, chapter));
        saveSaveBlocks();
        player.sendMessage(PREFIX + "§aSave Block \"§f" + locationName + "§a\" (ch. " + chapter + ") registered!");
    }

    public void startScanTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanSaveBlocks, scanInterval, scanInterval);
    }

    public int scanSaveBlocks() {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, SaveBlockData> entry : saveBlockLocations.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length != 4) { toRemove.add(entry.getKey()); continue; }
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            if (world.getBlockAt(x, y, z).getType() != Material.BEACON) {
                toRemove.add(entry.getKey());
            }
        }
        if (toRemove.isEmpty()) return 0;
        for (String key : toRemove) {
            SaveBlockData data = saveBlockLocations.remove(key);
            plugin.getLogger().info("Save Block \"" + data.name() + "\" at " + key + " no longer exists — unregistered.");
        }
        saveSaveBlocks();
        String msg = PREFIX + "§e" + toRemove.size()
                + " save block" + (toRemove.size() == 1 ? "" : "s")
                + " unregistered (no longer present in world).";
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp()) op.sendMessage(msg);
        }
        return toRemove.size();
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
        String key = locationToKey(clicked.getLocation());
        if (!saveBlockLocations.containsKey(key)) return;
        SaveBlockData data = saveBlockLocations.get(key);
        if (saveMenuGui != null) {
            saveMenuGui.open(event.getPlayer(), data.name(), data.chapter());
        } else {
            saveGame(event.getPlayer(), data.name());
        }
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
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;

        Block standing = to.getBlock().getRelative(0, -1, 0);
        if (standing.getType() != Material.BEACON) return;
        if (player.hasMetadata("savemsg_cooldown")) return;

        player.sendMessage(beaconMessages.get(new Random().nextInt(beaconMessages.size())));
        player.setMetadata("savemsg_cooldown", new FixedMetadataValue(plugin, true));
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> player.removeMetadata("savemsg_cooldown", plugin), beaconMsgCooldown);
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
                player.sendMessage(PREFIX + msg("welcome-back"));
            } else {
                player.sendMessage(PREFIX + msg("no-checkpoint"));
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
                player.sendMessage(PREFIX + msg("respawned"));
            } else {
                player.sendMessage(PREFIX + "§7No checkpoint set.");
                teleportToDefaultSpawn(player);
            }
        }, 5L);
    }

    public void teleportToDefaultSpawn(Player player) {
        World world = defaultSpawnWorld != null ? Bukkit.getWorld(defaultSpawnWorld) : null;
        if (world == null) world = Bukkit.getWorlds().getFirst();
        Location loc = defaultSpawnWorld != null
                ? new Location(world, defaultSpawnX, defaultSpawnY, defaultSpawnZ, defaultSpawnYaw, defaultSpawnPitch)
                : world.getSpawnLocation();
        player.teleport(loc);
    }
}