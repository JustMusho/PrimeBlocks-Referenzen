package de.plotropolis.clan.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public interface GuiScreen {
    Inventory inventory();
    void onClick(InventoryClickEvent e, Player player);
}
