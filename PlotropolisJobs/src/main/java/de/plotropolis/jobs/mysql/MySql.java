package de.plotropolis.jobs.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

public final class MySql {

    private final HikariDataSource ds;
    private final String table;

    public MySql(ConfigurationSection cfg) {
        this.table = cfg.getString("table");

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://" + cfg.getString("host") + ":" + cfg.getInt("port")
                + "/" + cfg.getString("database") + "?useSSL=" + cfg.getBoolean("useSSL"));
        hc.setUsername(cfg.getString("username"));
        hc.setPassword(cfg.getString("password"));
        hc.setMaximumPoolSize(cfg.getInt("poolSize", 10));

        this.ds = new HikariDataSource(hc);
        initTable();
    }

    private void initTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS %s (
              uuid CHAR(36) NOT NULL,
              job VARCHAR(32) NOT NULL,
              level INT NOT NULL,
              xp BIGINT NOT NULL,
              pending_coins DOUBLE NOT NULL,
              session_coins DOUBLE NOT NULL,
              PRIMARY KEY (uuid, job)
            );
        """.formatted(table);

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (Exception e) {
            throw new RuntimeException("MySQL init failed", e);
        }
    }

    public DataSource dataSource() {
        return ds;
    }

    public String table() {
        return table;
    }

    public void close() {
        ds.close();
    }
}
