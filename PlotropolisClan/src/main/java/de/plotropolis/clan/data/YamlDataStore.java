package de.plotropolis.clan.data;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.model.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class YamlDataStore implements DataStore {

    private final PlotropolisClan plugin;
    private final File file;
    private FileConfiguration cfg;

    private final Map<String, Clan> clansByTag = new HashMap<>();
    private final Map<UUID, String> playerClan = new HashMap<>();

    private boolean dirty = false;

    public YamlDataStore(PlotropolisClan plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    @Override
    public void load() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            cfg = YamlConfiguration.loadConfiguration(file);

            clansByTag.clear();
            playerClan.clear();

            var sec = cfg.getConfigurationSection("clans");
            if (sec != null) {
                for (String tagLowerRaw : sec.getKeys(false)) {
                    String tagLower = tagLowerRaw.toLowerCase(Locale.ROOT);

                    var csec = sec.getConfigurationSection(tagLowerRaw);
                    if (csec == null) continue;

                    String tag = csec.getString("tag", tagLowerRaw);
                    String name = csec.getString("name", tagLowerRaw);
                    String ownerStr = csec.getString("owner");
                    if (ownerStr == null || ownerStr.isEmpty()) continue;

                    UUID owner = UUID.fromString(ownerStr);

                    String createdServer = csec.getString("createdServer", "default");

                    Clan clan = new Clan(tag, name, owner, createdServer);
                    clan.verified(csec.getBoolean("verified", false));
                    clan.publicJoin(csec.getBoolean("publicJoin", false));
                    clan.bankMoney(csec.getLong("bankMoney", 0L));
                    clan.kills(csec.getLong("kills", 0L));

                    var asec = csec.getConfigurationSection("activitySecondsByDay");
                    if (asec != null) {
                        for (String day : asec.getKeys(false)) {
                            try {
                                long dayEpoch = Long.parseLong(day);
                                long seconds = asec.getLong(day, 0);
                                clan.activitySecondsByDay().put(dayEpoch, seconds);
                            } catch (Exception ignored) {}
                        }
                    }

                    clan.roles().clear();
                    var rsec = csec.getConfigurationSection("roles");
                    if (rsec != null) {
                        for (String rKeyRaw : rsec.getKeys(false)) {
                            String rKey = rKeyRaw.toLowerCase(Locale.ROOT);

                            var rr = rsec.getConfigurationSection(rKeyRaw);
                            if (rr == null) continue;

                            String rName = rr.getString("name", rKeyRaw);
                            int weight = rr.getInt("weight", 10);

                            ClanRole role = new ClanRole(rName, weight);

                            List<String> perms = rr.getStringList("perms");
                            for (String p : perms) {
                                try { role.set(ClanPermission.valueOf(p), true); } catch (Exception ignored) {}
                            }

                            clan.roles().put(rKey, role);
                        }
                    }

                    clan.members().clear();
                    var msec = csec.getConfigurationSection("members");
                    if (msec != null) {
                        for (String uuid : msec.getKeys(false)) {
                            try {
                                UUID u = UUID.fromString(uuid);
                                String roleName = msec.getString(uuid, "Mitglied");
                                clan.members().put(u, roleName);
                            } catch (Exception ignored) {}
                        }
                    }

                    clan.invites().clear();
                    var isec = csec.getConfigurationSection("invites");
                    if (isec != null) {
                        for (String uuid : isec.getKeys(false)) {
                            try {
                                UUID u = UUID.fromString(uuid);
                                String t = isec.getString(uuid + ".tag", clan.tag());
                                String by = isec.getString(uuid + ".by", owner.toString());
                                long at = isec.getLong(uuid + ".at", System.currentTimeMillis());
                                clan.invites().put(u, new Invite(t, UUID.fromString(by), at));
                            } catch (Exception ignored) {}
                        }
                    }

                    clansByTag.put(tagLower, clan);

                    for (UUID u : clan.members().keySet()) {
                        playerClan.put(u, tagLower);
                    }
                }
            }

            dirty = false;

        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Laden von data.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void save() {
        if (!dirty && cfg != null) {
        }

        try {
            if (cfg == null) cfg = new YamlConfiguration();
            cfg.set("clans", null);

            for (var entry : clansByTag.entrySet()) {
                String tagLower = entry.getKey().toLowerCase(Locale.ROOT);
                Clan clan = entry.getValue();

                String base = "clans." + tagLower + ".";
                cfg.set(base + "tag", clan.tag());
                cfg.set(base + "name", clan.name());
                cfg.set(base + "owner", clan.owner().toString());
                cfg.set(base + "verified", clan.verified());
                cfg.set(base + "publicJoin", clan.publicJoin());
                cfg.set(base + "bankMoney", clan.bankMoney());
                cfg.set(base + "kills", clan.kills());

                cfg.set(base + "createdServer", clan.createdServer());

                cfg.set(base + "activitySecondsByDay", null);
                for (var a : clan.activitySecondsByDay().entrySet()) {
                    cfg.set(base + "activitySecondsByDay." + a.getKey(), a.getValue());
                }

                cfg.set(base + "roles", null);
                for (var r : clan.roles().entrySet()) {
                    ClanRole role = r.getValue();
                    String rBase = base + "roles." + r.getKey().toLowerCase(Locale.ROOT) + ".";
                    cfg.set(rBase + "name", role.name());
                    cfg.set(rBase + "weight", role.weight());

                    List<String> perms = new ArrayList<>();
                    for (ClanPermission p : role.perms()) perms.add(p.name());
                    cfg.set(rBase + "perms", perms);
                }

                cfg.set(base + "members", null);
                for (var m : clan.members().entrySet()) {
                    cfg.set(base + "members." + m.getKey().toString(), m.getValue());
                }

                cfg.set(base + "invites", null);
                for (var inv : clan.invites().entrySet()) {
                    String iBase = base + "invites." + inv.getKey().toString();
                    cfg.set(iBase + ".tag", inv.getValue().tag());
                    cfg.set(iBase + ".by", inv.getValue().invitedBy().toString());
                    cfg.set(iBase + ".at", inv.getValue().createdAt());
                }
            }

            cfg.save(file);
            dirty = false;

        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Speichern von data.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void markDirty() {
        dirty = true;
    }

    @Override
    public boolean isMySql() {
        return false;
    }

    @Override
    public void close() {
    }

    @Override public Map<String, Clan> clansByTag() { return clansByTag; }
    @Override public Map<UUID, String> playerClanTag() { return playerClan; }
}
