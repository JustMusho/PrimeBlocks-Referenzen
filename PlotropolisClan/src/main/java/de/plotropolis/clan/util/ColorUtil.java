package de.plotropolis.clan.util;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_1 = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_2 = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    private ColorUtil(){}

    public static String c(String input) {
        if (input == null) return "";
        String s = input;

        s = translateHex(s, HEX_1);
        s = translateHex(s, HEX_2);

        s = ChatColor.translateAlternateColorCodes('&', s);
        return s;
    }

    private static String translateHex(String s, Pattern p) {
        Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            ChatColor color = ChatColor.of("#" + hex);
            m.appendReplacement(sb, color.toString());
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
