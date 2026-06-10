package de.plotropolis.jobs.data;

import de.plotropolis.jobs.PlotropolisJobs;
import de.plotropolis.jobs.jobs.JobType;
import de.plotropolis.jobs.jobs.Progression;
import de.plotropolis.jobs.mysql.MySql;
import de.plotropolis.jobs.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MySqlDataStore implements JobsDataStore {

    private final PlotropolisJobs plugin;
    private final DataSource ds;
    private final String table;

    private final ConcurrentMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, EnumMap<JobType, PendingUpdate>> pending = new ConcurrentHashMap<>();

    private int flushTaskId = -1;

    private static final class PendingUpdate {
        double addCoins;
        int level;
        long xp;
        boolean dirty;
    }

    public MySqlDataStore(PlotropolisJobs plugin, MySql mysql) {
        this.plugin = plugin;
        this.ds = mysql.dataSource();
        this.table = mysql.table();

        int intervalTicks = Math.max(10, plugin.getConfig().getInt("mysql.flush-interval-ticks", 20));
        this.flushTaskId = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, this::flushPendingToDbSafe, intervalTicks, intervalTicks)
                .getTaskId();
    }

    private void ensureRow(UUID uuid, JobType job) {
        String sql = "INSERT IGNORE INTO " + table +
                " (uuid, job, level, xp, pending_coins, session_coins) VALUES (?, ?, 1, 0, 0, 0)";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job.id());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public PlayerData get(UUID uuid) {
        if (uuid == null) return new PlayerData();

        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;

        for (JobType t : JobType.values()) ensureRow(uuid, t);

        PlayerData data = new PlayerData();

        String sql = "SELECT job, level, xp, pending_coins, session_coins FROM " + table + " WHERE uuid=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JobType type = JobType.byId(rs.getString("job"));
                    if (type == null) continue;

                    PlayerData.JobStats js = data.job(type);
                    js.level = Math.max(1, rs.getInt("level"));
                    js.xp = Math.max(0L, rs.getLong("xp"));
                    js.pendingCoins = Math.max(0.0, rs.getDouble("pending_coins"));
                    js.sessionCoins = Math.max(0.0, rs.getDouble("session_coins"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        cache.put(uuid, data);
        return data;
    }

    @Override
    public int getLevel(UUID uuid, JobType job) {
        return get(uuid).job(job).level;
    }

    @Override
    public long getXp(UUID uuid, JobType job) {
        return get(uuid).job(job).xp;
    }

    @Override
    public double getPendingCoins(UUID uuid, JobType job) {
        return get(uuid).job(job).pendingCoins;
    }

    @Override
    public void addXpAndCoins(UUID uuid, JobType job, long xpAdd, double coinsAdd) {
        if (uuid == null || job == null) return;


        PlayerData data = get(uuid);
        PlayerData.JobStats js = data.job(job);

        double addCoins = Math.max(0.0, coinsAdd);

        if (addCoins > 0.0) {
            js.pendingCoins += addCoins;
            js.sessionCoins += addCoins;
        }

        if (xpAdd > 0) {
            int max = Progression.maxLevel(plugin);
            if (js.level < max) {
                js.xp += xpAdd;

                while (js.level < max) {
                    long req = Progression.requiredForNext(plugin, js.level);
                    if (req <= 0) break;

                    if (js.xp >= req) {
                        js.xp -= req;
                        js.level++;
                    } else break;
                }
            }
        }

        EnumMap<JobType, PendingUpdate> map = pending.computeIfAbsent(uuid, k -> new EnumMap<>(JobType.class));
        PendingUpdate pu = map.computeIfAbsent(job, k -> new PendingUpdate());
        pu.addCoins += addCoins;
        pu.level = js.level;
        pu.xp = js.xp;
        pu.dirty = true;
    }

    private void flushPendingToDbSafe() {
        try {
            flushPendingToDb();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void flushPendingToDb() {
        if (pending.isEmpty()) return;

        Map<UUID, EnumMap<JobType, PendingUpdate>> snap = new ConcurrentHashMap<>(pending);
        pending.clear();

        String sql = "UPDATE " + table + " SET " +
                "pending_coins = pending_coins + ?, " +
                "session_coins = session_coins + ?, " +
                "level = ?, xp = ? " +
                "WHERE uuid=? AND job=?";

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            for (var entry : snap.entrySet()) {
                UUID uuid = entry.getKey();

                for (var e : entry.getValue().entrySet()) {
                    JobType job = e.getKey();
                    PendingUpdate pu = e.getValue();
                    if (pu == null || !pu.dirty) continue;

                    ps.setDouble(1, pu.addCoins);
                    ps.setDouble(2, pu.addCoins);
                    ps.setInt(3, pu.level);
                    ps.setLong(4, pu.xp);
                    ps.setString(5, uuid.toString());
                    ps.setString(6, job.id());
                    ps.addBatch();

                    pu.dirty = false;
                    pu.addCoins = 0.0;
                }
            }

            ps.executeBatch();

        } catch (Exception ex) {
            ex.printStackTrace();

            for (var entry : snap.entrySet()) {
                UUID uuid = entry.getKey();
                EnumMap<JobType, PendingUpdate> map = pending.computeIfAbsent(uuid, k -> new EnumMap<>(JobType.class));

                for (var e : entry.getValue().entrySet()) {
                    JobType job = e.getKey();
                    PendingUpdate old = e.getValue();
                    if (old == null) continue;

                    PendingUpdate pu = map.computeIfAbsent(job, k -> new PendingUpdate());
                    pu.addCoins += old.addCoins;
                    pu.level = Math.max(pu.level, old.level);
                    pu.xp = Math.max(pu.xp, old.xp);
                    pu.dirty = true;
                }
            }
        }
    }

    @Override
    public void flushPayouts(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        PlayerData data = get(uuid);

        long payoutTotal = 0L;
        EnumMap<JobType, Double> restMap = new EnumMap<>(JobType.class);

        for (JobType job : JobType.values()) {
            PlayerData.JobStats js = data.job(job);

            double pendingCoins = js.pendingCoins;
            long pay = (pendingCoins <= 0.0) ? 0L : (long) Math.floor(pendingCoins);

            if (pay > 0L) {
                payoutTotal += pay;
                double rest = pendingCoins - pay;

                js.pendingCoins = rest;
                js.sessionCoins = 0.0;
                restMap.put(job, rest);
            } else {
                js.sessionCoins = 0.0;
                restMap.put(job, js.pendingCoins);
            }
        }

        if (payoutTotal <= 0L) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> resetSessionCoinsAsync(uuid));
            return;
        }

        plugin.economy().addMoney(uuid, payoutTotal);

        if (plugin.bossBars() != null) {
            plugin.bossBars().onPayout(player, payoutTotal);
        }

        player.sendMessage(ColorUtil.c(
                plugin.prefix() + "Auszahlung: &a+" + plugin.economy().format(payoutTotal)
        ));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "UPDATE " + table + " SET pending_coins=?, session_coins=0 WHERE uuid=? AND job=?";
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {

                for (JobType job : JobType.values()) {
                    double rest = restMap.getOrDefault(job, 0.0);

                    ps.setDouble(1, Math.max(0.0, rest));
                    ps.setString(2, uuid.toString());
                    ps.setString(3, job.id());
                    ps.addBatch();
                }

                ps.executeBatch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void resetSessionCoinsAsync(UUID uuid) {
        String sql = "UPDATE " + table + " SET session_coins=0 WHERE uuid=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void save(UUID uuid) {
    }

    @Override
    public void unload(UUID uuid) {
        if (uuid == null) return;
        cache.remove(uuid);
        pending.remove(uuid);
    }

    @Override
    public void shutdown() {
        flushPendingToDbSafe();

        if (flushTaskId != -1) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }

        cache.clear();
        pending.clear();
    }
}
