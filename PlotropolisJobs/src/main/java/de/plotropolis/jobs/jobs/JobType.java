package de.plotropolis.jobs.jobs;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum JobType {

    MINER("miner", "plotropolisjobs.job.miner"),
    WOODCUTTER("woodcutter", "plotropolisjobs.job.woodcutter"),
    HUNTER("hunter", "plotropolisjobs.job.hunter"),
    DIGGER("digger", "plotropolisjobs.job.digger"),
    FISHER("fisher", "plotropolisjobs.job.fisher");

    private static final Map<String, JobType> BY_ID = new HashMap<>();

    static {
        for (JobType t : values()) {
            BY_ID.put(t.id.toLowerCase(Locale.ROOT), t);
        }
    }

    private final String id;
    private final String permission;

    JobType(String id, String permission) {
        this.id = id;
        this.permission = permission;
    }

    public String id() {
        return id;
    }

    public String permission() {
        return permission;
    }

    public static JobType byId(String id) {
        if (id == null) return null;
        String key = id.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) return null;
        return BY_ID.get(key);
    }
}
