package de.plotropolis.chestshop.gui;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import de.plotropolis.chestshop.util.ColorUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ShopInfoGuiListener implements Listener {

    private final PlotropolisChestShopPlugin plugin;
    private final String titleRaw;

    public ShopInfoGuiListener(PlotropolisChestShopPlugin plugin) {
        this.plugin = plugin;
        ColorUtil c = plugin.color();
        // exakt der GUI Title wie in der Config (farbig) -> exakt matchen
        this.titleRaw = c.c(plugin.getConfig().getString("gui.title", "SHOPINFO"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView() == null) return;
        if (!e.getView().getTitle().equals(titleRaw)) return;

        // ✅ Alles blocken: rausnehmen, reinlegen, shiftclick, hotbar swap, etc.
        e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView() == null) return;
        if (!e.getView().getTitle().equals(titleRaw)) return;

        // ✅ Auch Dragging blocken
        e.setCancelled(true);
    }
}
