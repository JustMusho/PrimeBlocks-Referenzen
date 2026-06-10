package de.plotropolis.clan.data.sql;

import java.sql.*;
import java.util.Locale;
import java.util.UUID;

public final class Sql {

    private Sql() {}

    public static String table(String base, String suffix) {
        base = base == null ? "" : base.trim();
        if (base.isEmpty()) base = "plotropolis_clan";
        suffix = suffix == null ? "" : suffix.trim();
        if (suffix.isEmpty()) return base;
        if (suffix.startsWith("_")) suffix = suffix.substring(1);
        return base + "_" + suffix;
    }

    public static String key(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }

    public static String s(String s) {
        return s == null ? "" : s;
    }

    public static UUID uuidOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    public static UUID uuid(ResultSet rs, String col) throws SQLException {
        return uuidOrNull(rs.getString(col));
    }

    public static void setUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        ps.setString(index, uuid == null ? null : uuid.toString());
    }

    public static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) {}
    }

    public static void rollbackQuietly(Connection con) {
        if (con == null) return;
        try { con.rollback(); } catch (Exception ignored) {}
    }

    public static void setAutoCommitQuietly(Connection con, boolean value) {
        if (con == null) return;
        try { con.setAutoCommit(value); } catch (Exception ignored) {}
    }

    public static void setStringSafe(PreparedStatement ps, int index, String value) throws SQLException {
        ps.setString(index, value == null ? "" : value);
    }

    public static boolean tableExists(Connection con, String table) throws SQLException {
        DatabaseMetaData md = con.getMetaData();
        try (ResultSet rs = md.getTables(con.getCatalog(), null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
