package de.plotropolis.clan.gui;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.model.Clan;
import de.plotropolis.clan.model.ClanPermission;
import de.plotropolis.clan.model.ClanRole;
import de.plotropolis.clan.util.ItemBuilder;
import de.plotropolis.clan.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class RolePermsGui implements GuiScreen {

    private final PlotropolisClan plugin;
    private final Clan clan;
    private final ClanRole role;
    private final Inventory inv;

    private static final int SLOT_MINUS_5 = 46;
    private static final int SLOT_MINUS_1 = 47;
    private static final int SLOT_PLUS_1  = 51;
    private static final int SLOT_PLUS_5  = 52;

    public RolePermsGui(PlotropolisClan plugin, Clan clan, ClanRole role) {
        this.plugin = plugin;
        this.clan = clan;
        this.role = role;

        String title = ColorUtil.c("&#2fd9ffPermissions &#ffffff" + role.name());
        this.inv = Bukkit.createInventory(null, 54, title);

        build();
    }

    private void build() {
        inv.clear();
        GuiStyle.border(inv);

        inv.setItem(4, new ItemBuilder(Material.COMMAND_BLOCK)
                .name("&#2fd9ff&l" + role.name())
                .loreLine("&7Toggle die Clan-Rechte")
                .loreLine("&7Updates sind live (Speichern läuft im Hintergrund)")
                .loreLine("&8━━━━━━━━━━━━━━━━━━━━")
                .loreLine("&7Rang-Priorität: &f" + role.weight())
                .loreLine("&8(Je höher, desto übergeordnet)")
                .build());

        inv.setItem(45, GuiStyle.buttonBack());
        inv.setItem(49, GuiStyle.buttonClose());

        boolean lockWeight = isDefaultRole(role);

        inv.setItem(SLOT_MINUS_5, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(lockWeight ? "&8Priorität -5" : "&c&lPriorität -5")
                .loreLine(lockWeight ? "&7Default-Rolle: gesperrt" : "&7Verringert Priorität um &f5")
                .loreLine(lockWeight ? "" : "&eKlick")
                .build());

        inv.setItem(SLOT_MINUS_1, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(lockWeight ? "&8Priorität -1" : "&c&lPriorität -1")
                .loreLine(lockWeight ? "&7Default-Rolle: gesperrt" : "&7Verringert Priorität um &f1")
                .loreLine(lockWeight ? "" : "&eKlick")
                .build());

        inv.setItem(SLOT_PLUS_1, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(lockWeight ? "&8Priorität +1" : "&a&lPriorität +1")
                .loreLine(lockWeight ? "&7Default-Rolle: gesperrt" : "&7Erhöht Priorität um &f1")
                .loreLine(lockWeight ? "" : "&eKlick")
                .build());

        inv.setItem(SLOT_PLUS_5, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(lockWeight ? "&8Priorität +5" : "&a&lPriorität +5")
                .loreLine(lockWeight ? "&7Default-Rolle: gesperrt" : "&7Erhöht Priorität um &f5")
                .loreLine(lockWeight ? "" : "&eKlick")
                .build());

        int[] slots = { 20,21,22,23,24, 29,30,31,32,33 };
        int i = 0;

        for (ClanPermission p : ClanPermission.values()) {
            boolean on = role.has(p);

            Material icon = switch (p) {
                case INVITE -> Material.WRITABLE_BOOK;
                case KICK -> Material.IRON_SWORD;
                case BANK_WITHDRAW -> Material.EMERALD;
                case ROLE_MANAGE -> Material.ANVIL;
                case ROLE_ASSIGN -> Material.NAME_TAG;
                case SETTINGS_PUBLIC_JOIN -> Material.OAK_DOOR;
            };

            ItemStack toggle = new ItemBuilder(icon)
                    .name((on ? "&a&l" : "&7&l") + p.name())
                    .loreLine("&8━━━━━━━━━━━━━━━━━━━━")
                    .loreLine("&7Status: " + (on ? "&aAN" : "&cAUS"))
                    .loreLine("&7Beschreibung: &f" + desc(p))
                    .loreLine("&8━━━━━━━━━━━━━━━━━━━━")
                    .loreLine("&eKlick &7→ togglen")
                    .glow(on)
                    .build();

            if (i < slots.length) {
                inv.setItem(slots[i++], toggle);
            }
        }
    }

    private boolean isDefaultRole(ClanRole r) {
        String n = r.name();
        if (n == null) return false;
        return n.equalsIgnoreCase("Anführer") || n.equalsIgnoreCase("Mitglied");
    }

    private boolean canEditThisRole(Player player) {
        if (player == null) return false;

        try {
            if (clan.isOwner(player.getUniqueId())) return true;
        } catch (Throwable ignored) {}

        try {
            ClanRole self = clan.roleOf(player.getUniqueId());
            if (self == null) return false;
            if (!self.has(ClanPermission.ROLE_MANAGE)) return false;
            return self.weight() > role.weight();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void changeWeight(Player player, int delta) {
        if (isDefaultRole(role)) {
            player.sendMessage(ColorUtil.c("&cDiese Default-Rolle kann nicht in der Reihenfolge geändert werden."));
            return;
        }
        if (!canEditThisRole(player)) {
            player.sendMessage(ColorUtil.c("&cDu darfst die Rang-Reihenfolge dieser Rolle nicht ändern."));
            return;
        }

        int before = role.weight();

        int next = before + delta;
        if (next < 0) next = 0;
        if (next > 1000) next = 1000;

        if (next == before) {
            player.sendMessage(ColorUtil.c("&ePriorität bleibt bei: &f" + before));
            return;
        }

        role.weight(next);

        build();

        plugin.requestSave();

        String dir = (next > before) ? "&aerhöht" : "&cverringert";
        int diff = Math.abs(next - before);

        player.sendMessage(ColorUtil.c(
                "&7Priorität von &b" + role.name() + " &7wurde " + dir +
                        " &7(&f" + before + " &7→ &f" + next + "&7, " +
                        (next > before ? "&a+" : "&c-") + diff + "&7)"
        ));
    }

    private String desc(ClanPermission p) {
        return switch (p) {
            case INVITE -> "Spieler einladen";
            case KICK -> "Spieler kicken";
            case BANK_WITHDRAW -> "Clan-Geld auszahlen";
            case ROLE_MANAGE -> "Rollen erstellen/löschen + verwalten";
            case ROLE_ASSIGN -> "Rollen vergeben";
            case SETTINGS_PUBLIC_JOIN -> "Clan öffentlich/privat";
        };
    }

    @Override
    public Inventory inventory() {
        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent e, Player player) {
        ItemStack it = e.getCurrentItem();
        if (it == null) return;

        int slot = e.getRawSlot();

        if (slot < 0 || slot >= inv.getSize()) return;

        e.setCancelled(true);

        if (it.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }
        if (it.getType() == Material.ARROW) {
            plugin.gui().open(player, new RolesGui(plugin, clan));
            return;
        }

        if (slot == SLOT_MINUS_5) { changeWeight(player, -5); return; }
        if (slot == SLOT_MINUS_1) { changeWeight(player, -1); return; }
        if (slot == SLOT_PLUS_1)  { changeWeight(player, +1); return; }
        if (slot == SLOT_PLUS_5)  { changeWeight(player, +5); return; }

        if (!it.hasItemMeta()) return;
        String plain = ChatColor.stripColor(it.getItemMeta().getDisplayName());
        if (plain == null) return;

        try {
            ClanPermission perm = ClanPermission.valueOf(plain.trim());

            if (!canEditThisRole(player)) {
                player.sendMessage(ColorUtil.c("&cDu darfst die Permissions dieser Rolle nicht ändern."));
                return;
            }

            role.set(perm, !role.has(perm));

            build();

            plugin.requestSave();

        } catch (Exception ignored) {}
    }
}