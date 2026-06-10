package de.plotropolis.clan;

import de.plotropolis.clan.chat.ClanChatListener;
import de.plotropolis.clan.cmd.ClanCommand;
import de.plotropolis.clan.cmd.ClanTabCompleter;
import de.plotropolis.clan.data.DataStore;
import de.plotropolis.clan.data.MySqlDataStore;
import de.plotropolis.clan.data.YamlDataStore;
import de.plotropolis.clan.economy.PlotropolisCoinsBridge;
import de.plotropolis.clan.gui.GuiManager;
import de.plotropolis.clan.listener.ClanJoinListener;
import de.plotropolis.clan.placeholders.ClanPlaceholders;
import de.plotropolis.clan.scoreboard.ClanTagScoreboardService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PlotropolisClan extends JavaPlugin {

    private DataStore dataStore;
    private PlotropolisCoinsBridge economy;
    private GuiManager guiManager;

    private ClanTagScoreboardService clanTagScoreboard;

    private BukkitTask mysqlSyncTask;

    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!Bukkit.getPluginManager().isPluginEnabled("PlotropolisBoard")) {
            getLogger().severe("PlotropolisBoard ist nicht geladen! PlotropolisClan benötigt PlotropolisBoard.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String serverId = getConfig().getString("network.server-id", "").trim();
        if (serverId.isEmpty()) {
            serverId = "default";
            getLogger().warning("network.server-id fehlt in config.yml! Fallback='default'. /clan top wird dann nicht sauber getrennt.");
        }

        boolean mysqlEnabled = isMysqlEnabled();
        try {
            if (mysqlEnabled) {
                this.dataStore = new MySqlDataStore(this);
                getLogger().info("DataStore: MySQL aktiv. (server-id=" + serverId + ")");
            } else {
                this.dataStore = new YamlDataStore(this);
                getLogger().info("DataStore: YAML (data.yml) aktiv.");
            }
            this.dataStore.load();
        } catch (Throwable t) {
            getLogger().severe("DataStore konnte nicht initialisiert werden: " + t.getMessage());
            t.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.economy = new PlotropolisCoinsBridge(this);
        if (!this.economy.isReady()) {
            getLogger().severe("PlotropolisCoins Hook fehlgeschlagen!");
            getLogger().severe("Stelle sicher, dass PlotropolisCoins geladen ist und keine Fehler hat.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.guiManager = new GuiManager(this);

        var cmd = new ClanCommand(this);
        if (getCommand("clan") != null) {
            getCommand("clan").setExecutor(cmd);
            getCommand("clan").setTabCompleter(new ClanTabCompleter(this));
        }

        Bukkit.getPluginManager().registerEvents(new ClanChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);

        Bukkit.getPluginManager().registerEvents(new ClanJoinListener(this), this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ClanPlaceholders(this).register();
            getLogger().info("PlaceholderAPI gefunden: Placeholders aktiviert.");
        } else {
            getLogger().info("PlaceholderAPI nicht gefunden: Plugin läuft trotzdem.");
        }

        if (getConfig().getBoolean("settings.clan-tags.enabled", true)) {
            clanTagScoreboard = new ClanTagScoreboardService(this);
            clanTagScoreboard.start();
            getLogger().info("ClanTags: Scoreboard-Service aktiv.");
        } else {
            getLogger().info("ClanTags: deaktiviert (settings.clan-tags.enabled=false).");
        }

        if (mysqlEnabled) {
            startMysqlSyncTask();
        }

        getLogger().info("PlotropolisClan enabled.");
    }

    @Override
    public void onDisable() {
        if (saveTask != null) {
            try { saveTask.cancel(); } catch (Throwable ignored) {}
            saveTask = null;
        }

        if (mysqlSyncTask != null) {
            try { mysqlSyncTask.cancel(); } catch (Throwable ignored) {}
            mysqlSyncTask = null;
        }

        if (clanTagScoreboard != null) {
            try { clanTagScoreboard.shutdown(); } catch (Throwable ignored) {}
            clanTagScoreboard = null;
        }

        if (dataStore != null) {
            try { dataStore.save(); } catch (Throwable ignored) {}

            try {
                if (dataStore instanceof AutoCloseable) {
                    ((AutoCloseable) dataStore).close();
                }
            } catch (Throwable ignored) {}
        }
    }

    private boolean isMysqlEnabled() {
        String host = getConfig().getString("mysql.host", "").trim();
        return !host.isEmpty();
    }

    private void startMysqlSyncTask() {
        int seconds = Math.max(3, getConfig().getInt("sync.refresh-seconds", 10));
        long period = seconds * 20L;

        this.mysqlSyncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                dataStore.load();
            } catch (Throwable t) {
                getLogger().warning("MySQL Sync fehlgeschlagen: " + t.getMessage());
            }
        }, period, period);

        getLogger().info("MySQL Sync aktiv: alle " + seconds + "s.");
    }

    public void requestSave() {
        if (saveTask != null) {
            try { saveTask.cancel(); } catch (Throwable ignored) {}
            saveTask = null;
        }

        saveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
            try {
                if (dataStore != null) dataStore.save();
            } catch (Throwable t) {
                getLogger().warning("Save fehlgeschlagen: " + t.getMessage());
            }
        }, 10L);
    }

    public DataStore data() { return dataStore; }
    public PlotropolisCoinsBridge economy() { return economy; }
    public GuiManager gui() { return guiManager; }

    public ClanTagScoreboardService clanTagScoreboard() { return clanTagScoreboard; }
}