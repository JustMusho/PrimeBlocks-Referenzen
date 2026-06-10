package de.plotropolis.jobs.economy;

import java.util.UUID;

public interface EconomyHook {
    boolean isReady();
    void addMoney(UUID uuid, long amount);
    String format(long amount);
}
