package de.plotropolis.coins.storage;

import java.util.UUID;

public final class PlayerData {

    private final UUID uuid;
    private long money;
    private long bank;
    private long kristalle;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getMoney() {
        return money;
    }

    public void setMoney(long money) {
        this.money = Math.max(0, money);
    }

    public long getBank() {
        return bank;
    }

    public void setBank(long bank) {
        this.bank = Math.max(0, bank);
    }

    public long getKristalle() {
        return kristalle;
    }

    public void setKristalle(long kristalle) {
        this.kristalle = Math.max(0, kristalle);
    }
}
