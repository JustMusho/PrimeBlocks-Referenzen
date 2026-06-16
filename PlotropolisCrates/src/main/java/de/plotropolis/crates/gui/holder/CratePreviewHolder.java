package de.plotropolis.crates.gui.holder;

import de.plotropolis.crates.crates.Crate;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record CratePreviewHolder(Crate crate, int page) implements InventoryHolder {
    @Override public Inventory getInventory() { return null; }
}
