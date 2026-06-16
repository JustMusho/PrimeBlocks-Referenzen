package de.plotropolis.coins.commands;

import de.plotropolis.coins.PlotropolisCoins;
import de.plotropolis.coins.util.ColorUtil;
import de.plotropolis.coins.util.MoneyFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class PayCommand implements CommandExecutor, TabCompleter {

    private final PlotropolisCoins plugin;

    public PayCommand(PlotropolisCoins plugin) {
        this.plugin = plugin;
    }

    private String prefix() {
        return ColorUtil.colorize(plugin.getConfig().getString("prefix",
                        "&#2fd9ff&lP&#4fdfff&lL&#6fe5ff&lO&#8febff&lT&#aff1ff&lR&#cff7ff&lO&#effdff&lP&#ffffff&lO&#ffffff&lL&#ffffff&lI&#ffffff&lS &f&l» &7"),
                plugin.getConfig().getBoolean("use-hex-colors", true));
    }

    private void send(CommandSender s, String path, String... kv) {
        String raw = plugin.getConfig().getString("messages." + path, "");
        String msg = raw.replace("{prefix}", prefix());
        for (int i = 0; i + 1 < kv.length; i += 2) msg = msg.replace(kv[i], kv[i + 1]);
        s.sendMessage(ColorUtil.colorize(msg, plugin.getConfig().getBoolean("use-hex-colors", true)));
    }

    private Long parsePositive(String in) {
        try {
            long v = Long.parseLong(in);
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("plotropoliscoins.pay")) {
            send(sender, "no-permission");
            return true;
        }

        if (!(sender instanceof Player p)) {
            send(sender, "player-only");
            return true;
        }

        if (args.length < 2) {
            send(sender, "pay-usage");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "player-not-found");
            return true;
        }

        if (target.getUniqueId().equals(p.getUniqueId())) {
            send(sender, "pay-self");
            return true;
        }

        Long amount = parsePositive(args[1]);
        if (amount == null) {
            send(sender, "invalid-number");
            return true;
        }

        boolean ok = plugin.getDataStore().transferMoney(
                p.getUniqueId(), p.getName(),
                target.getUniqueId(), target.getName(),
                amount
        );

        if (!ok) {
            send(sender, "pay-not-enough");
            return true;
        }

        String formatted = MoneyFormat.formatMoney(amount);

        send(sender, "pay-success-sender",
                "{amount}", formatted,
                "{player}", target.getName()
        );
        send(target, "pay-success-target",
                "{amount}", formatted,
                "{player}", p.getName()
        );

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) list.add(p.getName());
            }
        }
        return list;
    }
}
