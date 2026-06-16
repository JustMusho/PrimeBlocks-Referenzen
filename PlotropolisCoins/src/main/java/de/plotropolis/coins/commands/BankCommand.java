package de.plotropolis.coins.commands;

import de.plotropolis.coins.PlotropolisCoins;
import de.plotropolis.coins.storage.PlayerData;
import de.plotropolis.coins.util.ColorUtil;
import de.plotropolis.coins.util.MoneyFormat;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class BankCommand implements CommandExecutor, TabCompleter {

    private final PlotropolisCoins plugin;

    public BankCommand(PlotropolisCoins plugin) {
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

        if (!sender.hasPermission("plotropoliscoins.bank")) {
            send(sender, "no-permission");
            return true;
        }

        if (!(sender instanceof Player p)) {
            send(sender, "player-only");
            return true;
        }

        if (!plugin.getConfig().getBoolean("bank.enabled", true)) {
            send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            PlayerData pd = plugin.getDataStore().get(p.getUniqueId(), p.getName());
            send(sender, "bank-self", "{bank}", MoneyFormat.formatMoney(pd.getBank()));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (args.length < 2) {
            send(sender, "invalid-number");
            return true;
        }

        Long amount = parsePositive(args[1]);
        if (amount == null) {
            send(sender, "invalid-number");
            return true;
        }

        if (sub.equals("einzahlen")) {
            boolean ok = plugin.getDataStore().bankDeposit(p.getUniqueId(), p.getName(), amount);
            if (!ok) {
                send(sender, "bank-not-enough-wallet");
                return true;
            }

            PlayerData pd = plugin.getDataStore().get(p.getUniqueId(), p.getName());
            send(sender, "bank-deposit-ok",
                    "{amount}", MoneyFormat.formatMoney(amount),
                    "{bank}", MoneyFormat.formatMoney(pd.getBank())
            );
            return true;
        }

        if (sub.equals("auszahlen")) {
            boolean ok = plugin.getDataStore().bankWithdraw(p.getUniqueId(), p.getName(), amount);
            if (!ok) {
                send(sender, "bank-not-enough-bank");
                return true;
            }

            PlayerData pd = plugin.getDataStore().get(p.getUniqueId(), p.getName());
            send(sender, "bank-withdraw-ok",
                    "{amount}", MoneyFormat.formatMoney(amount),
                    "{bank}", MoneyFormat.formatMoney(pd.getBank())
            );
            return true;
        }

        send(sender, "invalid-number");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            if ("einzahlen".startsWith(args[0].toLowerCase())) list.add("einzahlen");
            if ("auszahlen".startsWith(args[0].toLowerCase())) list.add("auszahlen");
        }
        return list;
    }
}
