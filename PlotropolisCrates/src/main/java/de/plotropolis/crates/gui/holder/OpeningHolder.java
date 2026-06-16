package de.plotropolis.crates.gui.holder;

import de.plotropolis.crates.PlotropolisCratesPlugin;
import de.plotropolis.crates.crates.Crate;
import de.plotropolis.crates.crates.CrateItem;
import de.plotropolis.crates.crates.Rarity;
import de.plotropolis.crates.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public final class OpeningHolder implements InventoryHolder {

    private static final int WINDOW_SIZE = 9;
    private static final int CENTER_SLOT = 13;
    private static final int CENTER_OFFSET = 4;

    private final Crate crate;

    private final Location crateLocation;

    private boolean running = false;
    private boolean rewarded = false;

    private Inventory runningInv;
    private Player runningPlayer;
    private PlotropolisCratesPlugin runningPlugin;

    private CrateItem finalWin;

    public OpeningHolder(Crate crate) {
        this(crate, null);
    }

    public OpeningHolder(Crate crate, Location crateLocation) {
        this.crate = crate;
        this.crateLocation = crateLocation == null ? null : crateLocation.clone();
    }

    public Crate crate() { return crate; }

    @Override
    public Inventory getInventory() { return null; }

    public void start(PlotropolisCratesPlugin plugin, Player p, Inventory inv) {
        if (running) return;
        running = true;

        this.runningInv = inv;
        this.runningPlayer = p;
        this.runningPlugin = plugin;

        List<CrateItem> items = resolveCrateItems();
        if (items.isEmpty()) {
            running = false;
            p.closeInventory();
            p.sendMessage(ColorUtil.color(plugin.prefix() + "&cDiese Crate hat keine Items."));
            return;
        }

        Map<Rarity, List<CrateItem>> byRarity = items.stream()
                .collect(Collectors.groupingBy(CrateItem::getRarity));

        Random rnd = new Random();

        int mainSteps = 70;
        int tail = CENTER_OFFSET;

        List<CrateItem> roll = new ArrayList<>(mainSteps + tail);

        for (int i = 0; i < mainSteps - 1; i++) {
            roll.add(pickWeighted(plugin, byRarity, rnd));
        }

        this.finalWin = pickWeighted(plugin, byRarity, rnd);
        roll.add(finalWin);

        for (int i = 0; i < tail; i++) {
            roll.add(pickWeighted(plugin, byRarity, rnd));
        }

        int[] delays = buildDelays(roll.size());
        tick(plugin, p, inv, roll, finalWin, delays, 0, 0);
    }

    public void skip(PlotropolisCratesPlugin plugin, Player p, Inventory inv) {
        if (rewarded) return;
        if (!running) return;

        if (this.runningInv != inv) return;
        if (this.runningPlayer == null) return;
        if (!this.runningPlayer.getUniqueId().equals(p.getUniqueId())) return;

        CrateItem tmp = this.finalWin;
        if (tmp == null) {
            List<CrateItem> items = resolveCrateItems();
            if (items.isEmpty()) return;
            tmp = items.get(new Random().nextInt(items.size()));
        }

        final CrateItem win = tmp;

        running = false;


        Bukkit.getScheduler().runTask(plugin, () -> reward(plugin, p, win));
    }

    private List<CrateItem> resolveCrateItems() {
        try {
            Collection<CrateItem> direct = crate.getItems();
            if (direct != null && !direct.isEmpty()) {
                List<CrateItem> out = new ArrayList<>(direct.size());
                for (CrateItem ci : direct) {
                    if (ci == null) continue;
                    ItemStack it = ci.getItem();
                    if (it == null || it.getType() == Material.AIR) continue;
                    out.add(ci);
                }
                if (!out.isEmpty()) return out;
            }
        } catch (Throwable ignored) {}

        List<CrateItem> out = new ArrayList<>();
        for (int slot = 0; slot < 2000; slot++) {
            CrateItem ci = crate.getItemAt(slot);
            if (ci == null) continue;
            ItemStack it = ci.getItem();
            if (it == null || it.getType() == Material.AIR) continue;
            out.add(ci);
        }
        return out;
    }

    private void tick(PlotropolisCratesPlugin plugin, Player p, Inventory inv,
                      List<CrateItem> roll, CrateItem finalWin, int[] delays,
                      int step, int cursor) {

        if (rewarded) return;

        if (!running) return;

        if (!p.isOnline() || p.getOpenInventory().getTopInventory() != inv) {
            running = false;
            return;
        }

        int rightIndex = Math.min(cursor, roll.size() - 1);
        List<CrateItem> window = new ArrayList<>(WINDOW_SIZE);

        for (int i = 0; i < WINDOW_SIZE; i++) {
            int idx = rightIndex - i;
            if (idx < 0) idx = 0;
            window.add(roll.get(idx));
        }

        for (int i = 0; i < WINDOW_SIZE; i++) {
            CrateItem ci = window.get(8 - i);
            ItemStack show = ci.getItem();
            inv.setItem(9 + i, show == null ? new ItemStack(Material.AIR) : show.clone());
        }

        playTick(plugin, p);

        ItemStack center = inv.getItem(CENTER_SLOT);
        inv.setItem(4, decorateHopper(center));

        if (step >= roll.size() - 1) {
            running = false;
            Bukkit.getScheduler().runTask(plugin, () -> reward(plugin, p, finalWin));
            return;
        }

        int delay = delays[Math.min(step, delays.length - 1)];
        int nextStep = step + 1;
        int nextCursor = Math.min(cursor + 1, roll.size() - 1);

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                tick(plugin, p, inv, roll, finalWin, delays, nextStep, nextCursor), delay);
    }

    private int[] buildDelays(int steps) {
        int[] d = new int[steps];
        int slowStart = Math.max(steps - 14, 0);

        for (int i = 0; i < steps; i++) {
            if (i < slowStart) d[i] = 2;
            else {
                int k = i - slowStart;
                if (k < 4) d[i] = 3;
                else if (k < 8) d[i] = 4;
                else if (k < 11) d[i] = 5;
                else d[i] = 6;
            }
        }
        return d;
    }

    private ItemStack decorateHopper(ItemStack center) {
        ItemStack hop = new ItemStack(Material.HOPPER);
        ItemMeta im = hop.getItemMeta();
        if (im != null) {
            im.setDisplayName(ColorUtil.color("&fZieh-Slot"));
            if (center != null && center.getType() != Material.AIR) {
                im.setLore(List.of(ColorUtil.color("&7Aktuell: &f" + niceName(center))));
            }
            hop.setItemMeta(im);
        }
        return hop;
    }

    private String niceName(ItemStack it) {
        ItemMeta im = it.getItemMeta();
        if (im != null && im.hasDisplayName()) return im.getDisplayName();
        return it.getType().name();
    }

    private void playTick(PlotropolisCratesPlugin plugin, Player p) {
        try {
            String s = plugin.getConfig().getString("settings.sounds.open_tick", "UI_BUTTON_CLICK");
            Sound sound = Sound.valueOf(s.toUpperCase(Locale.ROOT));
            p.playSound(p.getLocation(), sound, 0.8f, 1.2f);
        } catch (Exception ignored) {}
    }

    private CrateItem pickWeighted(PlotropolisCratesPlugin plugin, Map<Rarity, List<CrateItem>> byRarity, Random rnd) {
        List<Rarity> rarities = Arrays.asList(Rarity.values());
        double roll = rnd.nextDouble() * 100.0;
        double cur = 0.0;

        for (Rarity r : rarities) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("rarities." + r.name());
            double chance = sec != null ? sec.getDouble("chance", 0.0) : 0.0;

            cur += chance;
            if (roll <= cur) {
                List<CrateItem> list = byRarity.getOrDefault(r, List.of());
                if (!list.isEmpty()) return list.get(rnd.nextInt(list.size()));
                break;
            }
        }

        List<CrateItem> all = byRarity.values().stream().flatMap(Collection::stream).toList();
        return all.get(rnd.nextInt(all.size()));
    }

    private void reward(PlotropolisCratesPlugin plugin, Player p, CrateItem win) {
        if (rewarded) return;
        rewarded = true;

        if (win.getRarity() == Rarity.MEGA_JACKPOT) plugin.playerData().addMega(p.getUniqueId());
        if (win.getRarity() == Rarity.JACKPOT) plugin.playerData().addJackpot(p.getUniqueId());

        boolean isMega = win.getRarity() == Rarity.MEGA_JACKPOT;
        boolean isJackpot = win.getRarity() == Rarity.JACKPOT;

        boolean broadcast = plugin.getConfig().getBoolean("broadcast.enabled", true) && (isMega || isJackpot);

        String itemName = niceName(win.getItem());

        if (isMega) {
            p.sendTitle(
                    ColorUtil.color("&b&lMEGA JACKPOT"),
                    ColorUtil.color("&dGewonnen: &f" + itemName),
                    10, 70, 20
            );
        } else if (isJackpot) {
            p.sendTitle(
                    ColorUtil.color("&b&lJACKPOT"),
                    ColorUtil.color("&eGewonnen: &f" + itemName),
                    10, 60, 20
            );
        }

        if (broadcast) {
            String path = isMega ? "broadcast.mega" : "broadcast.jackpot";
            String msg = plugin.getConfig().getString(path, "")
                    .replace("%prefix%", plugin.prefix())
                    .replace("%player%", p.getName())
                    .replace("%item%", itemName);
            Bukkit.broadcastMessage(ColorUtil.color(msg));
        }

        if (isMega || isJackpot) {
            playJackpotSoundRadius(plugin, p);
        }

        if (win.isCommandReward()) {
            String cmd = (win.getCommand() == null ? "" : win.getCommand())
                    .replace("%player%", p.getName())
                    .replace("%uuid%", p.getUniqueId().toString());

            if (cmd.startsWith("/")) cmd = cmd.substring(1);

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            p.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.won-command","")
                    .replace("%prefix%", plugin.prefix())
                    .replace("%cmd%", cmd)));
            return;
        }

        HashMap<Integer, ItemStack> left = p.getInventory().addItem(win.getItem().clone());
        for (ItemStack it : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), it);

        p.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.won-item","")
                .replace("%prefix%", plugin.prefix())
                .replace("%item%", itemName)));
    }

    private void playJackpotSoundRadius(PlotropolisCratesPlugin plugin, Player winner) {
        try {
            String s = plugin.getConfig().getString("settings.sounds.jackpot", "ENTITY_ENDER_DRAGON_DEATH");
            if (s == null || s.isBlank()) return;

            Sound sound = Sound.valueOf(s.toUpperCase(Locale.ROOT));

            int radius = plugin.getConfig().getInt("settings.sounds.jackpot-radius", 120);
            if (radius <= 0) radius = 120;

            if (crateLocation == null || crateLocation.getWorld() == null) {
                winner.playSound(winner.getLocation(), sound, 1f, 1f);
                return;
            }

            double maxDistSq = (double) radius * (double) radius;

            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getWorld() != crateLocation.getWorld()) continue;
                if (pl.getLocation().distanceSquared(crateLocation) <= maxDistSq) {
                    pl.playSound(pl.getLocation(), sound, 1f, 1f);
                }
            }
        } catch (Exception ignored) {}
    }
}