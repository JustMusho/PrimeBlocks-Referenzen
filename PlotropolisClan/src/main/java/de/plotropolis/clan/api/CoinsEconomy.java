package de.plotropolis.clan.api;

import java.util.UUID;

public interface CoinsEconomy {
    long getMoney(UUID player);
    boolean withdrawMoney(UUID player, long amount);
    void depositMoney(UUID player, long amount);

    String formatMoney(long amount);
}
