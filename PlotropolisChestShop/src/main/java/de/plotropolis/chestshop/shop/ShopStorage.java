package de.plotropolis.chestshop.shop;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

public final class ShopStorage {

    private final PlotropolisChestShopPlugin plugin;
    private final File file;

    public ShopStorage(PlotropolisChestShopPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");

        if (!file.exists()) {
            try {
                plugin.saveResource("shops.yml", false);
            } catch (IllegalArgumentException ignored) {
                // shops.yml not bundled - fine, we will create it on save
            }
        }
    }

    public void loadAll(ShopManager manager) {
        manager.clear();

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("shops");
        if (sec == null) {
            plugin.getLogger().info("Loaded shops: 0");
            return;
        }

        int loaded = 0;

        for (String id : sec.getKeys(false)) {
            String base = "shops." + id + ".";

            Location sign = readLoc(yml.getString(base + "sign"));
            Location chest = readLoc(yml.getString(base + "chest"));

            String ownerUuidStr = yml.getString(base + "owner-uuid");
            String ownerName = yml.getString(base + "owner-name");
            String itemKey = yml.getString(base + "item-key");

            if (sign == null || chest == null || ownerUuidStr == null || ownerName == null || itemKey == null) {
                continue;
            }

            UUID owner;
            try {
                owner = UUID.fromString(ownerUuidStr);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            int amount = yml.getInt(base + "amount", 1);
            long buy = yml.getLong(base + "buy", 0L);
            long sell = yml.getLong(base + "sell", 0L);

            // kleine Sanity Checks
            if (amount <= 0) amount = 1;
            if (buy < 0) buy = 0;
            if (sell < 0) sell = 0;

            manager.addLoaded(new Shop(sign, chest, owner, ownerName, amount, buy, sell, itemKey));
            loaded++;
        }

        plugin.getLogger().info("Loaded shops: " + loaded);
    }

    public void saveAll(ShopManager manager) {
        YamlConfiguration yml = new YamlConfiguration();

        // ✅ Section immer frisch bauen (verhindert alte Leichen)
        yml.set("shops", null);

        for (Shop s : manager.all()) {
            if (s == null || s.signLoc() == null || s.chestLoc() == null) continue;

            // ✅ stabiler Key: world:x:y:z vom Schild
            String id = signKey(s.signLoc());
            String base = "shops." + id + ".";

            yml.set(base + "sign", writeLoc(s.signLoc()));
            yml.set(base + "chest", writeLoc(s.chestLoc()));
            yml.set(base + "owner-uuid", s.ownerUuid().toString());
            yml.set(base + "owner-name", s.ownerName());
            yml.set(base + "amount", s.amount());
            yml.set(base + "buy", s.buyPrice());
            yml.set(base + "sell", s.sellPrice());
            yml.set(base + "item-key", s.itemKey());
        }

        try {
            yml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save shops.yml: " + e.getMessage());
        }
    }

    private String signKey(Location l) {
        return l.getWorld().getName()
                + ":" + l.getBlockX()
                + ":" + l.getBlockY()
                + ":" + l.getBlockZ();
    }

    private Location readLoc(String s) {
        if (s == null || s.isBlank()) return null;

        String[] p = s.split(";");
        if (p.length != 4) return null;

        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;

        try {
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            int z = Integer.parseInt(p[3]);
            return new Location(w, x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String writeLoc(Location l) {
        if (l == null || l.getWorld() == null) return null;
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }
}