package de.plotropolis.clan.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.plotropolis.clan.PlotropolisClan;

import java.sql.Connection;
import java.sql.SQLException;

public final class MySqlManager {

    private final PlotropolisClan plugin;
    private HikariDataSource dataSource;

    public MySqlManager(PlotropolisClan plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        String host = plugin.getConfig().getString("mysql.host");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String database = plugin.getConfig().getString("mysql.database");
        String username = plugin.getConfig().getString("mysql.username");
        String password = plugin.getConfig().getString("mysql.password");
        boolean useSSL = plugin.getConfig().getBoolean("mysql.useSSL", false);
        int poolSize = plugin.getConfig().getInt("mysql.poolSize", 10);

        if (host == null || host.isEmpty()) {
            throw new IllegalStateException("mysql.host fehlt in der config.yml");
        }
        if (database == null || database.isEmpty()) {
            throw new IllegalStateException("mysql.database fehlt in der config.yml");
        }

        String jdbcUrl =
                "jdbc:mysql://" + host + ":" + port + "/" + database +
                        "?useSSL=" + useSSL +
                        "&allowPublicKeyRetrieval=true" +
                        "&useUnicode=true" +
                        "&characterEncoding=utf8";

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(username);
        cfg.setPassword(password);

        cfg.setPoolName("PlotropolisClan-Hikari");
        cfg.setMaximumPoolSize(Math.max(2, poolSize));
        cfg.setMinimumIdle(Math.min(2, poolSize));
        cfg.setConnectionTimeout(10_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);

        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        cfg.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(cfg);

        plugin.getLogger().info("MySQL verbunden (" + host + ":" + port + "/" + database + ")");
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HikariDataSource ist nicht initialisiert oder geschlossen");
        }
        return dataSource.getConnection();
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void shutdown() {
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (Exception ignored) {}
            dataSource = null;
        }
    }
}
