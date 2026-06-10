package de.plotropolis.jobs.bossbar;

import de.plotropolis.jobs.PlotropolisJobs;
import de.plotropolis.jobs.data.PlayerData;
import de.plotropolis.jobs.jobs.JobType;
import de.plotropolis.jobs.jobs.Progression;
import de.plotropolis.jobs.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossBarService {

    private final PlotropolisJobs plugin;

    private static final class BarState {
        BossBar bar;
        JobType lastJob;
        long lastActivityMillis;

        long payoutFreezeUntilMillis;

        long lastUpdateMillis;

        String lastTitle;
        double lastProgress = -1.0;
        BarColor lastColor;
        BarStyle lastStyle;
    }

    private final Map<UUID, BarState> bars = new ConcurrentHashMap<>();

    public BossBarService(PlotropolisJobs plugin) {
        this.plugin = plugin;
    }

    private long minUpdateIntervalMs() {
        return Math.max(50L, plugin.getConfig().getLong("settings.bossbar.min-update-interval-ms", 120L));
    }

    public void showOrUpdate(Player p, JobType job) {
        if (!plugin.getConfig().getBoolean("settings.bossbar.enabled", true)) return;
        if (!p.hasPermission("plotropolisjobs.bossbar")) return;

        long now = System.currentTimeMillis();

        BarState st = bars.computeIfAbsent(p.getUniqueId(), k -> {
            BarState s = new BarState();
            s.bar = Bukkit.createBossBar("", color(), style());
            s.bar.addPlayer(p);
            s.bar.setVisible(true);
            s.lastActivityMillis = now;
            s.lastUpdateMillis = 0L;
            s.lastColor = null;
            s.lastStyle = null;
            return s;
        });

        st.lastJob = job;
        st.lastActivityMillis = now;

        if (now < st.payoutFreezeUntilMillis) return;

        if (now - st.lastUpdateMillis < minUpdateIntervalMs()) return;
        st.lastUpdateMillis = now;

        update(p, job, st);
    }

    public void onPayout(Player p, long totalPaid) {
        if (!plugin.getConfig().getBoolean("settings.bossbar.enabled", true)) return;
        if (!p.hasPermission("plotropolisjobs.bossbar")) return;
        if (totalPaid <= 0) return;

        long now = System.currentTimeMillis();

        BarState st = bars.computeIfAbsent(p.getUniqueId(), k -> {
            BarState s = new BarState();
            s.bar = Bukkit.createBossBar("", color(), style());
            s.bar.addPlayer(p);
            s.bar.setVisible(true);
            s.lastActivityMillis = now;
            s.lastUpdateMillis = 0L;
            return s;
        });

        st.lastActivityMillis = now;

        st.payoutFreezeUntilMillis = now + 1250L;

        BarColor c = color();
        BarStyle s = style();
        if (st.lastColor != c) { st.bar.setColor(c); st.lastColor = c; }
        if (st.lastStyle != s) { st.bar.setStyle(s); st.lastStyle = s; }

        if (st.lastProgress != 1.0) { st.bar.setProgress(1.0); st.lastProgress = 1.0; }

        String title = ColorUtil.c("&bAuszahlung &8| &a+"
                + plugin.economy().format(totalPaid)
                + " &8| &7Counter reset...");
        if (!title.equals(st.lastTitle)) {
            st.bar.setTitle(title);
            st.lastTitle = title;
        }

        st.bar.setVisible(true);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BarState st2 = bars.get(p.getUniqueId());
            if (st2 == null) return;

            st2.payoutFreezeUntilMillis = 0;

            if (!p.isOnline()) {
                removeBar(p);
                return;
            }

            long removeAfter = plugin.getConfig()
                    .getLong("settings.bossbar.remove-after-seconds", 5) * 1000L;
            long now2 = System.currentTimeMillis();
            if (now2 - st2.lastActivityMillis >= removeAfter) {
                removeBar(p);
                return;
            }

            if (st2.lastJob != null) {
                st2.lastUpdateMillis = 0L;
                showOrUpdate(p, st2.lastJob);
            }
        }, 25L);
    }

    public void remove(Player p) {
        removeBar(p);
    }

    private void update(Player p, JobType job, BarState st) {
        BossBar bar = st.bar;

        PlayerData data = plugin.data().get(p.getUniqueId());
        PlayerData.JobStats js = data.job(job);

        long need = Progression.requiredForNext(plugin, js.level);
        long xp = js.xp;

        String jobName = switch (job) {
            case MINER -> "&bMiner";
            case WOODCUTTER -> "&aHolzfäller";
            case HUNTER -> "&cJäger";
            case DIGGER -> "&eGräber";
            case FISHER -> "&9Fischer";
        };

        String title = ColorUtil.c(jobName
                + " &8| &fLvl: &b" + js.level
                + " &8| &fXP: &b" + xp + "&7/&b" + need
                + " &8| &fAuszahlung: &a+" + formatCoins(js.sessionCoins));

        if (!title.equals(st.lastTitle)) {
            bar.setTitle(title);
            st.lastTitle = title;
        }

        double progress = (need <= 0) ? 1.0 : Math.min(1.0, Math.max(0.0, (double) xp / (double) need));
        if (Math.abs(progress - st.lastProgress) > 0.0001) {
            bar.setProgress(progress);
            st.lastProgress = progress;
        }

        BarColor c = color();
        BarStyle s = style();
        if (st.lastColor != c) { bar.setColor(c); st.lastColor = c; }
        if (st.lastStyle != s) { bar.setStyle(s); st.lastStyle = s; }

        bar.setVisible(true);
    }

    private BarStyle style() {
        String s = plugin.getConfig().getString("settings.bossbar.style", "SOLID");
        try { return BarStyle.valueOf(s.toUpperCase()); }
        catch (Exception e) { return BarStyle.SOLID; }
    }

    private BarColor color() {
        String s = plugin.getConfig().getString("settings.bossbar.color", "BLUE");
        try { return BarColor.valueOf(s.toUpperCase()); }
        catch (Exception e) { return BarColor.BLUE; }
    }

    private String formatCoins(double v) {
        if (v <= 0.0) return "0";
        double rounded = Math.rint(v);
        if (Math.abs(v - rounded) < 1e-9) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.US, "%.1f", v);
    }

    private void removeBar(Player p) {
        BarState st = bars.remove(p.getUniqueId());
        if (st == null) return;

        try {
            st.bar.removeAll();
        } catch (Exception ignored) {}
    }

    public void tickCleanup() {
        long removeAfter = plugin.getConfig().getLong("settings.bossbar.remove-after-seconds", 5) * 1000L;
        long now = System.currentTimeMillis();
        long minInterval = minUpdateIntervalMs();

        for (Player p : Bukkit.getOnlinePlayers()) {
            BarState st = bars.get(p.getUniqueId());
            if (st == null) continue;

            if (now < st.payoutFreezeUntilMillis) continue;

            if (now - st.lastActivityMillis >= removeAfter) {
                removeBar(p);
                continue;
            }

            if (st.lastJob != null && now - st.lastUpdateMillis >= minInterval) {
                st.lastUpdateMillis = now;
                update(p, st.lastJob, st);
            }
        }

        bars.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    public void clearAll() {
        for (UUID id : bars.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) removeBar(p);
        }
        bars.clear();
    }
}
