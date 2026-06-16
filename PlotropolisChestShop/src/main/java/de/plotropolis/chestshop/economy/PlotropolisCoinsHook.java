package de.plotropolis.chestshop.economy;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * PlotropolisCoins Hook (NO Vault)
 *
 * Passt zu deinem PlotropolisCoins Aufbau:
 * - de.plotropolis.coins.PlotropolisCoins#getInstance()
 * - PlotropolisCoins#getDataStore()
 * - DataStore#get(UUID, String) -> PlayerData
 * - PlayerData#getMoney()
 * - DataStore#addMoney(UUID, String, long)
 * - DataStore#removeMoney(UUID, String, long)
 */
public final class PlotropolisCoinsHook implements EconomyHook {

    private final PlotropolisChestShopPlugin plugin;

    private boolean ready;

    private Object coinsInstance; // de.plotropolis.coins.PlotropolisCoins
    private Object dataStore;     // de.plotropolis.coins.storage.DataStore

    private Method mGetInstance;
    private Method mGetDataStore;

    private Method mStoreGetUuidName;   // get(UUID, String) -> PlayerData
    private Method mStoreAddMoney;      // addMoney(UUID, String, long)
    private Method mStoreRemoveMoney;   // removeMoney(UUID, String, long)

    private Method mPlayerDataGetMoney; // PlayerData#getMoney()

    public PlotropolisCoinsHook(PlotropolisChestShopPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        String econPluginName = plugin.getConfig().getString("economy.plugin-name", "PlotropolisCoins");
        Plugin econ = Bukkit.getPluginManager().getPlugin(econPluginName);

        if (econ == null || !econ.isEnabled()) {
            plugin.getLogger().warning("PlotropolisCoins not found/enabled: " + econPluginName);
            ready = false;
            return;
        }

        try {
            // ✅ feste Klassen aus PlotropolisCoins
            Class<?> cCoins = Class.forName("de.plotropolis.coins.PlotropolisCoins");

            mGetInstance = cCoins.getMethod("getInstance");
            coinsInstance = mGetInstance.invoke(null);

            if (coinsInstance == null) {
                plugin.getLogger().warning("PlotropolisCoins.getInstance() returned null.");
                ready = false;
                return;
            }

            mGetDataStore = cCoins.getMethod("getDataStore");
            dataStore = mGetDataStore.invoke(coinsInstance);

            if (dataStore == null) {
                plugin.getLogger().warning("PlotropolisCoins.getDataStore() returned null.");
                ready = false;
                return;
            }

            Class<?> cStore = dataStore.getClass();

            // DataStore.get(UUID, String)
            mStoreGetUuidName = cStore.getMethod("get", UUID.class, String.class);

            // DataStore.addMoney/removeMoney(UUID, String, long)
            mStoreAddMoney = cStore.getMethod("addMoney", UUID.class, String.class, long.class);
            mStoreRemoveMoney = cStore.getMethod("removeMoney", UUID.class, String.class, long.class);

            // PlayerData.getMoney()
            Class<?> cPlayerData = Class.forName("de.plotropolis.coins.storage.PlayerData");
            mPlayerDataGetMoney = cPlayerData.getMethod("getMoney");

            ready = true;
            plugin.getLogger().info("Hooked into PlotropolisCoins (DataStore API).");
        } catch (Exception e) {
            ready = false;
            plugin.getLogger().warning("PlotropolisCoins hook failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public long balance(UUID uuid) {
        if (!ready) return 0L;

        try {
            String name = resolveName(uuid);

            Object playerData = mStoreGetUuidName.invoke(dataStore, uuid, name);
            if (playerData == null) return 0L;

            Object r = mPlayerDataGetMoney.invoke(playerData);
            if (r instanceof Number n) return n.longValue();
        } catch (Exception ignored) { }

        return 0L;
    }

    @Override
    public boolean withdraw(UUID uuid, long amount) {
        if (!ready) return false;
        if (amount <= 0) return true;

        try {
            if (balance(uuid) < amount) return false;

            String name = resolveName(uuid);
            mStoreRemoveMoney.invoke(dataStore, uuid, name, amount);
            return true;
        } catch (Exception ignored) { }

        return false;
    }

    @Override
    public void deposit(UUID uuid, long amount) {
        if (!ready) return;
        if (amount <= 0) return;

        try {
            String name = resolveName(uuid);
            mStoreAddMoney.invoke(dataStore, uuid, name, amount);
        } catch (Exception ignored) { }
    }

    private String resolveName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();

        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        String n = off.getName();
        return (n == null || n.isBlank()) ? "unknown" : n;
    }
}
