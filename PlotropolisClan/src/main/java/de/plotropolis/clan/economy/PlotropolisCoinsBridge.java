package de.plotropolis.clan.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PlotropolisCoinsBridge {

    private final JavaPlugin plugin;
    private final Plugin coins;

    private Object dataStore;

    private Method dsGetOffline;
    private Method dsAddMoney;
    private Method dsTakeMoney;
    private Method pdGetMoney;

    private Method moneyFormat;

    public PlotropolisCoinsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.coins = Bukkit.getPluginManager().getPlugin("PlotropolisCoins");
        hook();
    }

    public boolean isReady() {
        return coins != null && coins.isEnabled()
                && dataStore != null
                && dsGetOffline != null
                && dsAddMoney != null
                && dsTakeMoney != null
                && pdGetMoney != null;
    }

    private void hook() {
        if (coins == null) return;

        try {
            Method getDataStore = coins.getClass().getMethod("getDataStore");
            dataStore = getDataStore.invoke(coins);

            Class<?> dsClass = dataStore.getClass();

            dsGetOffline = dsClass.getMethod("get", OfflinePlayer.class);

            dsAddMoney = dsClass.getMethod("addMoney", UUID.class, String.class, long.class);

            dsTakeMoney = dsClass.getMethod("takeMoney", UUID.class, String.class, long.class);

            Class<?> pdClass = Class.forName("de.plotropolis.coins.storage.PlayerData", true, coins.getClass().getClassLoader());
            pdGetMoney = pdClass.getMethod("getMoney");

            try {
                Class<?> mfClass = Class.forName("de.plotropolis.coins.util.MoneyFormat", true, coins.getClass().getClassLoader());
                moneyFormat = mfClass.getDeclaredMethod("formatMoney", long.class);
                moneyFormat.setAccessible(true);
            } catch (Throwable ignored) {
                moneyFormat = null;
            }

        } catch (Throwable t) {
            plugin.getLogger().severe("[PlotropolisClan] CoinsBridge Hook Error: " + t.getMessage());
            t.printStackTrace();
            dataStore = null;
        }
    }

    private String safeName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        if (name == null || name.isBlank()) return "unknown";
        return name;
    }

    private Object getPlayerData(UUID uuid) throws Exception {
        return dsGetOffline.invoke(dataStore, Bukkit.getOfflinePlayer(uuid));
    }

    public long getMoney(UUID uuid) {
        try {
            Object pd = getPlayerData(uuid);
            Object money = pdGetMoney.invoke(pd);
            return (money instanceof Number) ? ((Number) money).longValue() : 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }

    public boolean withdrawMoney(UUID uuid, long amount) {
        if (amount <= 0) return true;
        try {
            return (boolean) dsTakeMoney.invoke(dataStore, uuid, safeName(uuid), amount);
        } catch (Throwable t) {
            return false;
        }
    }

    public void depositMoney(UUID uuid, long amount) {
        if (amount <= 0) return;
        try {
            dsAddMoney.invoke(dataStore, uuid, safeName(uuid), amount);
        } catch (Throwable ignored) {}
    }

    public String formatMoney(long amount) {
        try {
            if (moneyFormat != null) {
                Object out = moneyFormat.invoke(null, amount);
                if (out != null) return out.toString();
            }
        } catch (Throwable ignored) {}
        return amount + "€";
    }
}
