package de.plotropolis.jobs.data;

import de.plotropolis.jobs.jobs.JobType;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface JobsDataStore {

    PlayerData get(UUID uuid);

    int getLevel(UUID uuid, JobType job);

    long getXp(UUID uuid, JobType job);

    double getPendingCoins(UUID uuid, JobType job);

    void addXpAndCoins(UUID uuid, JobType job, long xp, double coins);

    void flushPayouts(UUID uuid);

    void save(UUID uuid);

    void unload(UUID uuid);

    void shutdown();

    default PlayerData get(Player player) {
        return get(player.getUniqueId());
    }

    default int getLevel(Player player, JobType job) {
        return getLevel(player.getUniqueId(), job);
    }

    default long getXp(Player player, JobType job) {
        return getXp(player.getUniqueId(), job);
    }

    default double getPendingCoins(Player player, JobType job) {
        return getPendingCoins(player.getUniqueId(), job);
    }

    default void addXpAndCoins(Player player, JobType job, long xp, double coins) {
        addXpAndCoins(player.getUniqueId(), job, xp, coins);
    }

    default void flushPayouts(Player player) {
        flushPayouts(player.getUniqueId());
    }

    default void save(Player player) {
        save(player.getUniqueId());
    }

    default void unload(Player player) {
        unload(player.getUniqueId());
    }
}
