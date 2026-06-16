package de.plotropolis.coins.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");

    private ColorUtil() {}

    public static String colorize(String input, boolean hex) {
        if (input == null) return "";
        String msg = input;

        if (hex) {
            Matcher matcher = HEX_PATTERN.matcher(msg);
            while (matcher.find()) {
                String hexCode = matcher.group(1);
                StringBuilder replacement = new StringBuilder("§x");
                for (char c : hexCode.toCharArray()) {
                    replacement.append('§').append(c);
                }
                msg = msg.replace(matcher.group(0), replacement.toString());
            }
        }

        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
