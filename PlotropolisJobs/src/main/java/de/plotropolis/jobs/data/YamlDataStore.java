package de.plotropolis.jobs.data;

import de.plotropolis.jobs.PlotropolisJobs;
import de.plotropolis.jobs.jobs.JobType;
import de.plotropolis.jobs.jobs.Progression;
import de.plotropolis.jobs.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class YamlDataStore implements JobsDataStore {

    private final PlotropolisJobs plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public YamlDataStore(PlotropolisJobs plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
        if (!folder.exists()) folder.mkdirs();
    }


    @Override
    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
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
    public void addXpAndCoins(UUID uuid, JobType type, long xpAdd, double coinsAdd) {
        PlayerData data = get(uuid);
        PlayerData.JobStats js = data.job(type);

        int max = Progression.maxLevel(plugin);

        if (js.level < max) {
            js.xp += Math.max(0L, xpAdd);

            while (js.level < max) {
                long req = Progression.requiredForNext(plugin, js.level);
                if (js.xp >= req) {
                    js.xp -= req;
                    js.level++;
                } else break;
            }
        }

        if (coinsAdd > 0.0) {
            js.pendingCoins += coinsAdd;
            js.sessionCoins += coinsAdd;
        }
    }

    @Override
    public void flushPayouts(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        PlayerData data = get(uuid);

        long payoutTotal = 0L;

        for (JobType t : JobType.values()) {
            double pending = data.job(t).pendingCoins;
            if (pending <= 0.0) continue;

            long pay = (long) Math.floor(pending);
            if (pay <= 0) continue;

            payoutTotal += pay;
            data.job(t).pendingCoins = pending - pay;
        }

        if (payoutTotal <= 0L) return;

        plugin.economy().addMoney(uuid, payoutTotal);

        for (JobType t : JobType.values()) {
            data.job(t).sessionCoins = 0.0;
        }

        if (plugin.bossBars() != null) {
            plugin.bossBars().onPayout(player, payoutTotal);
        }

        player.sendMessage(ColorUtil.c(
                plugin.prefix() + "Auszahlung: &a+" + plugin.economy().format(payoutTotal)
        ));
    }

    @Override
    public void save(UUID uuid) {
        saveInternal(uuid);
    }

    @Override
    public void unload(UUID uuid) {
        save(uuid);
        cache.remove(uuid);
    }

    @Override
    public void shutdown() {
        saveAllOnline();
        cache.clear();
    }


    public void loadAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            get(p.getUniqueId());
        }
    }

    public void saveAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            save(p.getUniqueId());
        }
    }

    private File file(UUID uuid) {
        return new File(folder, uuid.toString() + ".yml");
    }

    private PlayerData load(UUID uuid) {
        PlayerData data = new PlayerData();
        File f = file(uuid);
        if (!f.exists()) return data;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);

        for (JobType t : JobType.values()) {
            String base = "jobs." + t.id() + ".";

            data.job(t).level = Math.max(1, yml.getInt(base + "level", 1));
            data.job(t).xp = Math.max(0L, yml.getLong(base + "xp", 0L));

            double pending = yml.contains(base + "pendingCoins")
                    ? yml.getDouble(base + "pendingCoins", 0.0)
                    : (double) yml.getLong(base + "pendingCoins", 0L);

            double session = yml.contains(base + "sessionCoins")
                    ? yml.getDouble(base + "sessionCoins", 0.0)
                    : (double) yml.getLong(base + "sessionCoins", 0L);

            data.job(t).pendingCoins = Math.max(0.0, pending);
            data.job(t).sessionCoins = Math.max(0.0, session);
        }

        return data;
    }

    private void saveInternal(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;

        YamlConfiguration yml = new YamlConfiguration();
        for (JobType t : JobType.values()) {
            String base = "jobs." + t.id() + ".";
            yml.set(base + "level", data.job(t).level);
            yml.set(base + "xp", data.job(t).xp);
            yml.set(base + "pendingCoins", data.job(t).pendingCoins);
            yml.set(base + "sessionCoins", data.job(t).sessionCoins);
        }

        try {
            yml.save(file(uuid));
        } catch (Exception e) {
            plugin.getLogger().warning("Konnte PlayerData nicht speichern: " + uuid + " -> " + e.getMessage());
        }
    }

    public void flushPayoutsAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            flushPayouts(p.getUniqueId());
        }
    }
}
