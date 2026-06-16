package de.plotropolis.crates.listener;

import de.plotropolis.crates.PlotropolisCratesPlugin;
import de.plotropolis.crates.crates.Crate;
import de.plotropolis.crates.util.LocKey;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class BlockInteractListener implements Listener {

    private final PlotropolisCratesPlugin plugin;

    public BlockInteractListener(PlotropolisCratesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.LEFT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        String key = LocKey.of(b.getLocation());
        Crate found = plugin.holograms().crateByPlacementKey(key);
        if (found == null) return;

        e.setCancelled(true);

        Player p = e.getPlayer();
        if (!p.hasPermission("plotropoliscrates.preview")) {
            p.sendMessage(plugin.prefix() + "§cDafür hast du keine Rechte.");
            return;
        }

        Location l = b.getLocation();
        Location crateLoc = (l.getWorld() == null) ? null : new Location(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        plugin.gui().rememberLastCrateLocation(p.getUniqueId(), crateLoc);

        plugin.gui().openPreview(p, found, 1);
    }
}