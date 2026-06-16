package de.plotropolis.coins.placeholders;

import de.plotropolis.coins.PlotropolisCoins;
import de.plotropolis.coins.storage.PlayerData;
import de.plotropolis.coins.util.MoneyFormat;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PlotropolisPlaceholderExpansion extends PlaceholderExpansion {

    private final PlotropolisCoins plugin;

    public PlotropolisPlaceholderExpansion(PlotropolisCoins plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "plotropolis";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Plotropolis";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "0";
        UUID uuid = player.getUniqueId();
        if (uuid == null) return "0";

        String name = player.getName() != null ? player.getName() : "unknown";

        PlayerData data = plugin.getDataStore().getCached(uuid);

        if (data == null) {
            plugin.getDataStore().requestLoad(uuid, name);
            return "0";
        }

        return switch (params.toLowerCase()) {
            case "money" -> MoneyFormat.formatMoney(data.getMoney());
            case "bank" -> MoneyFormat.formatMoney(data.getBank());
            case "kristalle" -> MoneyFormat.formatKristalle(data.getKristalle());

            case "money_raw" -> String.valueOf(data.getMoney());
            case "bank_raw" -> String.valueOf(data.getBank());
            case "kristalle_raw" -> String.valueOf(data.getKristalle());

            default -> null;
        };
    }
}
