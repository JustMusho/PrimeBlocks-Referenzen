package de.plotropolis.coins.commands;

import de.plotropolis.coins.PlotropolisCoins;
import de.plotropolis.coins.storage.PlayerData;
import de.plotropolis.coins.util.ColorUtil;
import de.plotropolis.coins.util.MoneyFormat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class MoneyCommand implements CommandExecutor, TabCompleter {

    private final PlotropolisCoins plugin;

    public MoneyCommand(PlotropolisCoins plugin) {
        this.plugin = plugin;
    }

    private String prefix() {
        return ColorUtil.colorize(
                plugin.getConfig().getString("prefix",
                        "&#2fd9ff&lP&#4fdfff&lL&#6fe5ff&lO&#8febff&lT&#aff1ff&lR&#cff7ff&lO&#effdff&lP&#ffffff&lO&#ffffff&lL&#ffffff&lI&#ffffff&lS &f&l» &7"),
                plugin.getConfig().getBoolean("use-hex-colors", true)
        );
    }

    private void send(CommandSender s, String path, String... kv) {
        String raw = plugin.getConfig().getString("messages." + path, "");
        String msg = raw.replace("{prefix}", prefix());
        for (int i = 0; i + 1 < kv.length; i += 2) msg = msg.replace(kv[i], kv[i + 1]);
        s.sendMessage(ColorUtil.colorize(msg, plugin.getConfig().getBoolean("use-hex-colors", true)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("plotropoliscoins.money")) {
            send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                send(sender, "player-only");
                return true;
            }

            PlayerData pd = plugin.getDataStore().get(p.getUniqueId(), p.getName());
            send(sender, "money-self", "{money}", MoneyFormat.formatMoney(pd.getMoney()));
            return true;
        }

        String targetName = args[0];

        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            PlayerData pd = plugin.getDataStore().get(online.getUniqueId(), online.getName());
            send(sender, "money-other",
                    "{player}", online.getName(),
                    "{money}", MoneyFormat.formatMoney(pd.getMoney())
            );
            return true;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
        if (offline.getName() == null) {
            send(sender, "player-not-found");
            return true;
        }

        String name = offline.getName();
        PlayerData pd = plugin.getDataStore().get(offline.getUniqueId(), name);
        send(sender, "money-other",
                "{player}", name,
                "{money}", MoneyFormat.formatMoney(pd.getMoney())
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(start)) list.add(p.getName());
            }
        }
        return list;
    }
}
