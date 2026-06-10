package de.plotropolis.clan.gui;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.model.Clan;
import de.plotropolis.clan.model.ClanRole;
import de.plotropolis.clan.util.ItemBuilder;
import de.plotropolis.clan.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class RolesGui implements GuiScreen {

    private final PlotropolisClan plugin;
    private final Clan clan;
    private final Inventory inv;

    private final List<ClanRole> viewRoles = new ArrayList<>();
    private final Map<Integer, ClanRole> roleBySlot = new HashMap<>();

    public RolesGui(PlotropolisClan plugin, Clan clan) {
        this.plugin = plugin;
        this.clan = clan;

        String title = ColorUtil.c("&#2fd9ffClan &#ffffffRollen &8• &f[" + clan.tag() + "]");
        this.inv = Bukkit.createInventory(null, 54, title);

        build();
    }

    private void build() {
        inv.clear();
        GuiStyle.border(inv);

        viewRoles.clear();
        roleBySlot.clear();

        inv.setItem(4, new ItemBuilder(Material.NETHER_STAR)
                .name("&#2fd9ff&lRollen Manager")
                .loreLine("&7Verwalte Rechte & Reihenfolge")
                .loreLine("&7Klicke eine Rolle um Permissions zu öffnen")
                .build());

        inv.setItem(49, GuiStyle.buttonClose());

        List<ClanRole> roles = new ArrayList<>(clan.roles().values());
        roles.removeIf(Objects::isNull);
        roles.sort((a, b) -> Integer.compare(b.weight(), a.weight()));

        viewRoles.addAll(roles);

        int slot = 10;
        for (ClanRole role : roles) {
            Material icon = role.name().equalsIgnoreCase("Anführer") ? Material.DIAMOND :
                    role.name().equalsIgnoreCase("Mitglied") ? Material.IRON_INGOT : Material.NAME_TAG;

            ItemStack card = new ItemBuilder(icon)
                    .name("&#2fd9ff&l" + role.name())
                    .loreLine("&8━━━━━━━━━━━━━━━━━━━━")
                    .loreLine("&7Priorität: &f" + role.weight())
                    .loreLine("&7Permissions: &f" + role.perms().size())
                    .loreLine("&8━━━━━━━━━━━━━━━━━━━━")
                    .loreLine("&aKlick &7→ Permissions öffnen")
                    .build();

            inv.setItem(slot, card);
            roleBySlot.put(slot, role);

            slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
        }
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

        if (it.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        ClanRole bySlot = roleBySlot.get(slot);
        if (bySlot != null) {
            plugin.gui().open(player, new RolePermsGui(plugin, clan, bySlot));
            return;
        }

        if (!it.hasItemMeta()) return;
        String plain = org.bukkit.ChatColor.stripColor(it.getItemMeta().getDisplayName());
        if (plain == null || plain.isBlank()) return;

        String roleNameClicked = plain.replace("l", "").trim();

        ClanRole found = null;
        for (ClanRole r : clan.roles().values()) {
            if (r != null && r.name() != null && r.name().equalsIgnoreCase(roleNameClicked)) {
                found = r;
                break;
            }
        }
        if (found == null) return;

        plugin.gui().open(player, new RolePermsGui(plugin, clan, found));
    }
}
