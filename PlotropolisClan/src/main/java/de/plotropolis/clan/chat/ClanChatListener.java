package de.plotropolis.clan.chat;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.model.Clan;
import de.plotropolis.clan.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ClanChatListener implements Listener {

    private final PlotropolisClan plugin;

    public ClanChatListener(PlotropolisClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {

        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) return;

        String lower = msg.toLowerCase();
        if (!lower.startsWith("@clan")) return;

        Player p = e.getPlayer();
        Clan clan = plugin.data().getClanOf(p.getUniqueId());

        e.setCancelled(true);
        e.getRecipients().clear();

        if (clan == null) {
            p.sendMessage(ColorUtil.c("&cDu bist in keinem Clan."));
            return;
        }

        String content = msg.length() > 5 ? msg.substring(5).trim() : "";
        if (content.isEmpty()) {
            p.sendMessage(ColorUtil.c("&cBitte schreibe: &7@clan <nachricht>"));
            return;
        }

        String format = ColorUtil.c("&8[&bCLAN&8] &f" + p.getName() + " &8» &7" + content);

        clan.members().keySet().forEach(uuid -> {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(format);
            }
        });
    }
}