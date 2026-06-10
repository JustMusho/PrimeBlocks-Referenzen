package de.plotropolis.clan.migrate;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.data.DataStore;
import de.plotropolis.clan.data.MySqlDataStore;
import de.plotropolis.clan.data.YamlDataStore;
import de.plotropolis.clan.model.Clan;
import org.bukkit.Bukkit;

import java.util.Locale;

public final class MigrationService {

    private final PlotropolisClan plugin;

    public MigrationService(PlotropolisClan plugin) {
        this.plugin = plugin;
    }

    public void migrateIfNeeded(MySqlDataStore mySqlStore) {
        if (mySqlStore == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                mySqlStore.load();

                if (!mySqlStore.clansByTag().isEmpty()) {
                    plugin.getLogger().info("Migration: MySQL hat bereits Clans -> skip.");
                    return;
                }

                YamlDataStore yaml = new YamlDataStore(plugin);
                yaml.load();

                if (yaml.clansByTag().isEmpty()) {
                    plugin.getLogger().info("Migration: data.yml hat keine Clans -> skip.");
                    return;
                }

                String serverId = plugin.getConfig().getString("network.server-id", "default");
                if (serverId == null || serverId.trim().isEmpty()) serverId = "default";
                serverId = serverId.trim();

                mySqlStore.clansByTag().clear();
                mySqlStore.playerClanTag().clear();

                for (var e : yaml.clansByTag().entrySet()) {
                    String tagLower = e.getKey().toLowerCase(Locale.ROOT);
                    Clan c = e.getValue();
                    if (c == null) continue;

                    Clan fixed = c;
                    if (c.createdServer() == null || c.createdServer().trim().isEmpty() || "default".equalsIgnoreCase(c.createdServer())) {
                        fixed = new Clan(c.tag(), c.name(), c.owner(), serverId);
                        fixed.verified(c.verified());
                        fixed.publicJoin(c.publicJoin());
                        fixed.bankMoney(c.bankMoney());
                        fixed.kills(c.kills());

                        fixed.roles().clear();
                        fixed.roles().putAll(c.roles());

                        fixed.members().clear();
                        fixed.members().putAll(c.members());

                        fixed.invites().clear();
                        fixed.invites().putAll(c.invites());

                        fixed.activitySecondsByDay().clear();
                        fixed.activitySecondsByDay().putAll(c.activitySecondsByDay());
                    }

                    mySqlStore.clansByTag().put(tagLower, fixed);

                    for (var m : fixed.members().keySet()) {
                        mySqlStore.playerClanTag().put(m, tagLower);
                    }
                }

                mySqlStore.save();

                plugin.getLogger().info("Migration: YAML -> MySQL erfolgreich (" + yaml.clansByTag().size() + " Clans).");

            } catch (Throwable t) {
                plugin.getLogger().severe("Migration Fehler: " + t.getMessage());
                t.printStackTrace();
            }
        });
    }

    public void migrateIfNeeded(DataStore store) {
        if (store instanceof MySqlDataStore ms) migrateIfNeeded(ms);
    }
}
