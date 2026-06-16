package de.plotropolis.coins.util;

import de.plotropolis.coins.PlotropolisCoins;

public final class MoneyFormat {

    private MoneyFormat() {}

    public static String format(long value, String symbol, String thousandsSep) {
        boolean negative = value < 0;
        long abs = Math.abs(value);

        String s = Long.toString(abs);
        StringBuilder out = new StringBuilder();

        int len = s.length();
        for (int i = 0; i < len; i++) {
            out.append(s.charAt(i));
            int remaining = len - i - 1;
            if (remaining > 0 && remaining % 3 == 0) {
                out.append(thousandsSep);
            }
        }

        if (negative) out.insert(0, "-");
        out.append(symbol);

        return out.toString();
    }

    public static String formatMoney(long value) {
        PlotropolisCoins p = PlotropolisCoins.getInstance();
        String sym = p.getConfig().getString("currency.money.symbol", "€");
        String sep = p.getConfig().getString("currency.money.format-thousands", ".");
        return format(value, sym, sep);
    }

    public static String formatKristalle(long value) {
        PlotropolisCoins p = PlotropolisCoins.getInstance();
        String sym = p.getConfig().getString("currency.kristalle.symbol", "✦");
        String sep = p.getConfig().getString("currency.kristalle.format-thousands", ".");
        return format(value, sym, sep);
    }
}
