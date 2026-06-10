package de.plotropolis.jobs.jobs;

import de.plotropolis.jobs.PlotropolisJobs;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public final class JobConfig {

    private final PlotropolisJobs plugin;

    public JobConfig(PlotropolisJobs plugin) {
        this.plugin = plugin;
    }

    public boolean jobEnabled(JobType type) {
        return plugin.getConfig().getBoolean("jobs." + type.id() + ".enabled", true);
    }

    public int xpForBlock(JobType type, Material mat) {
        String path = "jobs." + type.id() + ".xp." + mat.name();
        if (plugin.getConfig().isInt(path)) return plugin.getConfig().getInt(path);
        int def = plugin.getConfig().getInt("jobs." + type.id() + ".default-xp", 0);
        return def;
    }

    public int xpForMob(JobType type, EntityType e) {
        String path = "jobs." + type.id() + ".xp." + e.name();
        if (plugin.getConfig().isInt(path)) return plugin.getConfig().getInt(path);
        return 0;
    }

    public int fisherXp() {
        return plugin.getConfig().getInt("jobs.fisher.xp-per-catch", 30);
    }

    public boolean ignoreCreative() {
        return plugin.getConfig().getBoolean("settings.ignore-creative", true);
    }

    public boolean ignoreSilkTouch() {
        return plugin.getConfig().getBoolean("settings.ignore-silk-touch", false);
    }
}
