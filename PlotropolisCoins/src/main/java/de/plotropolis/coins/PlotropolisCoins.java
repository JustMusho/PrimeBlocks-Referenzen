package de.plotropolis.coins;

import de.plotropolis.coins.commands.*;
import de.plotropolis.coins.listener.CoinsJoinQuitListener;
import de.plotropolis.coins.placeholders.PlotropolisPlaceholderExpansion;
import de.plotropolis.coins.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlotropolisCoins extends JavaPlugin {

    private static PlotropolisCoins instance;
    private DataStore dataStore;

    public static PlotropolisCoins getInstance() {
        return instance;
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        dataStore = new DataStore(this);
        dataStore.init();

        getServer().getPluginManager().registerEvents(new CoinsJoinQuitListener(this), this);

        register("pay", new PayCommand(this));
        register("money", new MoneyCommand(this));
        register("bank", new BankCommand(this));

        register("addmoney", new AddMoneyCommand(this));
        register("removemoney", new RemoveMoneyCommand(this));
        register("setmoney", new SetMoneyCommand(this));

        register("addkristalle", new AddKristalleCommand(this));
        register("removekristalle", new RemoveKristalleCommand(this));
        register("setkristalle", new SetKristalleCommand(this));

        if (getConfig().getBoolean("placeholders.enabled", true)
                && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlotropolisPlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI gefunden: PlotropolisCoins Placeholders registriert.");
        } else {
            getLogger().info("PlaceholderAPI nicht gefunden oder deaktiviert: Placeholders sind aus.");
        }

        getLogger().info("PlotropolisCoins aktiviert.");
    }

    private void register(String name, Object executorAndTab) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().severe("Command fehlt in plugin.yml: " + name);
            return;
        }
        c.setExecutor((org.bukkit.command.CommandExecutor) executorAndTab);
        c.setTabCompleter((org.bukkit.command.TabCompleter) executorAndTab);
    }

    @Override
    public void onDisable() {
        if (dataStore != null) dataStore.shutdown();
        getLogger().info("PlotropolisCoins gespeichert & deaktiviert.");
    }
}
