package de.plotropolis.crates.economy;

import de.plotropolis.crates.PlotropolisCratesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PlotropolisCoinsHook {

    private final PlotropolisCratesPlugin plugin;

    private Object coinsInstance;
    private Object dataStore;

    private Method mGetDataStore;

    private Method mGetCached;
    private Method mRequestLoad;
    private Method mGet;

    private Method mAddKristalle;
    private Method mRemoveKristalle;

    private Method mPlayerDataGetKristalle;

    public PlotropolisCoinsHook(PlotropolisCratesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        Plugin p = Bukkit.getPluginManager().getPlugin("PlotropolisCoins");
        if (p == null || !p.isEnabled()) {
            plugin.getLogger().severe("PlotropolisCoins nicht gefunden/disabled.");
            return false;
        }

        try {
            this.coinsInstance = p;

            mGetDataStore = coinsInstance.getClass().getMethod("getDataStore");
            dataStore = mGetDataStore.invoke(coinsInstance);
            if (dataStore == null) {
                plugin.getLogger().severe("PlotropolisCoins.getDataStore() returned null.");
                return false;
            }

            Class<?> ds = dataStore.getClass();

            mGetCached = ds.getMethod("getCached", UUID.class);
            mRequestLoad = ds.getMethod("requestLoad", UUID.class, String.class);
            mGet = ds.getMethod("get", UUID.class, String.class);

            mAddKristalle = ds.getMethod("addKristalle", UUID.class, String.class, long.class);
            mRemoveKristalle = ds.getMethod("removeKristalle", UUID.class, String.class, long.class);

            mPlayerDataGetKristalle = null;

            plugin.getLogger().info("PlotropolisCoinsHook erfolgreich initialisiert.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("CoinsHook init failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return false;
        }
    }

    public long getBalance(Player player) {
        UUID uuid = player.getUniqueId();
        String name = safeName(player.getName());

        try {
            Object pd = mGetCached.invoke(dataStore, uuid);
            if (pd == null) {
                mRequestLoad.invoke(dataStore, uuid, name);

                pd = mGet.invoke(dataStore, uuid, name);
            }

            if (pd == null) return 0L;

            if (mPlayerDataGetKristalle == null) {
                mPlayerDataGetKristalle = pd.getClass().getMethod("getKristalle");
            }

            Object val = mPlayerDataGetKristalle.invoke(pd);
            if (val instanceof Number n) return n.longValue();
            return 0L;

        } catch (Exception e) {
            return 0L;
        }
    }

    public boolean has(Player player, long amount) {
        return getBalance(player) >= amount;
    }

    public boolean withdraw(Player player, long amount) {
        if (amount <= 0) return true;
        if (!has(player, amount)) return false;

        try {
            mRemoveKristalle.invoke(dataStore, player.getUniqueId(), safeName(player.getName()), amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void deposit(Player player, long amount) {
        if (amount <= 0) return;
        try {
            mAddKristalle.invoke(dataStore, player.getUniqueId(), safeName(player.getName()), amount);
        } catch (Exception ignored) {}
    }

    private String safeName(String name) {
        return (name == null || name.isBlank()) ? "unknown" : name;
    }
}
