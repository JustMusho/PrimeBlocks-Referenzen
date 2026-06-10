package de.plotropolis.jobs.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public final class JobsTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase()) && s.hasPermission("plotropolisjobs.command.reload")) out.add("reload");
        }
        return out;
    }
}
