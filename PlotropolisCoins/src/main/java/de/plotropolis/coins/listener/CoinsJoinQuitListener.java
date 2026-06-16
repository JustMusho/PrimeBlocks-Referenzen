package de.plotropolis.coins.listener;

import de.plotropolis.coins.PlotropolisCoins;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CoinsJoinQuitListener implements Listener {

    private final PlotropolisCoins plugin;

    public CoinsJoinQuitListener(PlotropolisCoins plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getDataStore().requestLoad(p.getUniqueId(), p.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.getDataStore().requestSave(p.getUniqueId(), p.getName());
    }
}
