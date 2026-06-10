package de.plotropolis.jobs;

import de.plotropolis.jobs.bossbar.BossBarService;
import de.plotropolis.jobs.cmd.JobsCommand;
import de.plotropolis.jobs.cmd.JobsTabCompleter;
import de.plotropolis.jobs.data.JobsDataStore;
import de.plotropolis.jobs.data.MySqlDataStore;
import de.plotropolis.jobs.data.YamlDataStore;
import de.plotropolis.jobs.economy.EconomyHook;
import de.plotropolis.jobs.economy.PlotropolisCoinsHook;
import de.plotropolis.jobs.gui.JobsGUI;
import de.plotropolis.jobs.listener.JobsListener;
import de.plotropolis.jobs.mysql.MySql;
import de.plotropolis.jobs.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PlotropolisJobs extends JavaPlugin {

    private static PlotropolisJobs instance;

    private EconomyHook economy;
    private JobsDataStore dataStore;
    private MySql mySql;
    private BossBarService bossBars;
    private JobsGUI gui;

    private int payoutTaskId = -1;
    private int bossbarCleanupTaskId = -1;
    private int autosaveTaskId = -1;

    public static PlotropolisJobs get() {
        return instance;
    }

    public EconomyHook economy() {
        return economy;
    }

    public JobsDataStore data() {
        return dataStore;
    }

    public BossBarService bossBars() {
        return bossBars;
    }

    public JobsGUI gui() {
        return gui;
    }

    public String prefix() {
        return ColorUtil.c(getConfig().getString("prefix", "&bPlotropolis &f» &7"));
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        reloadConfig();

        this.economy = new PlotropolisCoinsHook(this);
        if (!economy.isReady()) {
            getLogger().severe("PlotropolisCoins wurde nicht gefunden oder API passt nicht. PlotropolisJobs wird deaktiviert.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.dataStore = createStore();

        this.bossBars = new BossBarService(this);
        this.gui = new JobsGUI(this);

        Bukkit.getPluginManager().registerEvents(new JobsListener(this), this);

        PluginCommand cmd = getCommand("jobs");
        Objects.requireNonNull(cmd).setExecutor(new JobsCommand(this));
        cmd.setTabCompleter(new JobsTabCompleter());

        int sec = Math.max(5, getConfig().getInt("settings.payout-interval-seconds", 60));
        payoutTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (var p : Bukkit.getOnlinePlayers()) {
                dataStore.flushPayouts(p.getUniqueId());
            }
        }, 20L, sec * 20L).getTaskId();

        bossbarCleanupTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            bossBars.tickCleanup();
        }, 20L, 20L).getTaskId();

        autosaveTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (var p : Bukkit.getOnlinePlayers()) {
                dataStore.save(p.getUniqueId());
            }
        }, 20L * 60, 20L * 60).getTaskId();

        getLogger().info("PlotropolisJobs enabled. Storage=" + dataStore.getClass().getSimpleName());
    }

    private JobsDataStore createStore() {
        ConfigurationSection mysqlCfg = getConfig().getConfigurationSection("mysql");
        if (mysqlCfg == null) {
            getLogger().info("MySQL nicht konfiguriert -> benutze YAML Storage.");
            YamlDataStore yml = new YamlDataStore(this);
            yml.loadAllOnline();
            return yml;
        }

        try {
            this.mySql = new MySql(mysqlCfg);
            getLogger().info("MySQL verbunden & Tabelle bereit: " + mysqlCfg.getString("table"));
            return new MySqlDataStore(this, mySql);
        } catch (Exception e) {
            getLogger().severe("MySQL konnte nicht initialisiert werden -> Fallback auf YAML. Fehler: " + e.getMessage());
            e.printStackTrace();

            YamlDataStore yml = new YamlDataStore(this);
            yml.loadAllOnline();
            return yml;
        }
    }

    @Override
    public void onDisable() {
        if (payoutTaskId != -1) Bukkit.getScheduler().cancelTask(payoutTaskId);
        if (bossbarCleanupTaskId != -1) Bukkit.getScheduler().cancelTask(bossbarCleanupTaskId);
        if (autosaveTaskId != -1) Bukkit.getScheduler().cancelTask(autosaveTaskId);


        if (dataStore != null) {
            for (var p : Bukkit.getOnlinePlayers()) {
                dataStore.save(p.getUniqueId());
                dataStore.unload(p.getUniqueId());
            }
            dataStore.shutdown();
        }

        if (bossBars != null) bossBars.clearAll();

        if (mySql != null) {
            try {
                mySql.close();
            } catch (Exception ignored) {}
        }

        getLogger().info("PlotropolisJobs disabled.");
    }
}
