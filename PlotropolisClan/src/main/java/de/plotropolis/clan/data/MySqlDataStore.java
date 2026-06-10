package de.plotropolis.clan.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.model.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MySqlDataStore implements DataStore {

    private final PlotropolisClan plugin;
    private final HikariDataSource ds;

    private final Map<String, Clan> clansByTag = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerClan = new ConcurrentHashMap<>();

    private volatile Set<String> knownTags = ConcurrentHashMap.newKeySet();

    private volatile boolean dirty = false;

    private final String tClans;
    private final String tMembers;
    private final String tRoles;
    private final String tInvites;
    private final String tActivity;

    public MySqlDataStore(PlotropolisClan plugin) {
        this.plugin = plugin;

        String base = plugin.getConfig().getString("mysql.table", "plotropolis_clan").trim();
        if (base.isEmpty()) base = "plotropolis_clan";

        this.tClans = base;
        this.tMembers = base + "_members";
        this.tRoles = base + "_roles";
        this.tInvites = base + "_invites";
        this.tActivity = base + "_activity";

        this.ds = createDataSource(plugin);
        setupSchema();
    }

    @Override
    public boolean isMySql() {
        return true;
    }

    @Override
    public void markDirty() {
        dirty = true;
    }

    @Override
    public Map<String, Clan> clansByTag() {
        return clansByTag;
    }

    @Override
    public Map<UUID, String> playerClanTag() {
        return playerClan;
    }

    @Override
    public void load() {
        Map<String, Clan> newClans = new HashMap<>();
        Map<UUID, String> newPlayerClan = new HashMap<>();
        Set<String> newKnownTags = new HashSet<>();

        try (Connection con = ds.getConnection()) {
            con.setReadOnly(true);

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT tag_lower, tag, name, owner_uuid, verified, public_join, bank_money, kills, created_server, created_at " +
                            "FROM " + tClans
            )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tagLower = rs.getString("tag_lower");
                        if (tagLower == null) continue;
                        tagLower = tagLower.toLowerCase(Locale.ROOT);

                        String tag = rs.getString("tag");
                        String name = rs.getString("name");
                        UUID owner = uuidOrNull(rs.getString("owner_uuid"));
                        if (owner == null) continue;

                        String createdServer = rs.getString("created_server");
                        if (createdServer == null || createdServer.trim().isEmpty()) createdServer = "default";

                        Clan clan = new Clan(tag, name, owner, createdServer);
                        clan.verified(rs.getBoolean("verified"));
                        clan.publicJoin(rs.getBoolean("public_join"));
                        clan.bankMoney(rs.getLong("bank_money"));
                        clan.kills(rs.getLong("kills"));

                        clan.roles().clear();
                        clan.members().clear();
                        clan.invites().clear();
                        clan.activitySecondsByDay().clear();

                        newClans.put(tagLower, clan);
                        newKnownTags.add(tagLower);
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT tag_lower, role_key, name, weight, perms FROM " + tRoles
            )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tagLower = lower(rs.getString("tag_lower"));
                        Clan clan = newClans.get(tagLower);
                        if (clan == null) continue;

                        String roleKey = lower(rs.getString("role_key"));
                        if (roleKey.isEmpty()) continue;

                        String roleName = rs.getString("name");
                        int weight = rs.getInt("weight");
                        String permsStr = rs.getString("perms");

                        ClanRole role = new ClanRole(roleName == null ? roleKey : roleName, weight);

                        if (permsStr != null && !permsStr.isEmpty()) {
                            String[] parts = permsStr.split(",");
                            for (String p : parts) {
                                String x = p.trim();
                                if (x.isEmpty()) continue;
                                try {
                                    role.set(ClanPermission.valueOf(x), true);
                                } catch (Exception ignored) {}
                            }
                        }

                        clan.roles().put(roleKey, role);
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT tag_lower, player_uuid, role_name FROM " + tMembers
            )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tagLower = lower(rs.getString("tag_lower"));
                        Clan clan = newClans.get(tagLower);
                        if (clan == null) continue;

                        UUID u = uuidOrNull(rs.getString("player_uuid"));
                        if (u == null) continue;

                        String roleName = rs.getString("role_name");
                        if (roleName == null || roleName.isEmpty()) roleName = "Mitglied";

                        clan.members().put(u, roleName);
                        newPlayerClan.put(u, tagLower);
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT tag_lower, player_uuid, invited_by, created_at FROM " + tInvites
            )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tagLower = lower(rs.getString("tag_lower"));
                        Clan clan = newClans.get(tagLower);
                        if (clan == null) continue;

                        UUID u = uuidOrNull(rs.getString("player_uuid"));
                        UUID by = uuidOrNull(rs.getString("invited_by"));
                        if (u == null || by == null) continue;

                        long at = rs.getLong("created_at");
                        clan.invites().put(u, new Invite(clan.tag(), by, at));
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT tag_lower, day_epoch, seconds FROM " + tActivity
            )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tagLower = lower(rs.getString("tag_lower"));
                        Clan clan = newClans.get(tagLower);
                        if (clan == null) continue;

                        long day = rs.getLong("day_epoch");
                        long sec = rs.getLong("seconds");
                        clan.activitySecondsByDay().put(day, sec);
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL load() Fehler: " + e.getMessage());
            return;
        }

        clansByTag.clear();
        clansByTag.putAll(newClans);

        playerClan.clear();
        playerClan.putAll(newPlayerClan);

        Set<String> k = ConcurrentHashMap.newKeySet();
        k.addAll(newKnownTags);
        knownTags = k;

        dirty = false;
    }

    @Override
    public void save() {

        Map<String, Clan> snapshotClans = new HashMap<>(clansByTag);

        Set<String> current = new HashSet<>(snapshotClans.keySet());
        Set<String> removed = new HashSet<>(knownTags);
        removed.removeAll(current);

        try (Connection con = ds.getConnection()) {
            con.setAutoCommit(false);

            for (String tagLower : removed) {
                deleteClan(con, tagLower);
            }

            for (Map.Entry<String, Clan> entry : snapshotClans.entrySet()) {
                String tagLower = lower(entry.getKey());
                Clan clan = entry.getValue();
                if (clan == null) continue;

                upsertClan(con, tagLower, clan);

                replaceRoles(con, tagLower, clan);
                replaceMembers(con, tagLower, clan);
                replaceInvites(con, tagLower, clan);
                replaceActivity(con, tagLower, clan);
            }

            con.commit();

            Set<String> k = ConcurrentHashMap.newKeySet();
            k.addAll(current);
            knownTags = k;

            dirty = false;

        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL save() Fehler: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            ds.close();
        } catch (Throwable ignored) {}
    }


    private void setupSchema() {
        try (Connection con = ds.getConnection();
             Statement st = con.createStatement()) {

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tClans + " (" +
                            "tag_lower VARCHAR(16) PRIMARY KEY," +
                            "tag VARCHAR(16) NOT NULL," +
                            "name VARCHAR(64) NOT NULL," +
                            "owner_uuid CHAR(36) NOT NULL," +
                            "verified TINYINT(1) NOT NULL DEFAULT 0," +
                            "public_join TINYINT(1) NOT NULL DEFAULT 0," +
                            "bank_money BIGINT NOT NULL DEFAULT 0," +
                            "kills BIGINT NOT NULL DEFAULT 0," +
                            "created_server VARCHAR(64) NOT NULL," +
                            "created_at BIGINT NOT NULL" +
                            ")"
            );

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tMembers + " (" +
                            "tag_lower VARCHAR(16) NOT NULL," +
                            "player_uuid CHAR(36) NOT NULL," +
                            "role_name VARCHAR(32) NOT NULL," +
                            "PRIMARY KEY (tag_lower, player_uuid)" +
                            ")"
            );

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tRoles + " (" +
                            "tag_lower VARCHAR(16) NOT NULL," +
                            "role_key VARCHAR(32) NOT NULL," +
                            "name VARCHAR(32) NOT NULL," +
                            "weight INT NOT NULL," +
                            "perms TEXT NOT NULL," +
                            "PRIMARY KEY (tag_lower, role_key)" +
                            ")"
            );

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tInvites + " (" +
                            "tag_lower VARCHAR(16) NOT NULL," +
                            "player_uuid CHAR(36) NOT NULL," +
                            "invited_by CHAR(36) NOT NULL," +
                            "created_at BIGINT NOT NULL," +
                            "PRIMARY KEY (tag_lower, player_uuid)" +
                            ")"
            );

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tActivity + " (" +
                            "tag_lower VARCHAR(16) NOT NULL," +
                            "day_epoch BIGINT NOT NULL," +
                            "seconds BIGINT NOT NULL," +
                            "PRIMARY KEY (tag_lower, day_epoch)" +
                            ")"
            );

        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL Schema Setup Fehler: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    private void upsertClan(Connection con, String tagLower, Clan clan) throws SQLException {
        String sql =
                "INSERT INTO " + tClans + " (tag_lower, tag, name, owner_uuid, verified, public_join, bank_money, kills, created_server, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "tag=VALUES(tag), name=VALUES(name), owner_uuid=VALUES(owner_uuid), verified=VALUES(verified), public_join=VALUES(public_join), " +
                        "bank_money=VALUES(bank_money), kills=VALUES(kills), created_server=VALUES(created_server)";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tagLower);
            ps.setString(2, safe(clan.tag()));
            ps.setString(3, safe(clan.name()));
            ps.setString(4, clan.owner().toString());
            ps.setBoolean(5, clan.verified());
            ps.setBoolean(6, clan.publicJoin());
            ps.setLong(7, clan.bankMoney());
            ps.setLong(8, clan.kills());
            ps.setString(9, safe(clan.createdServer()));
            ps.setLong(10, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void replaceMembers(Connection con, String tagLower, Clan clan) throws SQLException {
        try (PreparedStatement del = con.prepareStatement("DELETE FROM " + tMembers + " WHERE tag_lower=?")) {
            del.setString(1, tagLower);
            del.executeUpdate();
        }
        if (clan.members().isEmpty()) return;

        try (PreparedStatement ins = con.prepareStatement(
                "INSERT INTO " + tMembers + " (tag_lower, player_uuid, role_name) VALUES (?,?,?)"
        )) {
            for (var e : clan.members().entrySet()) {
                if (e.getKey() == null) continue;
                ins.setString(1, tagLower);
                ins.setString(2, e.getKey().toString());
                ins.setString(3, safe(e.getValue() == null ? "Mitglied" : e.getValue()));
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private void replaceRoles(Connection con, String tagLower, Clan clan) throws SQLException {
        try (PreparedStatement del = con.prepareStatement("DELETE FROM " + tRoles + " WHERE tag_lower=?")) {
            del.setString(1, tagLower);
            del.executeUpdate();
        }
        if (clan.roles().isEmpty()) return;

        try (PreparedStatement ins = con.prepareStatement(
                "INSERT INTO " + tRoles + " (tag_lower, role_key, name, weight, perms) VALUES (?,?,?,?,?)"
        )) {
            for (var e : clan.roles().entrySet()) {
                String roleKey = lower(e.getKey());
                ClanRole r = e.getValue();
                if (roleKey.isEmpty() || r == null) continue;

                String perms = permsToCsv(r);

                ins.setString(1, tagLower);
                ins.setString(2, roleKey);
                ins.setString(3, safe(r.name()));
                ins.setInt(4, r.weight());
                ins.setString(5, perms);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private void replaceInvites(Connection con, String tagLower, Clan clan) throws SQLException {
        try (PreparedStatement del = con.prepareStatement("DELETE FROM " + tInvites + " WHERE tag_lower=?")) {
            del.setString(1, tagLower);
            del.executeUpdate();
        }
        if (clan.invites().isEmpty()) return;

        try (PreparedStatement ins = con.prepareStatement(
                "INSERT INTO " + tInvites + " (tag_lower, player_uuid, invited_by, created_at) VALUES (?,?,?,?)"
        )) {
            for (var e : clan.invites().entrySet()) {
                UUID u = e.getKey();
                Invite inv = e.getValue();
                if (u == null || inv == null || inv.invitedBy() == null) continue;

                ins.setString(1, tagLower);
                ins.setString(2, u.toString());
                ins.setString(3, inv.invitedBy().toString());
                ins.setLong(4, inv.createdAt());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private void replaceActivity(Connection con, String tagLower, Clan clan) throws SQLException {
        try (PreparedStatement del = con.prepareStatement("DELETE FROM " + tActivity + " WHERE tag_lower=?")) {
            del.setString(1, tagLower);
            del.executeUpdate();
        }
        if (clan.activitySecondsByDay().isEmpty()) return;

        try (PreparedStatement ins = con.prepareStatement(
                "INSERT INTO " + tActivity + " (tag_lower, day_epoch, seconds) VALUES (?,?,?)"
        )) {
            for (var e : clan.activitySecondsByDay().entrySet()) {
                ins.setString(1, tagLower);
                ins.setLong(2, e.getKey());
                ins.setLong(3, e.getValue());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private void deleteClan(Connection con, String tagLower) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + tMembers + " WHERE tag_lower=?")) {
            ps.setString(1, tagLower);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + tRoles + " WHERE tag_lower=?")) {
            ps.setString(1, tagLower);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + tInvites + " WHERE tag_lower=?")) {
            ps.setString(1, tagLower);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + tActivity + " WHERE tag_lower=?")) {
            ps.setString(1, tagLower);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + tClans + " WHERE tag_lower=?")) {
            ps.setString(1, tagLower);
            ps.executeUpdate();
        }
    }


    private static HikariDataSource createDataSource(PlotropolisClan plugin) {
        String host = plugin.getConfig().getString("mysql.host", "");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String db = plugin.getConfig().getString("mysql.database", "");
        String user = plugin.getConfig().getString("mysql.username", "");
        String pass = plugin.getConfig().getString("mysql.password", "");
        boolean ssl = plugin.getConfig().getBoolean("mysql.useSSL", false);
        int poolSize = plugin.getConfig().getInt("mysql.poolSize", 10);

        if (host == null || host.trim().isEmpty()) {
            throw new IllegalStateException("mysql.host fehlt in config.yml");
        }

        String jdbc = "jdbc:mysql://" + host + ":" + port + "/" + db +
                "?useSSL=" + ssl +
                "&allowPublicKeyRetrieval=true" +
                "&useUnicode=true&characterEncoding=utf8";

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbc);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(Math.max(2, poolSize));
        cfg.setPoolName("PlotropolisClan-MySQL");

        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(cfg);
    }


    private static String lower(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static UUID uuidOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    private static String permsToCsv(ClanRole role) {
        List<String> out = new ArrayList<>();
        for (ClanPermission p : role.perms()) out.add(p.name());
        return String.join(",", out);
    }
}
