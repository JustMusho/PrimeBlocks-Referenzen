package de.plotropolis.chestshop.command;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import de.plotropolis.chestshop.gui.ShopInfoGui;
import de.plotropolis.chestshop.shop.Shop;
import de.plotropolis.chestshop.shop.ShopManager;
import de.plotropolis.chestshop.util.ColorUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class ShopInfoCommand implements CommandExecutor, TabCompleter {

    private final PlotropolisChestShopPlugin plugin;
    private final ShopManager shops;
    private final ColorUtil c;

    public ShopInfoCommand(PlotropolisChestShopPlugin plugin, ShopManager shops) {
        this.plugin = plugin;
        this.shops = shops;
        this.c = plugin.color();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (!p.hasPermission("plotropolis.chestshop.info")) {
            p.sendMessage(c.msg(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        Shop s = shops.getByTargeted(p);
        if (s == null) {
            p.sendMessage(c.msg(plugin.getConfig().getString("messages.not-a-shop")));
            return true;
        }

        ShopInfoGui.open(plugin, p, s);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
