package de.plotropolis.crates.hologram;

import de.plotropolis.crates.PlotropolisCratesPlugin;
import de.plotropolis.crates.crates.Crate;
import de.plotropolis.crates.util.ColorUtil;
import de.plotropolis.crates.util.LocKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class HologramService implements Listener {

    private final PlotropolisCratesPlugin plugin;

    private final Map<String, List<UUID>> holograms = new HashMap<>();
    private final Map<String, String> placementToCrate = new HashMap<>();

    private final Map<String, Crate> crateCache = new HashMap<>();

    private BukkitRunnable updater;

    private static final String HOLO_TAG = "plotropoliscrates_holo";
    private final NamespacedKey KEY_HOLO = new NamespacedKey("plotropoliscrates", "holo");
    private final NamespacedKey KEY_PLACEMENT = new NamespacedKey("plotropoliscrates", "placement");

    public HologramService(PlotropolisCratesPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void loadAll() {
        cleanupOrphanHolograms();

        refreshAll();

        long ticks = plugin.getConfig().getLong("settings.update-holograms-every-ticks", 1200);
        if (ticks <= 0) return;

        updater = new BukkitRunnable() {
            @Override public void run() {
                updateExistingTexts();
            }
        };
        updater.runTaskTimer(plugin, ticks, ticks);
    }

    public void shutdown() {
        if (updater != null) updater.cancel();
        for (String key : new ArrayList<>(holograms.keySet())) {
            removeByKey(key);
        }
        holograms.clear();
        placementToCrate.clear();
        crateCache.clear();
    }

    private void cleanupOrphanHolograms() {
        for (World w : Bukkit.getWorlds()) {
            for (ArmorStand stand : w.getEntitiesByClass(ArmorStand.class)) {
                if (isOurHologram(stand)) stand.remove();
            }
        }
    }

    private boolean isOurHologram(ArmorStand stand) {
        if (stand == null) return false;
        if (stand.getScoreboardTags().contains(HOLO_TAG)) return true;

        Byte flag = stand.getPersistentDataContainer().get(KEY_HOLO, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    public void refreshAll() {
        for (String key : new ArrayList<>(holograms.keySet())) removeByKey(key);
        holograms.clear();
        placementToCrate.clear();
        crateCache.clear();

        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) return;

        for (String crateName : Crate.listCrates(plugin)) {
            Crate c = Crate.load(plugin, crateName);
            if (c == null) continue;
            crateCache.put(c.getName(), c);

            for (String placementKey : c.getPlacements()) {
                if (placementKey == null || placementKey.isBlank()) continue;

                placementToCrate.put(placementKey, c.getName());

                Location loc = LocKey.toLocation(placementKey);
                if (loc == null || loc.getWorld() == null) continue;

                if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

                spawnForPlacement(c, loc);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) return;

        Chunk ch = e.getChunk();
        World w = ch.getWorld();
        int cx = ch.getX();
        int cz = ch.getZ();

        for (Map.Entry<String, String> en : placementToCrate.entrySet()) {
            String placementKey = en.getKey();
            String crateName = en.getValue();

            Location loc = LocKey.toLocation(placementKey);
            if (loc == null || loc.getWorld() == null) continue;
            if (loc.getWorld() != w) continue;

            if ((loc.getBlockX() >> 4) != cx) continue;
            if ((loc.getBlockZ() >> 4) != cz) continue;

            if (holograms.containsKey(placementKey)) continue;

            Crate c = getCrateCached(crateName);
            if (c == null) continue;

            spawnForPlacement(c, loc);
        }
    }

    private void updateExistingTexts() {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) return;

        List<String> lines = plugin.getConfig().getStringList("hologram.lines");
        if (lines == null || lines.isEmpty()) return;

        for (Map.Entry<String, List<UUID>> entry : new ArrayList<>(holograms.entrySet())) {
            String placementKey = entry.getKey();
            String crateName = placementToCrate.get(placementKey);
            if (crateName == null) continue;

            Crate c = getCrateCached(crateName);
            if (c == null) continue;

            List<UUID> ids = entry.getValue();
            if (ids == null || ids.isEmpty()) continue;

            for (int i = 0; i < ids.size() && i < lines.size(); i++) {
                Entity ent = Bukkit.getEntity(ids.get(i));
                if (!(ent instanceof ArmorStand stand) || stand.isDead()) continue;

                String line = lines.get(i)
                        .replace("%crate%", c.getDisplayName())
                        .replace("%amount%", "0");

                stand.setCustomName(ColorUtil.color(line));
            }
        }
    }

    public void spawnForPlacement(Crate c, Location blockLoc) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) return;
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        if (!blockLoc.getWorld().isChunkLoaded(blockLoc.getBlockX() >> 4, blockLoc.getBlockZ() >> 4)) return;

        String key = LocKey.of(blockLoc);

        removeAt(blockLoc);

        double yOff = plugin.getConfig().getDouble("hologram.y-offset", 1.35);
        List<String> lines = plugin.getConfig().getStringList("hologram.lines");
        if (lines == null || lines.isEmpty()) return;

        List<UUID> ids = new ArrayList<>(lines.size());
        Location base = blockLoc.clone().add(0.5, yOff, 0.5);

        for (int i = 0; i < lines.size(); i++) {
            final String rawLine = lines.get(i);
            Location l = base.clone().add(0, (lines.size() - 1 - i) * 0.27, 0);

            ArmorStand as = blockLoc.getWorld().spawn(l, ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setGravity(false);
                stand.setCustomNameVisible(true);

                stand.addScoreboardTag(HOLO_TAG);
                stand.getPersistentDataContainer().set(KEY_HOLO, PersistentDataType.BYTE, (byte) 1);
                stand.getPersistentDataContainer().set(KEY_PLACEMENT, PersistentDataType.STRING, key);

                String line = rawLine
                        .replace("%crate%", c.getDisplayName())
                        .replace("%amount%", "0");

                stand.setCustomName(ColorUtil.color(line));
            });

            ids.add(as.getUniqueId());
        }

        holograms.put(key, ids);

        placementToCrate.put(key, c.getName());
        crateCache.put(c.getName(), c);
    }

    public void removeAt(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        removeByKey(LocKey.of(blockLoc));

        double r = 1.6;
        for (Entity ent : blockLoc.getWorld().getNearbyEntities(blockLoc.clone().add(0.5, 1.0, 0.5), r, 3.0, r)) {
            if (ent instanceof ArmorStand stand && isOurHologram(stand)) {
                stand.remove();
            }
        }
    }

    private void removeByKey(String key) {
        List<UUID> ids = holograms.remove(key);
        if (ids != null) {
            for (UUID id : ids) {
                Entity ent = Bukkit.getEntity(id);
                if (ent != null) ent.remove();
            }
        }
        placementToCrate.remove(key);
    }

    public void removeAllPlacements(Crate c) {
        for (String key : new ArrayList<>(c.getPlacements())) removeByKey(key);
    }

    public Crate crateByPlacementKey(String key) {
        String crateName = placementToCrate.get(key);
        if (crateName == null) return null;

        return getCrateCached(crateName);
    }

    private Crate getCrateCached(String crateName) {
        if (crateName == null) return null;
        Crate cached = crateCache.get(crateName);
        if (cached != null) return cached;

        Crate loaded = Crate.load(plugin, crateName);
        if (loaded != null) crateCache.put(crateName, loaded);
        return loaded;
    }
}