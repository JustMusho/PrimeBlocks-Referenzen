package de.plotropolis.clan.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;
    private final List<String> lore = new ArrayList<>();

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String displayName) {
        meta.setDisplayName(ColorUtil.c(displayName));
        return this;
    }

    public ItemBuilder loreLine(String line) {
        lore.add(ColorUtil.c(line));
        return this;
    }

    public ItemBuilder loreLines(List<String> lines) {
        for (String l : lines) lore.add(ColorUtil.c(l));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
