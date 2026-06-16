package de.plotropolis.chestshop.util;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private final PlotropolisChestShopPlugin plugin;
    private String prefix;

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public ColorUtil(PlotropolisChestShopPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.prefix = c(plugin.getConfig().getString("prefix", "&bPlotropolis &7» &7"));
    }

    public String prefix() { return prefix; }

    public String c(String in) {
        if (in == null) return "";
        String msg = in;

        Matcher m = HEX.matcher(msg);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            String repl = net.md_5.bungee.api.ChatColor.of("#" + hex).toString();
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);

        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public String msg(String raw) {
        if (raw == null) raw = "";
        return c(raw.replace("%prefix%", prefix));
    }
}
