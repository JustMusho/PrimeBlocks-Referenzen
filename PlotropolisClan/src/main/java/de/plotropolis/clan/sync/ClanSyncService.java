package de.plotropolis.clan.sync;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.data.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class ClanSyncService {

    private final PlotropolisClan plugin;
    private final DataStore store;

    private BukkitTask task;

    public ClanSyncService(PlotropolisClan plugin) {
        this.plugin = plugin;
        this.store = plugin.data();
    }

    public void start() {
        if (task != null) return;

        int seconds = Math.max(3, plugin.getConfig().getInt("sync.refresh-seconds", 10));
        long period = seconds * 20L;

        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!plugin.isEnabled()) return;

            try {
                store.load();
            } catch (Throwable t) {
                plugin.getLogger().warning("ClanSync: load() fehlgeschlagen: " + t.getMessage());
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) return;
                if (plugin.clanTagScoreboard() == null) return;

                try {
                    plugin.clanTagScoreboard().refreshAllForEveryone();
                } catch (Throwable ignored) {}
            });

        }, period, period);

        plugin.getLogger().info("ClanSyncService gestartet (alle " + seconds + "s).");
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
            task = null;
        }
    }

    public boolean isRunning() {
        return task != null;
    }
}
