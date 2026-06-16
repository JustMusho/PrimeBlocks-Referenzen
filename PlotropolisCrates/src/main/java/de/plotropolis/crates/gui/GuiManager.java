package de.plotropolis.crates.gui;

import de.plotropolis.crates.PlotropolisCratesPlugin;
import de.plotropolis.crates.crates.Crate;
import de.plotropolis.crates.crates.CrateItem;
import de.plotropolis.crates.crates.Rarity;
import de.plotropolis.crates.gui.holder.CrateEditHolder;
import de.plotropolis.crates.gui.holder.CratePreviewHolder;
import de.plotropolis.crates.gui.holder.ItemEditHolder;
import de.plotropolis.crates.gui.holder.OpeningHolder;
import de.plotropolis.crates.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

public final class GuiManager implements Listener {

    private final PlotropolisCratesPlugin plugin;

    public GuiManager(PlotropolisCratesPlugin plugin) {
        this.plugin = plugin;
    }

    public final Map<UUID, ItemEditHolder> awaitingCommand = new HashMap<>();

    private final Map<UUID, Location> lastCrateLocation = new HashMap<>();

    private Location blockLoc(Location loc) {
        if (loc == null) return null;
        if (loc.getWorld() == null) return null;
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public void rememberLastCrateLocation(UUID player, Location loc) {
        if (player == null) return;
        Location b = blockLoc(loc);
        if (b == null) {
            lastCrateLocation.remove(player);
            return;
        }
        lastCrateLocation.put(player, b);
    }

    private Location consumeLastCrateLocation(UUID player) {
        Location loc = lastCrateLocation.remove(player);
        return blockLoc(loc);
    }

    public void openPreview(Player p, Crate c, int page) {
        int maxPage = maxPreviewPage(c);
        if (maxPage <= 0) maxPage = 1;
        if (page < 1) page = 1;
        if (page > maxPage) page = maxPage;

        Inventory inv = Bukkit.createInventory(new CratePreviewHolder(c, page), 54,
                ColorUtil.color(plugin.getConfig().getString("settings.gui.preview-title", "&8Crate")
                        .replace("%crate%", c.getDisplayName())));
        buildBorder(inv);
        placePreviewButtons(inv, p, c, page, maxPage);
        placeCrateItems(inv, c, page, false);
        p.openInventory(inv);
    }

    public void openEdit(Player p, Crate c, int page) {
        if (page < 1) page = 1;

        Inventory inv = Bukkit.createInventory(new CrateEditHolder(c, page), 54,
                ColorUtil.color(plugin.getConfig().getString("settings.gui.edit-title", "&8Edit")
                        .replace("%crate%", c.getDisplayName())));
        buildBorder(inv);
        placeEditButtons(inv, p, c, page);
        placeCrateItems(inv, c, page, true);
        p.openInventory(inv);
    }

    public void openItemEdit(Player p, Crate c, CrateItem item) {
        Inventory inv = Bukkit.createInventory(new ItemEditHolder(c, item), 45,
                ColorUtil.color(plugin.getConfig().getString("settings.gui.itemedit-title", "&8Item bearbeiten")));

        fill(inv, borderItem());

        inv.setItem(slot(3,2), button(Material.DIAMOND, "&bSeltenheit ändern",
                List.of("&7Aktuell: &f" + rarityDisplay(item.getRarity()), "&7Klick: nächste Seltenheit")));

        inv.setItem(slot(3,5), item.getItem().clone());

        inv.setItem(slot(3,8), button(Material.COMMAND_BLOCK, "&eCommand zuweisen",
                List.of("&7Aktuell: &f" + (item.isCommandReward() ? item.getCommand() : "&8(kein)"),
                        "&7Klick: Chat-Eingabe",
                        "&8Tippe &fcancel &8um zu löschen.")));

        inv.setItem(slot(5,5), button(Material.EMERALD, "&a&lSpeichern",
                List.of("&7Speichert die Crate-Datei", "&7und aktualisiert Hologramme.")));

        p.openInventory(inv);
    }

    public void openOpening(Player p, Crate c) {
        openOpening(p, c, null);
    }

    public void openOpening(Player p, Crate c, Location crateLoc) {
        Inventory inv = Bukkit.createInventory(new OpeningHolder(c, crateLoc), 27,
                ColorUtil.color(plugin.getConfig().getString("settings.gui.opening-title", "&8Opening")
                        .replace("%crate%", c.getDisplayName())));

        ItemStack grey = borderItem();
        for (int i = 0; i <= 8; i++) inv.setItem(i, grey);
        inv.setItem(4, button(Material.HOPPER, "&fZieh-Slot", List.of("&7Hier landet dein Item")));
        for (int i = 18; i <= 26; i++) inv.setItem(i, grey);

        p.openInventory(inv);

        OpeningHolder holder = (OpeningHolder) inv.getHolder();
        if (holder != null) {
            Bukkit.getScheduler().runTask(plugin, () -> holder.start(plugin, p, inv));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory top = e.getView().getTopInventory();
        if (top == null || top.getHolder() == null) return;

        int raw = e.getRawSlot();
        int topSize = top.getSize();
        boolean inTop = raw >= 0 && raw < topSize;

        if (top.getHolder() instanceof CratePreviewHolder h) {
            if (!inTop) { e.setCancelled(true); return; }
            e.setCancelled(true);

            int slot = raw;
            int maxPage = maxPreviewPage(h.crate());
            if (maxPage <= 0) maxPage = 1;

            if (slot == slot(6,1)) {
                if (h.page() > 1) openPreview(p, h.crate(), h.page() - 1);
                return;
            }
            if (slot == slot(6,9)) {
                if (h.page() < maxPage) openPreview(p, h.crate(), h.page() + 1);
                return;
            }

            if (slot == slot(6,5)) {
                if (!p.hasPermission("plotropoliscrates.buy")) {
                    p.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.no-permission", "%prefix%&cNo")
                            .replace("%prefix%", plugin.prefix())));
                    return;
                }

                long price = h.crate().getPrice();

                if (!plugin.economy().has(p, price)) {
                    p.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.not-enough-coins", "")
                            .replace("%prefix%", plugin.prefix())
                            .replace("%price%", String.valueOf(price))));
                    return;
                }

                if (!plugin.economy().withdraw(p, price)) {
                    p.sendMessage(ColorUtil.color(plugin.prefix() + "&cFehler beim Bezahlen."));
                    return;
                }

                plugin.playerData().addCrates(p.getUniqueId(), h.crate().getName(), 1);

                play(p, "sounds.buy");
                p.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.bought", "")
                        .replace("%prefix%", plugin.prefix())
                        .replace("%crate%", h.crate().getDisplayName())
                        .replace("%price%", String.valueOf(price))));

                boolean ok = plugin.playerData().takeCrate(p.getUniqueId(), h.crate().getName(), 1);
                if (!ok) return;

                p.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.opening", "")
                        .replace("%prefix%", plugin.prefix())));

                Location crateLoc = consumeLastCrateLocation(p.getUniqueId());
                openOpening(p, h.crate(), crateLoc);
            }
            return;
        }

        if (top.getHolder() instanceof CrateEditHolder h) {

            if (!inTop) return;

            int slot = raw;

            if (isBorderSlot(slot) || slot == slot(6,1) || slot == slot(6,9) || slot == slot(6,5) || slot == slot(1,5)) {
                e.setCancelled(true);

                if (slot == slot(6,1) && h.page() > 1) openEdit(p, h.crate(), h.page() - 1);
                if (slot == slot(6,9)) openEdit(p, h.crate(), h.page() + 1);
                return;
            }

            if (e.getClick().isRightClick()) {
                ItemStack cur = e.getCurrentItem();
                if (cur == null || cur.getType() == Material.AIR) return;

                int crateSlot = slotToCrateSlot(h.page(), slot);
                if (crateSlot < 0) return;

                e.setCancelled(true);

                CrateItem ci = h.crate().getItemAt(crateSlot);
                if (ci != null) openItemEdit(p, h.crate(), ci);
                return;
            }

            return;
        }

        if (top.getHolder() instanceof ItemEditHolder h) {
            if (!inTop) { e.setCancelled(true); return; }
            e.setCancelled(true);

            int slot = raw;

            if (slot == slot(5,5)) {
                h.crate().save(plugin);
                plugin.holograms().refreshAll();

                play(p, "sounds.buy");
                p.sendMessage(ColorUtil.color(plugin.prefix() + "&aCrate gespeichert!"));

                openEdit(p, h.crate(), 1);
                return;
            }

            if (slot == slot(3,2)) {
                Rarity next = nextRarity(h.item().getRarity());
                h.item().setRarity(next);
                top.setItem(slot(3,2), button(Material.DIAMOND, "&bSeltenheit ändern",
                        List.of("&7Aktuell: &f" + rarityDisplay(next), "&7Klick: nächste Seltenheit")));
                return;
            }

            if (slot == slot(3,8)) {
                p.closeInventory();
                p.sendMessage(ColorUtil.color(plugin.prefix() + "&7Schreibe den Command in den Chat (&fcancel&7 zum löschen)."));
                awaitingCommand.put(p.getUniqueId(), h);
                return;
            }

            return;
        }

        if (top.getHolder() instanceof OpeningHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (top == null || top.getHolder() == null) return;

        if (top.getHolder() instanceof CrateEditHolder h) {
            saveCrateFromEdit(top, h.crate(), h.page());
            h.crate().save(plugin);
            plugin.holograms().refreshAll();
            return;
        }

        if (top.getHolder() instanceof OpeningHolder h) {
            Bukkit.getScheduler().runTask(plugin, () -> h.skip(plugin, p, top));
        }
    }

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        ItemEditHolder h = awaitingCommand.remove(id);
        if (h == null) return;

        e.setCancelled(true);

        String msg = e.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("cancel")) {
                h.item().setCommand("");
                e.getPlayer().sendMessage(ColorUtil.color(plugin.prefix() + "&aCommand entfernt."));
            } else {
                h.item().setCommand(msg);
                e.getPlayer().sendMessage(ColorUtil.color(plugin.prefix() + "&aCommand gesetzt: &f" + msg));
            }
            openItemEdit(e.getPlayer(), h.crate(), h.item());
        });
    }

    private void buildBorder(Inventory inv) {
        ItemStack border = borderItem();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }
    }

    private void placePreviewButtons(Inventory inv, Player p, Crate c, int page, int maxPage) {
        inv.setItem(slot(1,5), playerHead(p, c));
        inv.setItem(slot(6,1), button(Material.ARROW, "&bSeite zurück", List.of("&7Page: &f" + page)));

        if (page < maxPage) {
            inv.setItem(slot(6,9), button(Material.ARROW, "&bSeite weiter", List.of("&7Page: &f" + page)));
        } else {
            inv.setItem(slot(6,9), button(Material.GRAY_DYE, "&7Keine weitere Seite", List.of("&7Letzte Seite erreicht")));
        }

        inv.setItem(slot(6,5), button(Material.EMERALD, "&aKiste kaufen",
                List.of("&7Kosten: &b" + c.getPrice() + " &7Kristalle",
                        "&7Du hast: &b" + plugin.playerData().getCrates(p.getUniqueId(), c.getName()) + " &7Kisten")));
    }

    private void placeEditButtons(Inventory inv, Player p, Crate c, int page) {
        inv.setItem(slot(1,5), playerHead(p, c));
        inv.setItem(slot(6,1), button(Material.ARROW, "&bSeite zurück", List.of("&7Page: &f" + page)));
        inv.setItem(slot(6,9), button(Material.ARROW, "&bSeite weiter", List.of("&7Page: &f" + page)));
        inv.setItem(slot(6,5), button(Material.LIME_DYE, "&aSpeichert beim Schließen",
                List.of("&7Rechtsklick Item: &fBearbeiten", "&7Linksklick: &fBewegen/Entfernen")));
    }

    private void placeCrateItems(Inventory inv, Crate c, int page, boolean edit) {
        int startIndex = (page - 1) * 28;
        List<Integer> innerSlots = innerSlots();

        for (int s : innerSlots) inv.setItem(s, null);

        for (int i = 0; i < innerSlots.size(); i++) {
            int crateSlot = startIndex + i;
            CrateItem ci = c.getItemAt(crateSlot);
            if (ci == null) continue;

            ItemStack it = ci.getItem() == null ? null : ci.getItem().clone();
            if (it == null || it.getType() == Material.AIR) continue;

            inv.setItem(innerSlots.get(i), it);
        }
    }

    private void saveCrateFromEdit(Inventory inv, Crate c, int page) {
        int startIndex = (page - 1) * 28;
        List<Integer> innerSlots = innerSlots();

        for (int i = 0; i < innerSlots.size(); i++) {
            int guiSlot = innerSlots.get(i);
            int crateSlot = startIndex + i;

            ItemStack it = inv.getItem(guiSlot);
            if (it == null || it.getType() == Material.AIR) {
                c.removeItemAt(crateSlot);
                continue;
            }

            CrateItem existing = c.getItemAt(crateSlot);
            if (existing == null) {
                c.setItemAt(crateSlot, new CrateItem(crateSlot, Rarity.NORMAL, it.clone(), ""));
            } else {
                existing.setItem(it.clone());
            }
        }
    }

    private int slot(int row, int col) { return (row - 1) * 9 + (col - 1); }

    private boolean isBorderSlot(int slot) {
        if (slot >= 0 && slot <= 8) return true;
        if (slot >= 45 && slot <= 53) return true;
        return slot % 9 == 0 || slot % 9 == 8;
    }

    private List<Integer> innerSlots() {
        List<Integer> s = new ArrayList<>(28);
        for (int r = 2; r <= 5; r++) {
            for (int c = 2; c <= 8; c++) s.add(slot(r, c));
        }
        return s;
    }

    private int slotToCrateSlot(int page, int guiSlot) {
        List<Integer> inner = innerSlots();
        int idx = inner.indexOf(guiSlot);
        if (idx < 0) return -1;
        return (page - 1) * 28 + idx;
    }

    private ItemStack borderItem() {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("border.material", "GRAY_STAINED_GLASS_PANE"));
        if (mat == null) mat = Material.GRAY_STAINED_GLASS_PANE;
        return button(mat, plugin.getConfig().getString("border.name", "&7"), List.of());
    }

    private void fill(Inventory inv, ItemStack item) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, item);
    }

    private ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ColorUtil.color(name));
            if (lore != null && !lore.isEmpty())
                im.setLore(lore.stream().map(ColorUtil::color).collect(Collectors.toList()));
            it.setItemMeta(im);
        }
        return it;
    }

    private ItemStack playerHead(Player p, Crate c) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            sm.setOwningPlayer(p);
            sm.setDisplayName(ColorUtil.color("&f" + p.getName()));
            sm.setLore(List.of(
                    ColorUtil.color("&7MEGA JACKPOTS: &f" + plugin.playerData().getMegaJackpots(p.getUniqueId())),
                    ColorUtil.color("&7JACKPOTS: &f" + plugin.playerData().getJackpots(p.getUniqueId())),
                    ColorUtil.color("&7Kisten: &b" + plugin.playerData().getCrates(p.getUniqueId(), c.getName()))
            ));
            head.setItemMeta(sm);
        }
        return head;
    }

    private void play(Player p, String path) {
        try {
            String s = plugin.getConfig().getString("settings." + path, "");
            if (s == null || s.isBlank()) return;
            Sound sound = Sound.valueOf(s.toUpperCase(Locale.ROOT));
            p.playSound(p.getLocation(), sound, 1f, 1f);
        } catch (Exception ignored) {}
    }

    private String rarityDisplay(Rarity r) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("rarities." + r.name());
        if (sec == null) return r.name();
        return ColorUtil.color(sec.getString("display", r.name()));
    }

    private Rarity nextRarity(Rarity r) {
        Rarity[] vals = Rarity.values();
        int idx = (r.ordinal() + 1) % vals.length;
        return vals[idx];
    }

    private int maxPreviewPage(Crate c) {
        int maxSlot = -1;
        for (CrateItem it : c.getItems()) {
            if (it == null) continue;
            int s = it.getSlot();
            if (s > maxSlot) maxSlot = s;
        }
        if (maxSlot < 0) return 1;
        return (maxSlot / 28) + 1;
    }
}