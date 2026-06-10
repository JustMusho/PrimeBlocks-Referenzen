package de.plotropolis.clan.listener;

import de.plotropolis.clan.PlotropolisClan;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ClanJoinListener implements Listener {

    private final PlotropolisClan plugin;

    public ClanJoinListener(PlotropolisClan plugin) {
        this.plugin = plugin;
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("settings.clan-tags.enabled", true)) return;
        if (plugin.clanTagScoreboard() == null) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled()) return;
            if (plugin.clanTagScoreboard() == null) return;

            try {
                plugin.clanTagScoreboard().refreshAllForEveryone();
            } catch (Throwable ignored) {
            }
        }, 60L);
    }
}
