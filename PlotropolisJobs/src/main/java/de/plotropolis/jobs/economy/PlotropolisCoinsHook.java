package de.plotropolis.jobs.economy;

import de.plotropolis.jobs.PlotropolisJobs;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PlotropolisCoinsHook implements EconomyHook {

    private final PlotropolisJobs plugin;
    private boolean ready;

    private Object coinsInstance;
    private Object dataStore;

    private Method mStoreAddMoneyUuidNameLong;

    private Method mStoreAddMoneyUuidLong;
    private Method mStoreAddMoneyOfflineLong;

    private Method mFormatMoney;

    public PlotropolisCoinsHook(PlotropolisJobs plugin) {
        this.plugin = plugin;
        hook();
    }

    private void hook() {
        Plugin p = Bukkit.getPluginManager().getPlugin("PlotropolisCoins");
        if (p == null || !p.isEnabled()) {
            ready = false;
            return;
        }

        try {
            Class<?> coinsClazz = Class.forName("de.plotropolis.coins.PlotropolisCoins");
            Method getInstance = coinsClazz.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            coinsInstance = getInstance.invoke(null);

            Method getDataStore = coinsClazz.getDeclaredMethod("getDataStore");
            getDataStore.setAccessible(true);
            dataStore = getDataStore.invoke(coinsInstance);

            if (dataStore == null) {
                plugin.getLogger().severe("PlotropolisCoins Hook: DataStore ist null.");
                ready = false;
                return;
            }

            Class<?> storeClass = dataStore.getClass();

            mStoreAddMoneyUuidNameLong = findDeclared(storeClass, "addMoney", UUID.class, String.class, long.class);

            mStoreAddMoneyUuidLong = findDeclared(storeClass, "addMoney", UUID.class, long.class);
            mStoreAddMoneyOfflineLong = findDeclared(storeClass, "addMoney", OfflinePlayer.class, long.class);

            if (mStoreAddMoneyUuidNameLong == null && mStoreAddMoneyUuidLong == null && mStoreAddMoneyOfflineLong == null) {
                plugin.getLogger().severe("PlotropolisCoins Hook: Keine passende addMoney(...) Methode gefunden!");
                plugin.getLogger().severe("Erwartet: addMoney(UUID,String,long) (oder fallback UUID,long / OfflinePlayer,long)");
                ready = false;
                return;
            }

            try {
                Class<?> fmt = Class.forName("de.plotropolis.coins.util.MoneyFormat");
                mFormatMoney = findDeclared(fmt, "formatMoney", long.class);
                if (mFormatMoney == null) mFormatMoney = findDeclared(fmt, "format", long.class);
            } catch (Throwable ignored) {
                mFormatMoney = null;
            }

            ready = true;

            plugin.getLogger().info("PlotropolisCoins Hook OK:");
            if (mStoreAddMoneyUuidNameLong != null) plugin.getLogger().info(" - addMoney(UUID,String,long) ✅");
            else if (mStoreAddMoneyUuidLong != null) plugin.getLogger().info(" - addMoney(UUID,long) ✅");
            else plugin.getLogger().info(" - addMoney(OfflinePlayer,long) ✅");

        } catch (Throwable t) {
            plugin.getLogger().severe("PlotropolisCoins hook failed: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            ready = false;
        }
    }

    private Method findDeclared(Class<?> c, String name, Class<?>... params) {
        try {
            Method m = c.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            try {
                Method m = c.getMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public void addMoney(UUID uuid, long amount) {
        if (!ready || uuid == null || amount <= 0) return;

        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = (op != null && op.getName() != null) ? op.getName() : "unknown";

            if (mStoreAddMoneyUuidNameLong != null) {
                mStoreAddMoneyUuidNameLong.invoke(dataStore, uuid, name, amount);
                return;
            }

            if (mStoreAddMoneyUuidLong != null) {
                mStoreAddMoneyUuidLong.invoke(dataStore, uuid, amount);
                return;
            }

            if (mStoreAddMoneyOfflineLong != null) {
                mStoreAddMoneyOfflineLong.invoke(dataStore, op, amount);
            }

        } catch (Throwable t) {
            plugin.getLogger().warning("PlotropolisCoinsHook addMoney failed: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }

    @Override
    public String format(long amount) {
        try {
            if (mFormatMoney == null) return String.valueOf(amount);
            return String.valueOf(mFormatMoney.invoke(null, amount));
        } catch (Throwable ignored) {
            return String.valueOf(amount);
        }
    }
}
