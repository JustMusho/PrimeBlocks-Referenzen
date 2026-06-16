package de.plotropolis.chestshop.holo;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import de.plotropolis.chestshop.shop.Shop;
import de.plotropolis.chestshop.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class HologramManager implements Listener {

    private final PlotropolisChestShopPlugin plugin;
    private final ShopManager shops;

    private int taskId = -1;

    // ✅ stabile Keys (Sign-Block Position)
    private final Map<String, Item> active = new HashMap<>();
    private final Map<String, Set<UUID>> viewers = new HashMap<>();

    public HologramManager(PlotropolisChestShopPlugin plugin, ShopManager shops) {
        this.plugin = plugin;
        this.shops = shops;
    }

    public void start() {
        int interval = plugin.getConfig().getInt("hologram.tick-interval", 10);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, interval, interval);
    }

    public void shutdown() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;

        for (Item it : active.values()) {
            if (it != null && !it.isDead()) it.remove();
        }
        active.clear();
        viewers.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        for (Set<UUID> set : viewers.values()) set.remove(u);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) return;

        double rayDist = plugin.getConfig().getDouble("hologram.raytrace-distance", 6.0);
        String perm = plugin.getConfig().getString("hologram.permission", "plotropolis.chestshop.hologram");

        // ✅ IDs der existierenden Shops sammeln + viewer reset
        Set<String> shopIds = new HashSet<>();
        for (Shop s : shops.all()) {
            String id = shopId(s);
            shopIds.add(id);
            viewers.computeIfAbsent(id, k -> new HashSet<>()).clear();
        }

        // ✅ Viewer-Tracking (wer schaut gerade ein Shop-Schild an)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (perm != null && !perm.isBlank() && !p.hasPermission(perm)) continue;

            Block b = p.getTargetBlockExact((int) Math.ceil(rayDist));
            if (b == null) continue;

            if (!(b.getState() instanceof org.bukkit.block.Sign)) continue;

            Shop shop = shops.getBySign(b);
            if (shop == null) continue;

            viewers.get(shopId(shop)).add(p.getUniqueId());
        }

        // ✅ Spawn/Despawn für alle Shops
        for (Shop s : shops.all()) {
            String id = shopId(s);

            boolean shouldExist = viewers.containsKey(id) && !viewers.get(id).isEmpty();
            boolean exists = active.containsKey(id);

            if (shouldExist && !exists) spawn(id, s);
            if (!shouldExist && exists) despawn(id);
        }

        // ✅ HARD CLEANUP:
        // falls Shops neu geladen wurden / alte Keys hängen -> weg damit
        Iterator<Map.Entry<String, Item>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Item> en = it.next();
            String id = en.getKey();
            Item ent = en.getValue();

            boolean shopStillExists = shopIds.contains(id);
            boolean someoneWatching = viewers.containsKey(id) && !viewers.get(id).isEmpty();

            if (!shopStillExists || !someoneWatching || ent == null || ent.isDead()) {
                if (ent != null && !ent.isDead()) ent.remove();
                it.remove();
            }
        }
    }

    private void spawn(String id, Shop s) {
        final double yOff = plugin.getConfig().getDouble("hologram.y-offset", 1.25);

        final Location loc = s.chestLoc().clone().add(0.5, yOff, 0.5);
        if (loc.getWorld() == null) return;

        ItemStack stack = buildShopItem(s);
        if (stack == null || stack.getType() == Material.AIR) return;

        // ✅ Drop-Item Look (dreht + bobbt automatisch)
        Item drop = loc.getWorld().dropItem(loc, stack);

        drop.setPickupDelay(Integer.MAX_VALUE);
        drop.setGravity(false);
        drop.setVelocity(drop.getVelocity().zero());
        drop.setUnlimitedLifetime(true);
        drop.setInvulnerable(true);

        active.put(id, drop);
    }

    private void despawn(String id) {
        Item ent = active.remove(id);
        if (ent != null && !ent.isDead()) ent.remove();
    }

    private String shopId(Shop s) {
        return key(s.signLoc());
    }

    private String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private ItemStack buildShopItem(Shop s) {
        // Nexo
        if (s.itemKey().startsWith("nexo:")) {
            String id = s.itemKey().substring("nexo:".length());
            ItemStack nexoItem = plugin.nexo().itemFromId(id, 1);
            if (nexoItem != null) return nexoItem;
            return null;
        }

        // Vanilla
        if (s.itemKey().startsWith("minecraft:")) {
            String matName = s.itemKey().substring("minecraft:".length()).toUpperCase(Locale.ROOT);
            Material m = Material.matchMaterial(matName);
            if (m != null) return new ItemStack(m, 1);
        }

        return null;
    }
}
