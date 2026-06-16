package de.plotropolis.chestshop.shop;

import org.bukkit.Location;

import java.util.Objects;
import java.util.UUID;

public record Shop(
        Location signLoc,
        Location chestLoc,
        UUID ownerUuid,
        String ownerName,
        int amount,
        long buyPrice,
        long sellPrice,
        String itemKey
) {
    public Shop {
        Objects.requireNonNull(signLoc, "signLoc");
        Objects.requireNonNull(chestLoc, "chestLoc");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(itemKey, "itemKey");

        ownerName = ownerName.trim();
        itemKey = itemKey.trim();

        if (ownerName.isEmpty()) ownerName = "Unknown";
        if (itemKey.isEmpty()) itemKey = "minecraft:air";

        if (amount < 1) amount = 1;

        if (buyPrice < 0) buyPrice = 0;
        if (sellPrice < 0) sellPrice = 0;
    }
}