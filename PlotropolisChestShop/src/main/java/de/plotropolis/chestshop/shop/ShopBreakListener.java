package de.plotropolis.chestshop.shop;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import de.plotropolis.chestshop.util.ColorUtil;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class ShopBreakListener implements Listener {

    private final PlotropolisChestShopPlugin plugin;
    private final ShopManager shopManager;
    private final ColorUtil c;

    public ShopBreakListener(PlotropolisChestShopPlugin plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.c = plugin.color();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();

        boolean isSign = b.getState() instanceof Sign;
        boolean isChest = b.getState() instanceof Chest;

        if (!isSign && !isChest) return;

        Shop shop = null;

        // 1) Schild -> Shop direkt finden
        if (isSign) {
            shop = shopManager.getBySign(b);
        }

        // 2) Kiste -> Shop finden
        if (shop == null && isChest) {
            shop = shopManager.getByChest(b);
        }

        if (shop == null) return;

        boolean admin = p.hasPermission("plotropolis.chestshop.admin");
        boolean owner = shop.ownerUuid().equals(p.getUniqueId());

        // Nur Owner/Admin darf entfernen
        if (!owner && !admin) {
            e.setCancelled(true);
            p.sendMessage(c.msg(plugin.getConfig().getString("messages.no-permission")));
            return;
        }

        // Schild abbauen nur wenn sneak (außer Admin)
        if (isSign && !p.isSneaking() && !admin) {
            e.setCancelled(true);
            p.sendMessage(c.msg(plugin.getConfig().getString(
                    "messages.must-sneak-remove",
                    "%prefix%&7Zum Entfernen bitte ducken."
            )));
            return;
        }

        // ✅ Shop löschen (sauber)
        shopManager.remove(shop);

        p.sendMessage(c.msg(plugin.getConfig().getString(
                "messages.removed",
                "%prefix%&aShop entfernt!"
        )));
    }
}