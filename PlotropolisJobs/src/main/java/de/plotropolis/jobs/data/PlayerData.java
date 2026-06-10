package de.plotropolis.jobs.data;

import de.plotropolis.jobs.jobs.JobType;

import java.util.EnumMap;
import java.util.Map;

public final class PlayerData {

    public static final class JobStats {

        public int level = 1;
        public long xp = 0L;


        public double pendingCoins = 0.0;

        public double sessionCoins = 0.0;
    }

    private final Map<JobType, JobStats> stats = new EnumMap<>(JobType.class);

    public PlayerData() {
        for (JobType t : JobType.values()) {
            stats.put(t, new JobStats());
        }
    }

    public JobStats job(JobType type) {
        return stats.get(type);
    }

    public Map<JobType, JobStats> all() {
        return stats;
    }
}
