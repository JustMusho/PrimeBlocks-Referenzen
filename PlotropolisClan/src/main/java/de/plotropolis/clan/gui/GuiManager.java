package de.plotropolis.clan.gui;

import de.plotropolis.clan.PlotropolisClan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GuiManager implements Listener {

    private final PlotropolisClan plugin;
    private final Map<UUID, GuiScreen> open = new HashMap<>();

    public GuiManager(PlotropolisClan plugin) {
        this.plugin = plugin;
    }

    public void open(Player p, GuiScreen screen) {
        open.put(p.getUniqueId(), screen);
        p.openInventory(screen.inventory());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        GuiScreen screen = open.get(p.getUniqueId());
        if (screen == null) return;

        if (!e.getView().getTopInventory().equals(screen.inventory())) return;

        e.setCancelled(true);
        screen.onClick(e, p);
    }
}
