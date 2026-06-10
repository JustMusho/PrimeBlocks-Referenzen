package de.plotropolis.clan.cmd;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public final class ClanTabCompleter implements TabCompleter {

    private final PlotropolisClan plugin;

    public ClanTabCompleter(PlotropolisClan plugin) {
        this.plugin = plugin;
    }

    private List<String> filter(List<String> base, String token) {
        if (token == null || token.isEmpty()) return base;
        String t = token.toLowerCase(Locale.ROOT);
        return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t)).collect(Collectors.toList());
    }

    private List<String> clanTags() {
        return plugin.data().clansByTag().values().stream().map(Clan::tag).distinct().collect(Collectors.toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            return filter(Arrays.asList(
                    "help","hilfe",
                    "create","erstellen",
                    "delete","löschen","loeschen",
                    "info",
                    "beitreten","join",
                    "kick",
                    "mitglieder","members",
                    "bestenliste","top",
                    "einladen","invite",
                    "einladungen","invites",
                    "einladung","invitation",
                    "bank",
                    "aktivität","aktivitaet","activity",
                    "rolle","role",
                    "admin"
            ), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if ((sub.equals("info") || sub.equals("beitreten") || sub.equals("join") || sub.equals("mitglieder") || sub.equals("members") || sub.equals("aktivität") || sub.equals("aktivitaet") || sub.equals("activity"))
                && args.length == 2) {
            return filter(clanTags(), args[1]);
        }

        if ((sub.equals("einladen") || sub.equals("invite") || sub.equals("kick")) && args.length == 2) {
            return filter(Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).collect(Collectors.toList()), args[1]);
        }

        if (sub.equals("einladung") || sub.equals("invitation")) {
            if (args.length == 2) return filter(Arrays.asList("annehmen","ablehnen","accept","deny"), args[1]);
            if (args.length == 3) return filter(clanTags(), args[2]);
        }

        if (sub.equals("bank") && args.length == 2) {
            return filter(Arrays.asList("einzahlen","auszahlen","info","deposit","withdraw"), args[1]);
        }

        if ((sub.equals("rolle") || sub.equals("role")) && args.length == 2) {
            return filter(Arrays.asList("gui","erstellen","create","löschen","loeschen","delete","permissions","perms","setzen","set"), args[1]);
        }

        if ((sub.equals("rolle") || sub.equals("role")) && (args[1].equalsIgnoreCase("setzen") || args[1].equalsIgnoreCase("set")) && args.length == 4) {
            return filter(Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).collect(Collectors.toList()), args[3]);
        }

        if (sub.equals("admin") && args.length == 2) {
            return filter(Arrays.asList("verify","löschen","loeschen","delete","bank","kick"), args[1]);
        }
        if (sub.equals("admin") && args.length == 3 && (args[1].equalsIgnoreCase("verify") || args[1].equalsIgnoreCase("löschen") || args[1].equalsIgnoreCase("loeschen") || args[1].equalsIgnoreCase("delete"))) {
            return filter(clanTags(), args[2]);
        }
        if (sub.equals("admin") && args[1].equalsIgnoreCase("bank") && args.length == 3) {
            return filter(List.of("setzen"), args[2]);
        }
        if (sub.equals("admin") && args[1].equalsIgnoreCase("bank") && args.length == 4) {
            return filter(clanTags(), args[3]);
        }
        if (sub.equals("admin") && args[1].equalsIgnoreCase("kick") && args.length == 3) {
            return filter(Bukkit.getOnlinePlayers().stream().map(p -> p.getName()).collect(Collectors.toList()), args[2]);
        }

        return Collections.emptyList();
    }
}
