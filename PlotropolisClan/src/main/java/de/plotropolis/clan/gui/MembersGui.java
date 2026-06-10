package de.plotropolis.clan.gui;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.model.Clan;
import de.plotropolis.clan.model.ClanRole;
import de.plotropolis.clan.util.ColorUtil;
import de.plotropolis.clan.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

public final class MembersGui implements GuiScreen {

    private final PlotropolisClan plugin;
    private final Clan clan;
    private final Inventory inv;

    private final List<UUID> sortedMembers;
    private int page = 0;

    private static final int[] CONTENT_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    public MembersGui(PlotropolisClan plugin, Clan clan) {
        this.plugin = plugin;
        this.clan = clan;

        String title = ColorUtil.c("&#2fd9ffClan &#ffffffMitglieder &8• &f[" + clan.tag() + "]");

        this.inv = Bukkit.createInventory(null, 54, title);

        this.sortedMembers = clan.members().keySet().stream().sorted((a,b) -> {
            ClanRole ra = clan.roleOf(a);
            ClanRole rb = clan.roleOf(b);
            int wa = ra != null ? ra.weight() : 0;
            int wb = rb != null ? rb.weight() : 0;
            if (wa != wb) return Integer.compare(wb, wa);

            String na = nameOf(a);
            String nb = nameOf(b);
            return na.compareToIgnoreCase(nb);
        }).collect(Collectors.toList());

        build();
    }

    private void build() {
        inv.clear();
        GuiStyle.border(inv);

        ItemStack header = new ItemBuilder(Material.BOOK)
                .name("&#2fd9ff&l" + clan.name())
                .loreLine("&7TAG: &f[" + clan.tag() + "]")
                .loreLine("&7Mitglieder: &f" + clan.members().size())
                .loreLine("&7Bank: &f" + plugin.economy().formatMoney(clan.bankMoney()))
                .loreLine("&7Verifiziert: " + (clan.verified() ? "&a✔ &7Ja" : "&c✘ &7Nein"))
                .build();
        inv.setItem(4, header);

        inv.setItem(49, GuiStyle.buttonClose());

        int perPage = CONTENT_SLOTS.length;
        int maxPage = Math.max(0, (sortedMembers.size() - 1) / perPage);

        if (page > 0) inv.setItem(45, GuiStyle.buttonPrev());
        if (page < maxPage) inv.setItem(53, GuiStyle.buttonNext());

        int start = page * perPage;
        int end = Math.min(sortedMembers.size(), start + perPage);

        int slotIndex = 0;
        for (int i = start; i < end; i++) {
            UUID u = sortedMembers.get(i);

            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            String roleName = clan.members().get(u);
            boolean owner = clan.isOwner(u);
            boolean online = op.isOnline();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(op);

            meta.setDisplayName(ColorUtil.c(
                    (owner ? "&#ffd54a&l★ " : "&#2fd9ff") +
                            (op.getName() == null ? "Unknown" : op.getName()) +
                            (online ? " &a●" : " &7●")
            ));

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.c("&8━━━━━━━━━━━━━━━━━━━━"));
            lore.add(ColorUtil.c("&7Rolle: &f" + roleName));
            lore.add(ColorUtil.c("&7Status: &f" + (owner ? "Anführer" : "Mitglied")));
            lore.add(ColorUtil.c("&7Online: " + (online ? "&aJa" : "&cNein")));
            lore.add(ColorUtil.c("&8━━━━━━━━━━━━━━━━━━━━"));
            lore.add(ColorUtil.c("&8Sortiert nach Rollen-Rang"));
            meta.setLore(lore);

            head.setItemMeta(meta);

            inv.setItem(CONTENT_SLOTS[slotIndex++], head);
        }

        inv.setItem(50, new ItemBuilder(Material.PAPER)
                .name("&#2fd9ff&lSeite &f" + (page + 1) + "&#2fd9ff/&f" + (maxPage + 1))
                .loreLine("&7Nutze die Buttons links/rechts")
                .build()
        );
    }

    private String nameOf(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return op.getName() == null ? u.toString().substring(0,8) : op.getName();
    }

    @Override public Inventory inventory() { return inv; }

    @Override
    public void onClick(InventoryClickEvent e, Player player) {
        ItemStack it = e.getCurrentItem();
        if (it == null) return;

        if (it.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (it.getType() == Material.RED_STAINED_GLASS_PANE) {
            page = Math.max(0, page - 1);
            build();
            return;
        }
        if (it.getType() == Material.LIME_STAINED_GLASS_PANE) {
            page = page + 1;
            build();
        }
    }
}
