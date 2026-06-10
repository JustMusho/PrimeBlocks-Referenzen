package de.plotropolis.jobs.jobs;

import de.plotropolis.jobs.PlotropolisJobs;

public final class Progression {

    private Progression() {}


    public static int maxLevel(PlotropolisJobs plugin) {
        return plugin.getConfig().getInt("progression.max-level", 200);
    }

    public static long xpStep(PlotropolisJobs plugin) {
        return plugin.getConfig().getLong("progression.xp-step", 1000L);
    }

    public static long requiredForNext(PlotropolisJobs plugin, int currentLevel) {
        return (long) currentLevel * xpStep(plugin);
    }


    public static double coinsPerAction(int level) {
        if (level < 1) level = 1;
        return 0.5 + ((level - 1) / 3) * 0.5;
    }
}
