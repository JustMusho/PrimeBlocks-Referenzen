package de.plotropolis.clan.gui;

import de.plotropolis.clan.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class GuiStyle {

    private GuiStyle(){}

    public static void border(org.bukkit.inventory.Inventory inv) {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name("&8 ")
                .build();

        int size = inv.getSize();
        int rows = size / 9;

        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = size - 9; i < size; i++) inv.setItem(i, filler);

        for (int r = 1; r < rows - 1; r++) {
            inv.setItem(r * 9, filler);
            inv.setItem(r * 9 + 8, filler);
        }
    }

    public static ItemStack buttonBack() {
        return new ItemBuilder(Material.ARROW)
                .name("&#2fd9ff&lZurück")
                .loreLine("&7Klicke um zurückzugehen")
                .build();
    }

    public static ItemStack buttonClose() {
        return new ItemBuilder(Material.BARRIER)
                .name("&c&lSchließen")
                .loreLine("&7GUI schließen")
                .build();
    }

    public static ItemStack buttonNext() {
        return new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a&lWeiter »")
                .loreLine("&7Nächste Seite")
                .build();
    }

    public static ItemStack buttonPrev() {
        return new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c&l« Zurück")
                .loreLine("&7Vorherige Seite")
                .build();
    }
}
