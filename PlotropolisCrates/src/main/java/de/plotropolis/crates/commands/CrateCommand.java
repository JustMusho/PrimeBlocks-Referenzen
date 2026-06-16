package de.plotropolis.crates.commands;

import de.plotropolis.crates.PlotropolisCratesPlugin;
import de.plotropolis.crates.crates.Crate;
import de.plotropolis.crates.util.ColorUtil;
import de.plotropolis.crates.util.LocKey;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public final class CrateCommand implements CommandExecutor, TabCompleter {

    private final PlotropolisCratesPlugin plugin;

    public CrateCommand(PlotropolisCratesPlugin plugin) {
        this.plugin = plugin;
    }

    private String msg(String path, Map<String, String> repl) {
        String s = plugin.getConfig().getString("messages." + path, "");
        s = s.replace("%prefix%", plugin.prefix());
        if (repl != null) for (var e : repl.entrySet()) s = s.replace(e.getKey(), e.getValue());
        return ColorUtil.color(s);
    }

    private void send(CommandSender s, String path) {
        s.sendMessage(msg(path, null));
    }

    private boolean perm(CommandSender s, String perm) {
        if (!s.hasPermission(perm)) {
            send(s, "no-permission");
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtil.color(plugin.prefix() + "&7/pcrate create <Name> <Price>"));
            sender.sendMessage(ColorUtil.color(plugin.prefix() + "&7/pcrate delete <Name>"));
            sender.sendMessage(ColorUtil.color(plugin.prefix() + "&7/pcrate edit <Name>"));
            sender.sendMessage(ColorUtil.color(plugin.prefix() + "&7/pcrate list"));
            sender.sendMessage(ColorUtil.color(plugin.prefix() + "&7/pcrate set <Name>"));
            sender.sendMessage(ColorUtil.color(plugin.prefix() + "&7/pcrate remove <Name>"));
            sender.sendMessage(ColorUtil.color(plugin.prefix() + "&7/pcrate give <Player> <Name> <Amount>"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("list")) {
            if (!perm(sender, "plotropoliscrates.cmd.list")) return true;
            List<String> crates = Crate.listCrates(plugin);
            sender.sendMessage(ColorUtil.color(plugin.prefix() + "&7Crates: &f" + (crates.isEmpty() ? "&ckeine" : String.join("&7, &f", crates))));
            return true;
        }

        if (sub.equals("create")) {
            if (!perm(sender, "plotropoliscrates.cmd.create")) return true;
            if (args.length < 3) return false;

            String name = args[1];
            long price;
            try { price = Long.parseLong(args[2]); }
            catch (Exception e) { sender.sendMessage(ColorUtil.color(plugin.prefix() + "&cPreis muss eine Zahl sein.")); return true; }

            if (Crate.exists(plugin, name)) {
                sender.sendMessage(ColorUtil.color(plugin.prefix() + "&cDiese Crate existiert bereits."));
                return true;
            }

            Crate c = Crate.create(plugin, name, price);

            plugin.putCrate(c);

            sender.sendMessage(msg("crate-created", Map.of("%crate%", c.getName())));
            return true;
        }

        if (sub.equals("delete")) {
            if (!perm(sender, "plotropoliscrates.cmd.delete")) return true;
            if (args.length < 2) return false;

            String name = args[1];

            Crate c = plugin.getCrate(name);
            if (c == null) { send(sender, "unknown-crate"); return true; }

            plugin.holograms().removeAllPlacements(c);

            File f = Crate.file(plugin, c.getName());
            boolean ok = f.delete();
            if (ok) {
                plugin.removeCrate(c.getName());
                sender.sendMessage(msg("crate-deleted", Map.of("%crate%", c.getName())));
            } else {
                sender.sendMessage(ColorUtil.color(plugin.prefix() + "&cKonnte Datei nicht löschen."));
            }
            return true;
        }

        if (sub.equals("set")) {
            if (!(sender instanceof Player p)) { send(sender, "only-player"); return true; }
            if (!perm(sender, "plotropoliscrates.cmd.set")) return true;
            if (args.length < 2) return false;

            String name = args[1];

            Crate c = plugin.getCrate(name);
            if (c == null) { send(sender, "unknown-crate"); return true; }

            Block b = p.getTargetBlockExact(6);
            if (b == null) { p.sendMessage(ColorUtil.color(plugin.prefix() + "&cDu musst einen Block anschauen.")); return true; }

            String key = LocKey.of(b.getLocation());
            c.getPlacements().add(key);
            c.save(plugin);

            plugin.putCrate(c);

            plugin.holograms().spawnForPlacement(c, b.getLocation());

            p.sendMessage(msg("crate-set", Map.of("%crate%", c.getName())));
            return true;
        }

        if (sub.equals("remove")) {
            if (!(sender instanceof Player p)) { send(sender, "only-player"); return true; }
            if (!perm(sender, "plotropoliscrates.cmd.remove")) return true;
            if (args.length < 2) return false;

            String name = args[1];

            Crate c = plugin.getCrate(name);
            if (c == null) { send(sender, "unknown-crate"); return true; }

            Block b = p.getTargetBlockExact(6);
            if (b == null) { p.sendMessage(ColorUtil.color(plugin.prefix() + "&cDu musst einen Block anschauen.")); return true; }

            String key = LocKey.of(b.getLocation());
            if (!c.getPlacements().remove(key)) {
                p.sendMessage(ColorUtil.color(plugin.prefix() + "&cDiese Crate ist nicht auf diesem Block gesetzt."));
                return true;
            }

            c.save(plugin);
            plugin.putCrate(c);

            plugin.holograms().removeAt(b.getLocation());

            p.sendMessage(msg("crate-removed", Map.of("%crate%", c.getName())));
            return true;
        }

        if (sub.equals("give")) {
            if (!perm(sender, "plotropoliscrates.cmd.give")) return true;
            if (args.length < 4) return false;

            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) { sender.sendMessage(ColorUtil.color(plugin.prefix() + "&cSpieler offline.")); return true; }

            String crateName = args[2];

            Crate c = plugin.getCrate(crateName);
            if (c == null) { send(sender, "unknown-crate"); return true; }

            int amount;
            try { amount = Integer.parseInt(args[3]); }
            catch (Exception e) { sender.sendMessage(ColorUtil.color(plugin.prefix() + "&cAmount muss Zahl sein.")); return true; }
            if (amount <= 0) { sender.sendMessage(ColorUtil.color(plugin.prefix() + "&cAmount muss > 0 sein.")); return true; }

            plugin.playerData().addCrates(t.getUniqueId(), c.getName(), amount);
            t.sendMessage(msg("crate-given", Map.of("%crate%", c.getDisplayName(), "%x%", String.valueOf(amount))));
            return true;
        }

        if (sub.equals("edit")) {
            if (!(sender instanceof Player p)) { send(sender, "only-player"); return true; }
            if (!perm(sender, "plotropoliscrates.cmd.edit")) return true;
            if (args.length < 2) return false;

            Crate c = plugin.getCrate(args[1]);
            if (c == null) { send(sender, "unknown-crate"); return true; }

            plugin.gui().openEdit(p, c, 1);
            return true;
        }

        sender.sendMessage(ColorUtil.color(plugin.prefix() + "&cUnbekannter Subcommand."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("create","delete","edit","list","set","remove","give");
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);

            if (sub.equals("give")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }

            if (List.of("delete","edit","set","remove").contains(sub)) {
                return Crate.listCrates(plugin).stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                return Crate.listCrates(plugin).stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return List.of("1","5","10","32","64");
        }

        return Collections.emptyList();
    }
}