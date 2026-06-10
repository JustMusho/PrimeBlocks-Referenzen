package de.plotropolis.clan.placeholders;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.model.Clan;
import de.plotropolis.clan.util.ColorUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ClanPlaceholders extends PlaceholderExpansion {

    private final PlotropolisClan plugin;

    public ClanPlaceholders(PlotropolisClan plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "plotropolisclan"; }
    @Override public @NotNull String getAuthor() { return "Plotropolis"; }
    @Override public @NotNull String getVersion() { return "1.1.0"; }

    @Override
    public String onPlaceholderRequest(Player p, String params) {
        if (p == null) return "";

        Clan clan = plugin.data().getClanOf(p.getUniqueId());
        if (clan == null) return "";

        switch (params.toLowerCase()) {

            case "tag":
                return clan.tag();

            case "name":
                return clan.name();

            case "bank":
                return plugin.economy().formatMoney(clan.bankMoney());

            case "verified":
                return clan.verified()
                        ? ColorUtil.c("&a✔")
                        : ColorUtil.c("&c✘");

            case "role":
                return clan.members().getOrDefault(p.getUniqueId(), "");

            case "tag_brackets":
                return ColorUtil.c("&#feca39[&#f15f06" + clan.tag() + "&#feca39]");

            case "verified_tag":
                if (!clan.verified()) return "";
                return ColorUtil.c(" &f[&b" + clan.tag() + "&f]");

            default:
                return "";
        }
    }
}
