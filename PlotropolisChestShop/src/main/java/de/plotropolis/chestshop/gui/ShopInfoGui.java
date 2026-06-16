package de.plotropolis.chestshop.gui;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import de.plotropolis.chestshop.shop.Shop;
import de.plotropolis.chestshop.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ShopInfoGui {

    private ShopInfoGui() {}

    public static void open(PlotropolisChestShopPlugin plugin, Player p, Shop s) {
        ColorUtil c = plugin.color();

        String title = c.c(plugin.getConfig().getString("gui.title", "SHOPINFO"));
        Inventory inv = Bukkit.createInventory(null, 27, title);

        Material fillerMat = Material.matchMaterial(plugin.getConfig().getString("gui.filler.material", "GRAY_STAINED_GLASS_PANE"));
        if (fillerMat == null) fillerMat = Material.GRAY_STAINED_GLASS_PANE;

        // Filler
        ItemStack filler = new ItemStack(fillerMat);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(c.c(plugin.getConfig().getString("gui.filler.name", "&7")));
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // ✅ Display-Item aus der Kiste holen (damit DisplayName + Lore original bleiben)
        ItemStack display = findPrototypeFromChest(plugin, s);
        if (display == null) display = buildFallbackItem(plugin, s);

        int stock = countStock(plugin, s);

        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            // ✅ Name NICHT überschreiben wenn Item bereits einen Custom-Name hat
            // Wenn kein DisplayName vorhanden -> schöner Vanilla/Nexo Name
            if (!meta.hasDisplayName()) {
                meta.setDisplayName(c.c("&f" + prettyItemName(plugin, display, s.itemKey())));
            } else {
                meta.setDisplayName(c.c(meta.getDisplayName())); // Farben/Hex support
            }

            // ✅ Lore: Original behalten + moderne Shop-Infos dranhängen
            List<String> lore = new ArrayList<>();

            if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
                for (String line : meta.getLore()) lore.add(c.c(line));
                lore.add(c.c("&8──────────────"));
            }

            // Moderne Shopinfo-Lore (fresh)
            lore.add(c.c("&b&lSHOPINFO"));
            lore.add(c.c("&7Besitzer: &f" + safe(s.ownerName())));
            lore.add(c.c("&7Menge: &f" + s.amount() + "x"));
            lore.add(c.c("&7Kaufen: " + (s.buyPrice() > 0 ? "&a" + s.buyPrice() : "&c—")));
            lore.add(c.c("&7Verkaufen: " + (s.sellPrice() > 0 ? "&a" + s.sellPrice() : "&c—")));
            lore.add(c.c("&7Lager: &f" + (stock >= 0 ? stock : "?")));
            lore.add(c.c("&8Key: &7" + safe(s.itemKey())));

            // Optional: Config-Zeilen unten drunter (falls du Extras willst)
            // (Nur wenn du willst - ansonsten kannst du das auch löschen)
            List<String> extra = plugin.getConfig().getStringList("gui.item-lore-extra");
            if (extra != null && !extra.isEmpty()) {
                lore.add(c.c("&8──────────────"));
                for (String line : extra) {
                    lore.add(c.c(line
                            .replace("%owner%", safe(s.ownerName()))
                            .replace("%amount%", String.valueOf(s.amount()))
                            .replace("%buy%", String.valueOf(s.buyPrice()))
                            .replace("%sell%", String.valueOf(s.sellPrice()))
                            .replace("%key%", safe(s.itemKey()))
                            .replace("%stock%", stock >= 0 ? String.valueOf(stock) : "?")
                    ));
                }
            }

            meta.setLore(lore);
            display.setItemMeta(meta);
        }

        inv.setItem(13, display);
        p.openInventory(inv);
    }

    /**
     * ✅ Prototype aus der Kiste holen (best: nimmt echtes Item mit Name/Lore)
     */
    private static ItemStack findPrototypeFromChest(PlotropolisChestShopPlugin plugin, Shop s) {
        try {
            if (!(s.chestLoc().getBlock().getState() instanceof Chest chest)) return null;

            for (ItemStack it : chest.getBlockInventory().getContents()) {
                if (it == null || it.getType() == Material.AIR || it.getAmount() <= 0) continue;
                if (!matchesKey(plugin, it, s.itemKey())) continue;

                ItemStack copy = it.clone();
                copy.setAmount(1);
                return copy;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Fallback: Nexo/Vanilla aus Key bauen, wenn Kiste leer oder Welt nicht geladen
     */
    private static ItemStack buildFallbackItem(PlotropolisChestShopPlugin plugin, Shop s) {
        // ✅ Nexo
        if (s.itemKey().startsWith("nexo:")) {
            String id = s.itemKey().substring("nexo:".length());
            ItemStack nexoItem = plugin.nexo().itemFromId(id, 1);
            if (nexoItem != null) return nexoItem;
            return new ItemStack(Material.CHEST);
        }

        // ✅ Vanilla
        if (s.itemKey().startsWith("minecraft:")) {
            String matName = s.itemKey().substring("minecraft:".length()).toUpperCase(Locale.ROOT);
            Material m = Material.matchMaterial(matName);
            if (m != null) return new ItemStack(m);
        }

        return new ItemStack(Material.CHEST);
    }

    private static int countStock(PlotropolisChestShopPlugin plugin, Shop s) {
        try {
            if (!(s.chestLoc().getBlock().getState() instanceof Chest chest)) return -1;

            int count = 0;
            for (ItemStack it : chest.getBlockInventory().getContents()) {
                if (it == null || it.getType() == Material.AIR || it.getAmount() <= 0) continue;

                if (matchesKey(plugin, it, s.itemKey())) {
                    count += it.getAmount();
                }
            }
            return count;
        } catch (Exception ignored) { }
        return -1;
    }

    private static boolean matchesKey(PlotropolisChestShopPlugin plugin, ItemStack it, String key) {
        if (key == null) return false;

        if (key.startsWith("nexo:")) {
            if (!plugin.nexo().isReady()) return false;
            String wanted = key.substring("nexo:".length());
            String got = plugin.nexo().idFromItem(it);
            return got != null && got.equalsIgnoreCase(wanted);
        }

        if (key.startsWith("minecraft:")) {
            String matName = key.substring("minecraft:".length()).toUpperCase(Locale.ROOT);
            Material m = Material.matchMaterial(matName);
            return m != null && it.getType() == m;
        }

        return false;
    }

    private static String prettyItemName(PlotropolisChestShopPlugin plugin, ItemStack display, String key) {
        // Wenn ItemMeta keinen Namen hat, geben wir nice Vanilla Name zurück
        if (display != null && display.getType() != null) {
            String n = display.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            if (!n.isEmpty()) return n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1);
        }

        if (key == null) return "?";
        if (key.startsWith("minecraft:")) return key.substring("minecraft:".length()).toUpperCase(Locale.ROOT);
        return key;
    }

    private static String safe(String s) {
        return s == null ? "?" : s;
    }
}