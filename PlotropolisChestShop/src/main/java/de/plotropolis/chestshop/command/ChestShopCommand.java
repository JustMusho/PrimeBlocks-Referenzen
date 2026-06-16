package de.plotropolis.chestshop.command;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import de.plotropolis.chestshop.util.ColorUtil;
import org.bukkit.command.*;

import java.util.ArrayList;
import java.util.List;

public final class ChestShopCommand implements CommandExecutor, TabCompleter {

    private final PlotropolisChestShopPlugin plugin;
    private final ColorUtil c;

    public ChestShopCommand(PlotropolisChestShopPlugin plugin) {
        this.plugin = plugin;
        this.c = plugin.color();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("plotropolis.chestshop.admin")) {
            sender.sendMessage(c.msg(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(c.c("%prefix%&7/pcs reload").replace("%prefix%", c.prefix()));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("plotropolis.chestshop.admin.reload")) {
                sender.sendMessage(c.msg(plugin.getConfig().getString("messages.no-permission")));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(c.msg(plugin.getConfig().getString("messages.reloaded")));
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("plotropolis.chestshop.admin.reload")) {
            if ("reload".startsWith(args[0].toLowerCase())) out.add("reload");
        }
        return out;
    }
}
