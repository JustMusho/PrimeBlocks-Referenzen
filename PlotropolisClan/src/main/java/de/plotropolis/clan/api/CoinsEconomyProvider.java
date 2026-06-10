package de.plotropolis.clan.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoinsEconomyProvider {

    private CoinsEconomyProvider() {}

    public static CoinsEconomy hook(JavaPlugin plugin) {
        var reg = Bukkit.getServicesManager().getRegistration(CoinsEconomy.class);
        if (reg != null && reg.getProvider() != null) return reg.getProvider();
        return null;
    }
}
