package de.plotropolis.jobs.cmd;

import de.plotropolis.jobs.PlotropolisJobs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class JobsCommand implements CommandExecutor {

    private final PlotropolisJobs plugin;

    public JobsCommand(PlotropolisJobs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Only players.");
                return true;
            }
            if (!p.hasPermission("plotropolisjobs.command.jobs")) {
                p.sendMessage(plugin.prefix() + "&cKeine Rechte.");
                return true;
            }
            plugin.gui().open(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!s.hasPermission("plotropolisjobs.command.reload")) {
                s.sendMessage(plugin.prefix() + "&cKeine Rechte.");
                return true;
            }
            plugin.reloadConfig();
            s.sendMessage(plugin.prefix() + "&aConfig neu geladen.");
            return true;
        }

        s.sendMessage(plugin.prefix() + "&7/jobs &f→ GUI");
        s.sendMessage(plugin.prefix() + "&7/jobs reload &f→ Config reload");
        return true;
    }
}
