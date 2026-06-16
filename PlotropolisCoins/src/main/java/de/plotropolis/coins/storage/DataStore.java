package de.plotropolis.coins.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DataStore {

    public enum StorageType { FILE, MYSQL }

    private final JavaPlugin plugin;

    private File file;
    private YamlConfiguration cfg;

    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastLoadRequest = new ConcurrentHashMap<>();
    private long loadCooldownMs = 5000;

    private HikariDataSource ds;
    private StorageType type;
    private String table;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public StorageType getType() {
        return type;
    }

    public void init() {
        String raw = plugin.getConfig().getString("storage.type", "FILE").toUpperCase();
        try {
            this.type = StorageType.valueOf(raw);
        } catch (Exception e) {
            this.type = StorageType.FILE;
        }

        this.loadCooldownMs = plugin.getConfig().getLong("storage.cache-load-cooldown-ms", 5000);

        if (type == StorageType.MYSQL) {
            setupMySql();
            ensureTable();
            plugin.getLogger().info("DataStore: MYSQL aktiv (atomic).");
        } else {
            setupFile();
            plugin.getLogger().info("DataStore: FILE aktiv.");
        }

        for (var p : Bukkit.getOnlinePlayers()) {
            requestLoad(p.getUniqueId(), p.getName() == null ? "unknown" : p.getName());
        }
    }

    public void shutdown() {
        if (type == StorageType.FILE) {
            saveAll();
        } else {
            if (ds != null) ds.close();
        }
    }

    public PlayerData getCached(UUID uuid) {
        return cache.get(uuid);
    }

    public void requestLoad(UUID uuid, String name) {
        if (uuid == null) return;
        long now = System.currentTimeMillis();
        long last = lastLoadRequest.getOrDefault(uuid, 0L);
        if ((now - last) < loadCooldownMs) return;
        lastLoadRequest.put(uuid, now);

        if (type == StorageType.FILE) {
            cache.computeIfAbsent(uuid, id -> getFile(id));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerData pd = getMySqlBlocking(uuid, name);
            cache.put(uuid, pd);
        });
    }

    public void requestSave(UUID uuid, String name) {
        if (type != StorageType.MYSQL) return;
        PlayerData pd = cache.get(uuid);
        if (pd == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ensureRow(uuid, name);
            String sql = "UPDATE `" + table + "` SET name=?, money=?, bank=?, kristalle=? WHERE uuid=?";
            execUpdate(sql, ps -> {
                ps.setString(1, safeName(name));
                ps.setLong(2, pd.getMoney());
                ps.setLong(3, pd.getBank());
                ps.setLong(4, pd.getKristalle());
                ps.setString(5, uuid.toString());
            });
        });
    }


    public PlayerData get(UUID uuid, String name) {
        if (type == StorageType.MYSQL) return getMySqlBlocking(uuid, name);
        return getFile(uuid);
    }

    public PlayerData get(OfflinePlayer p) {
        String name = p.getName() == null ? "unknown" : p.getName();
        return get(p.getUniqueId(), name);
    }


    public void addMoney(UUID uuid, String name, long amount) {
        if (amount <= 0) return;

        cache.compute(uuid, (id, pd) -> {
            if (pd == null) pd = new PlayerData(id);
            pd.setMoney(pd.getMoney() + amount);
            return pd;
        });

        if (type == StorageType.MYSQL) {
            ensureRow(uuid, name);
            String sql = "UPDATE `" + table + "` SET name=?, money = money + ? WHERE uuid=?";
            execUpdate(sql, ps -> {
                ps.setString(1, safeName(name));
                ps.setLong(2, amount);
                ps.setString(3, uuid.toString());
            });
            return;
        }

        PlayerData pd = getFile(uuid);
        pd.setMoney(pd.getMoney() + amount);
    }

    public void removeMoney(UUID uuid, String name, long amount) {
        if (amount <= 0) return;

        cache.compute(uuid, (id, pd) -> {
            if (pd == null) pd = new PlayerData(id);
            pd.setMoney(Math.max(0, pd.getMoney() - amount));
            return pd;
        });

        if (type == StorageType.MYSQL) {
            ensureRow(uuid, name);
            String sql = "UPDATE `" + table + "` SET name=?, money = GREATEST(money - ?, 0) WHERE uuid=?";
            execUpdate(sql, ps -> {
                ps.setString(1, safeName(name));
                ps.setLong(2, amount);
                ps.setString(3, uuid.toString());
            });
            return;
        }

        PlayerData pd = getFile(uuid);
        pd.setMoney(Math.max(0, pd.getMoney() - amount));
    }

    public boolean takeMoney(UUID uuid, String name, long amount) {
        if (amount <= 0) return true;

        if (type != StorageType.MYSQL) {
            PlayerData pd = cache.computeIfAbsent(uuid, PlayerData::new);
            if (pd.getMoney() < amount) return false;
            pd.setMoney(pd.getMoney() - amount);
            return true;
        }

        ensureRow(uuid, name);

        String sql = "UPDATE `" + table + "` " +
                "SET name=?, money = money - ? " +
                "WHERE uuid=? AND money >= ?";

        int updated = execUpdate(sql, ps -> {
            ps.setString(1, safeName(name));
            ps.setLong(2, amount);
            ps.setString(3, uuid.toString());
            ps.setLong(4, amount);
        });

        if (updated == 1) {
            cache.compute(uuid, (id, pd) -> {
                if (pd == null) pd = new PlayerData(id);
                pd.setMoney(Math.max(0, pd.getMoney() - amount));
                return pd;
            });
            return true;
        }
        return false;
    }

    public void setMoney(UUID uuid, String name, long amount) {
        if (amount < 0) amount = 0;
        long finalAmount = amount;

        cache.compute(uuid, (id, pd) -> {
            if (pd == null) pd = new PlayerData(id);
            pd.setMoney(finalAmount);
            return pd;
        });

        if (type == StorageType.MYSQL) {
            ensureRow(uuid, name);
            String sql = "UPDATE `" + table + "` SET name=?, money=? WHERE uuid=?";
            execUpdate(sql, ps -> {
                ps.setString(1, safeName(name));
                ps.setLong(2, finalAmount);
                ps.setString(3, uuid.toString());
            });
            return;
        }

        PlayerData pd = getFile(uuid);
        pd.setMoney(finalAmount);
    }

    public void addKristalle(UUID uuid, String name, long amount) {
        if (amount <= 0) return;

        cache.compute(uuid, (id, pd) -> {
            if (pd == null) pd = new PlayerData(id);
            pd.setKristalle(pd.getKristalle() + amount);
            return pd;
        });

        if (type == StorageType.MYSQL) {
            ensureRow(uuid, name);
            String sql = "UPDATE `" + table + "` SET name=?, kristalle = kristalle + ? WHERE uuid=?";
            execUpdate(sql, ps -> {
                ps.setString(1, safeName(name));
                ps.setLong(2, amount);
                ps.setString(3, uuid.toString());
            });
            return;
        }

        PlayerData pd = getFile(uuid);
        pd.setKristalle(pd.getKristalle() + amount);
    }

    public void removeKristalle(UUID uuid, String name, long amount) {
        if (amount <= 0) return;

        cache.compute(uuid, (id, pd) -> {
            if (pd == null) pd = new PlayerData(id);
            pd.setKristalle(Math.max(0, pd.getKristalle() - amount));
            return pd;
        });

        if (type == StorageType.MYSQL) {
            ensureRow(uuid, name);
            String sql = "UPDATE `" + table + "` SET name=?, kristalle = GREATEST(kristalle - ?, 0) WHERE uuid=?";
            execUpdate(sql, ps -> {
                ps.setString(1, safeName(name));
                ps.setLong(2, amount);
                ps.setString(3, uuid.toString());
            });
            return;
        }

        PlayerData pd = getFile(uuid);
        pd.setKristalle(Math.max(0, pd.getKristalle() - amount));
    }


    public boolean transferMoney(UUID from, String fromName, UUID to, String toName, long amount) {
        if (amount <= 0) return false;

        if (type != StorageType.MYSQL) {
            PlayerData a = cache.computeIfAbsent(from, PlayerData::new);
            PlayerData b = cache.computeIfAbsent(to, PlayerData::new);

            if (a.getMoney() < amount) return false;
            a.setMoney(a.getMoney() - amount);
            b.setMoney(b.getMoney() + amount);
            return true;
        }

        ensureRow(from, fromName);
        ensureRow(to, toName);

        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);

            String dec = "UPDATE `" + table + "` SET name=?, money = money - ? WHERE uuid=? AND money >= ?";
            try (PreparedStatement ps = c.prepareStatement(dec)) {
                ps.setString(1, safeName(fromName));
                ps.setLong(2, amount);
                ps.setString(3, from.toString());
                ps.setLong(4, amount);

                int updated = ps.executeUpdate();
                if (updated != 1) {
                    c.rollback();
                    return false;
                }
            }

            String inc = "UPDATE `" + table + "` SET name=?, money = money + ? WHERE uuid=?";
            try (PreparedStatement ps = c.prepareStatement(inc)) {
                ps.setString(1, safeName(toName));
                ps.setLong(2, amount);
                ps.setString(3, to.toString());
                ps.executeUpdate();
            }

            c.commit();

            cache.compute(from, (id, pd) -> {
                if (pd == null) pd = new PlayerData(id);
                pd.setMoney(Math.max(0, pd.getMoney() - amount));
                return pd;
            });
            cache.compute(to, (id, pd) -> {
                if (pd == null) pd = new PlayerData(id);
                pd.setMoney(pd.getMoney() + amount);
                return pd;
            });

            return true;

        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL transferMoney Fehler: " + e.getMessage());
            return false;
        }
    }

    public boolean bankDeposit(UUID uuid, String name, long amount) {
        if (amount <= 0) return false;

        if (type != StorageType.MYSQL) {
            PlayerData pd = cache.computeIfAbsent(uuid, PlayerData::new);
            if (pd.getMoney() < amount) return false;
            pd.setMoney(pd.getMoney() - amount);
            pd.setBank(pd.getBank() + amount);
            return true;
        }

        ensureRow(uuid, name);

        String sql = "UPDATE `" + table + "` " +
                "SET name=?, money = money - ?, bank = bank + ? " +
                "WHERE uuid=? AND money >= ?";

        int updated = execUpdate(sql, ps -> {
            ps.setString(1, safeName(name));
            ps.setLong(2, amount);
            ps.setLong(3, amount);
            ps.setString(4, uuid.toString());
            ps.setLong(5, amount);
        });

        if (updated == 1) {
            cache.compute(uuid, (id, pd) -> {
                if (pd == null) pd = new PlayerData(id);
                pd.setMoney(Math.max(0, pd.getMoney() - amount));
                pd.setBank(pd.getBank() + amount);
                return pd;
            });
            return true;
        }
        return false;
    }

    public boolean bankWithdraw(UUID uuid, String name, long amount) {
        if (amount <= 0) return false;

        if (type != StorageType.MYSQL) {
            PlayerData pd = cache.computeIfAbsent(uuid, PlayerData::new);
            if (pd.getBank() < amount) return false;
            pd.setBank(pd.getBank() - amount);
            pd.setMoney(pd.getMoney() + amount);
            return true;
        }

        ensureRow(uuid, name);

        String sql = "UPDATE `" + table + "` " +
                "SET name=?, bank = bank - ?, money = money + ? " +
                "WHERE uuid=? AND bank >= ?";

        int updated = execUpdate(sql, ps -> {
            ps.setString(1, safeName(name));
            ps.setLong(2, amount);
            ps.setLong(3, amount);
            ps.setString(4, uuid.toString());
            ps.setLong(5, amount);
        });

        if (updated == 1) {
            cache.compute(uuid, (id, pd) -> {
                if (pd == null) pd = new PlayerData(id);
                pd.setBank(Math.max(0, pd.getBank() - amount));
                pd.setMoney(pd.getMoney() + amount);
                return pd;
            });
            return true;
        }
        return false;
    }

    public void setKristalle(UUID uuid, String name, long amount) {
        if (amount < 0) amount = 0;
        long finalAmount = amount;

        cache.compute(uuid, (id, pd) -> {
            if (pd == null) pd = new PlayerData(id);
            pd.setKristalle(finalAmount);
            return pd;
        });

        if (type == StorageType.MYSQL) {
            ensureRow(uuid, name);
            String sql = "UPDATE `" + table + "` SET name=?, kristalle=? WHERE uuid=?";
            execUpdate(sql, ps -> {
                ps.setString(1, safeName(name));
                ps.setLong(2, finalAmount);
                ps.setString(3, uuid.toString());
            });
            return;
        }

        PlayerData pd = getFile(uuid);
        pd.setKristalle(finalAmount);
    }


    private void setupFile() {
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
        saveFileIfMissing();
        for (var p : Bukkit.getOnlinePlayers()) getFile(p.getUniqueId());
    }

    private PlayerData getFile(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            PlayerData pd = new PlayerData(id);
            String base = "players." + id;
            pd.setMoney(cfg.getLong(base + ".money", 0));
            pd.setBank(cfg.getLong(base + ".bank", 0));
            pd.setKristalle(cfg.getLong(base + ".kristalle", 0));
            return pd;
        });
    }

    public void saveAll() {
        if (type != StorageType.FILE) return;
        for (Map.Entry<UUID, PlayerData> e : cache.entrySet()) {
            UUID id = e.getKey();
            PlayerData pd = e.getValue();
            String base = "players." + id;
            cfg.set(base + ".money", pd.getMoney());
            cfg.set(base + ".bank", pd.getBank());
            cfg.set(base + ".kristalle", pd.getKristalle());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Konnte data.yml nicht speichern: " + ex.getMessage());
        }
    }

    private void saveFileIfMissing() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
                cfg.save(file);
            } catch (IOException ex) {
                plugin.getLogger().severe("Konnte data.yml nicht erstellen: " + ex.getMessage());
            }
        }
    }


    private void setupMySql() {
        String host = plugin.getConfig().getString("mysql.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String db = plugin.getConfig().getString("mysql.database", "plotropolis");
        String user = plugin.getConfig().getString("mysql.username", "root");
        String pass = plugin.getConfig().getString("mysql.password", "");
        boolean ssl = plugin.getConfig().getBoolean("mysql.useSSL", false);
        int pool = plugin.getConfig().getInt("mysql.poolSize", 10);

        this.table = plugin.getConfig().getString("mysql.table", "plotropolis_coins");

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=" + ssl
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC"
                + "&useUnicode=true&characterEncoding=utf8";

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername(user);
        hc.setPassword(pass);
        hc.setMaximumPoolSize(Math.max(2, pool));
        hc.setPoolName("PlotropolisCoins-Hikari");

        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hc.addDataSourceProperty("useServerPrepStmts", "true");

        this.ds = new HikariDataSource(hc);
    }

    private void ensureTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
                + "name VARCHAR(16) NOT NULL,"
                + "money BIGINT NOT NULL DEFAULT 0,"
                + "bank BIGINT NOT NULL DEFAULT 0,"
                + "kristalle BIGINT NOT NULL DEFAULT 0,"
                + "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ");";
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL ensureTable Fehler: " + e.getMessage());
        }
    }

    private PlayerData getMySqlBlocking(UUID uuid, String name) {
        ensureRow(uuid, name);

        String select = "SELECT money, bank, kristalle FROM `" + table + "` WHERE uuid=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(select)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerData pd = new PlayerData(uuid);
                    pd.setMoney(rs.getLong("money"));
                    pd.setBank(rs.getLong("bank"));
                    pd.setKristalle(rs.getLong("kristalle"));
                    return pd;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL get Fehler: " + e.getMessage());
        }
        return new PlayerData(uuid);
    }

    private void ensureRow(UUID uuid, String name) {
        String ins = "INSERT IGNORE INTO `" + table + "` (uuid, name, money, bank, kristalle) VALUES (?,?,?,?,?)";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(ins)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, safeName(name));
            ps.setLong(3, 0L);
            ps.setLong(4, 0L);
            ps.setLong(5, 0L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL ensureRow Fehler: " + e.getMessage());
        }
    }


    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private int execUpdate(String sql, Binder binder) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL update Fehler: " + e.getMessage());
            return 0;
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "unknown";
        if (name.length() > 16) return name.substring(0, 16);
        return name;
    }
}
