package de.plotropolis.crates.util;

import de.plotropolis.crates.PlotropolisCratesPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static PlotropolisCratesPlugin plugin;
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {}

    public static void init(PlotropolisCratesPlugin pl) {
        plugin = pl;
    }

    public static String color(String s) {
        if (s == null) return "";
        Matcher m = HEX.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) rep.append('§').append(c);
            m.appendReplacement(sb, rep.toString());
        }
        m.appendTail(sb);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
