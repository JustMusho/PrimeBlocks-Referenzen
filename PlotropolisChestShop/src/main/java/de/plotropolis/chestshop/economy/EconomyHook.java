package de.plotropolis.chestshop.economy;

import java.util.UUID;

public interface EconomyHook {
    boolean isReady();
    long balance(UUID uuid);
    boolean withdraw(UUID uuid, long amount);
    void deposit(UUID uuid, long amount);
}
