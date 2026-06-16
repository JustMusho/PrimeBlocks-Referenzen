package de.plotropolis.crates.gui.holder;

import de.plotropolis.crates.crates.Crate;
import de.plotropolis.crates.crates.CrateItem;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record ItemEditHolder(Crate crate, CrateItem item) implements InventoryHolder {
    @Override public Inventory getInventory() { return null; }
}
