package de.plotropolis.jobs.gui;

import de.plotropolis.jobs.PlotropolisJobs;
import de.plotropolis.jobs.data.PlayerData;
import de.plotropolis.jobs.jobs.JobType;
import de.plotropolis.jobs.jobs.Progression;
import de.plotropolis.jobs.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.Locale;

public final class JobsGUI implements Listener {

    private final PlotropolisJobs plugin;

    public JobsGUI(PlotropolisJobs plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static final class MainHolder implements InventoryHolder {
        private final Map<Integer, JobType> jobSlotMap = new HashMap<>();
        public Map<Integer, JobType> map() { return jobSlotMap; }
        @Override public Inventory getInventory() { return null; }
    }

    private static final class DetailHolder implements InventoryHolder {
        private final JobType job;
        private DetailHolder(JobType job) { this.job = job; }
        public JobType job() { return job; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player p) {
        if (!p.hasPermission("plotropolisjobs.gui.open")) {
            p.sendMessage(c(plugin.prefix() + "&cKeine Rechte."));
            return;
        }

        int rows = clamp(plugin.getConfig().getInt("gui.rows", 6), 1, 6);
        String title = c(plugin.getConfig().getString("gui.title", "&fJobs"));

        MainHolder holder = new MainHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);

        paintMain(inv);

        PlayerData data = plugin.data().get(p.getUniqueId());

        placeInfoCard(inv, p);
        placeCloseButton(inv, p);

        placeJobButton(inv, holder, p, data, JobType.MINER, "gui.cards.miner", 20);
        placeJobButton(inv, holder, p, data, JobType.WOODCUTTER, "gui.cards.woodcutter", 21);
        placeJobButton(inv, holder, p, data, JobType.HUNTER, "gui.cards.hunter", 22);
        placeJobButton(inv, holder, p, data, JobType.DIGGER, "gui.cards.digger", 23);
        placeJobButton(inv, holder, p, data, JobType.FISHER, "gui.cards.fisher", 24);

        placeProgressBarRow(inv, data, 29, 33);

        p.openInventory(inv);
    }

    private void paintMain(Inventory inv) {
        Material border = mat("gui.decor.border", Material.BLACK_STAINED_GLASS_PANE);
        Material filler = mat("gui.decor.background", mat("gui.decor.filler", Material.GRAY_STAINED_GLASS_PANE));
        Material header = mat("gui.decor.header", filler);

        String empty = plugin.getConfig().getString("gui.decor.empty-name", " ");

        ItemStack b = glass(border, empty);
        ItemStack f = glass(filler, empty);
        ItemStack h = glass(header, empty);

        int size = inv.getSize();

        for (int i = 0; i < size; i++) inv.setItem(i, f);

        for (int i = 0; i < 9; i++) inv.setItem(i, b);
        for (int i = size - 9; i < size; i++) inv.setItem(i, b);
        for (int r = 0; r < size / 9; r++) {
            inv.setItem(r * 9, b);
            inv.setItem(r * 9 + 8, b);
        }

        for (int i = 9; i < 18 && i < size; i++) inv.setItem(i, h);
    }

    private void placeInfoCard(Inventory inv, Player p) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("gui.buttons.info");
        if (sec == null) return;
        inv.setItem(sec.getInt("slot", 4), buildSimple(sec, p, null));
    }

    private void placeCloseButton(Inventory inv, Player p) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("gui.buttons.close");
        if (sec == null) return;
        inv.setItem(sec.getInt("slot", 49), buildSimple(sec, p, null));
    }

    private void placeJobButton(Inventory inv, MainHolder holder, Player p, PlayerData data, JobType job, String path, int slot) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
        if (sec == null) return;

        Material icon = Material.matchMaterial(sec.getString("icon", sec.getString("material", "BOOK")));
        if (icon == null) icon = Material.BOOK;

        PlayerData.JobStats js = data.job(job);

        long need = Progression.requiredForNext(plugin, js.level);
        int percent = percent(js.xp, need);

        String name = sec.getString("name", niceJob(job));
        List<String> desc = sec.getStringList("desc");

        String color = switch (job) {
            case MINER -> "&b";
            case WOODCUTTER -> "&a";
            case HUNTER -> "&c";
            case DIGGER -> "&e";
            case FISHER -> "&9";
        };

        List<String> lore = new ArrayList<>();
        lore.addAll(desc);
        lore.add("");
        lore.add("&8▸ &7Level: " + color + js.level);
        lore.add("&8▸ &7XP: " + color + js.xp + "&7/&f" + need);
        lore.add(barColored(percent, color) + " &7" + percent + "%");
        lore.add("");
        lore.add("&8▸ &7Auszahlung: &a+" + formatCoins(js.sessionCoins));
        lore.add("");
        lore.add("&fKlick &8→ &7Details");

        ItemStack it = new ItemStack(icon);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(c(name));
        meta.setLore(lore.stream().map(this::c).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);

        inv.setItem(slot, it);
        holder.map().put(slot, job);
    }

    private void placeProgressBarRow(Inventory inv, PlayerData data, int from, int to) {
        long sumXp = 0;
        long sumNeed = 0;
        int bestLvl = 1;
        JobType bestJob = JobType.MINER;

        for (JobType jt : JobType.values()) {
            PlayerData.JobStats js = data.job(jt);
            long need = Progression.requiredForNext(plugin, js.level);
            sumXp += js.xp;
            sumNeed += need;
            if (js.level > bestLvl) {
                bestLvl = js.level;
                bestJob = jt;
            }
        }

        int percent = (sumNeed <= 0) ? 100 : (int) Math.round(Math.max(0, Math.min(1, (double) sumXp / (double) sumNeed)) * 100.0);

        int slots = (to - from) + 1;
        int filled = (int) Math.round((percent / 100.0) * slots);

        Material filledMat = Material.LIME_STAINED_GLASS_PANE;
        Material emptyMat = Material.GRAY_STAINED_GLASS_PANE;

        for (int i = 0; i < slots; i++) {
            boolean isFilled = i < filled;
            ItemStack it = new ItemStack(isFilled ? filledMat : emptyMat);
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(c("&fGesamt-Fortschritt"));
            meta.setLore(List.of(
                    c("&7Dein Gesamt-Level-Fortschritt über alle Jobs"),
                    c("&7Bestes Job-Level: &f" + niceJob(bestJob) + " &8(" + bestLvl + ")"),
                    c("&7Progress: &a" + percent + "%")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(meta);
            inv.setItem(from + i, it);
        }
    }

    private void openDetails(Player p, JobType job) {
        int rows = clamp(plugin.getConfig().getInt("gui.details.rows", 5), 1, 6);
        String title = c(plugin.getConfig().getString("gui.details.title", "&8Job &fDetails"));

        Inventory inv = Bukkit.createInventory(new DetailHolder(job), rows * 9, title);

        paintDetails(inv);

        ConfigurationSection back = plugin.getConfig().getConfigurationSection("gui.details.back");
        if (back != null) inv.setItem(back.getInt("slot", 36), buildSimple(back, p, job));

        ConfigurationSection card = plugin.getConfig().getConfigurationSection("gui.details.card");
        if (card != null) inv.setItem(card.getInt("slot", 22), buildDetailsCard(card, p, job));

        p.openInventory(inv);
    }

    private void paintDetails(Inventory inv) {
        Material border = mat("gui.details.decor.border", Material.BLACK_STAINED_GLASS_PANE);
        Material filler = mat("gui.details.decor.filler", Material.GRAY_STAINED_GLASS_PANE);
        Material header = mat("gui.details.decor.header", filler);

        String empty = plugin.getConfig().getString("gui.details.decor.empty-name", " ");

        ItemStack b = glass(border, empty);
        ItemStack f = glass(filler, empty);
        ItemStack h = glass(header, empty);

        int size = inv.getSize();
        for (int i = 0; i < size; i++) inv.setItem(i, f);

        for (int i = 0; i < 9; i++) inv.setItem(i, b);
        for (int i = size - 9; i < size; i++) inv.setItem(i, b);
        for (int r = 0; r < size / 9; r++) {
            inv.setItem(r * 9, b);
            inv.setItem(r * 9 + 8, b);
        }

        for (int i = 9; i < 18 && i < size; i++) inv.setItem(i, h);
    }

    private ItemStack buildDetailsCard(ConfigurationSection sec, Player p, JobType job) {
        PlayerData data = plugin.data().get(p.getUniqueId());
        PlayerData.JobStats js = data.job(job);

        long need = Progression.requiredForNext(plugin, js.level);
        double coins = Progression.coinsPerAction(js.level);
        int payout = plugin.getConfig().getInt("settings.payout-interval-seconds", 60);

        String color = switch (job) {
            case MINER -> "&b";
            case WOODCUTTER -> "&a";
            case HUNTER -> "&c";
            case DIGGER -> "&e";
            case FISHER -> "&9";
        };

        int percent = percent(js.xp, need);

        Material mat = Material.matchMaterial(sec.getString("material", "BOOK"));
        if (mat == null) mat = Material.BOOK;

        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(c(sec.getString("name", "&fFortschritt")));

        List<String> lore = new ArrayList<>();
        for (String l : sec.getStringList("lore")) {
            lore.add(c(l
                    .replace("{job}", niceJob(job))
                    .replace("{color}", color)
                    .replace("{level}", String.valueOf(js.level))
                    .replace("{xp}", String.valueOf(js.xp))
                    .replace("{need}", String.valueOf(need))
                    .replace("{percent}", String.valueOf(percent))
                    .replace("{bar}", barColored(percent, color))
                    .replace("{coins}", formatCoins(coins))
                    .replace("{session}", formatCoins(js.sessionCoins))
                    .replace("{payout}", String.valueOf(payout))
            ));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory() == null) return;

        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof MainHolder) && !(holder instanceof DetailHolder)) return;

        e.setCancelled(true);
        if (!p.hasPermission("plotropolisjobs.gui.click")) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int slot = e.getSlot();

        if (holder instanceof MainHolder mh) {
            int closeSlot = plugin.getConfig().getInt("gui.buttons.close.slot", 49);
            if (slot == closeSlot) {
                p.closeInventory();
                return;
            }

            JobType job = mh.map().get(slot);
            if (job != null) openDetails(p, job);
            return;
        }

        if (holder instanceof DetailHolder) {
            int backSlot = plugin.getConfig().getInt("gui.details.back.slot", 36);
            if (slot == backSlot) open(p);
        }
    }

    private ItemStack buildSimple(ConfigurationSection sec, Player p, JobType job) {
        Material mat = Material.matchMaterial(sec.getString("material", "STONE"));
        if (mat == null) mat = Material.STONE;

        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();

        meta.setDisplayName(c(repl(sec.getString("name", "&fItem"), p, job)));
        List<String> lore = new ArrayList<>();
        for (String l : sec.getStringList("lore")) lore.add(c(repl(l, p, job)));
        if (!lore.isEmpty()) meta.setLore(lore);

        if (mat == Material.PLAYER_HEAD && meta instanceof SkullMeta sm && p != null) {
            sm.setOwningPlayer(p);
            meta = sm;
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    private String repl(String s, Player p, JobType job) {
        if (s == null) return "";
        int payout = plugin.getConfig().getInt("settings.payout-interval-seconds", 60);
        return s.replace("{payout}", String.valueOf(payout));
    }

    private ItemStack glass(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(c(name));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    private Material mat(String path, Material def) {
        Material m = Material.matchMaterial(plugin.getConfig().getString(path, def.name()));
        return (m == null ? def : m);
    }

    private String c(String s) { return ColorUtil.c(s); }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private int percent(long xp, long need) {
        if (need <= 0) return 100;
        double d = (double) xp / (double) need;
        return (int) Math.round(Math.max(0, Math.min(1, d)) * 100.0);
    }

    private String barColored(int percent, String jobColor) {
        int total = 20;
        int filled = (int) Math.round((percent / 100.0) * total);

        StringBuilder sb = new StringBuilder();
        sb.append("&8[");
        for (int i = 0; i < total; i++) {
            sb.append(i < filled ? jobColor + "|" : "&7|");
        }
        sb.append("&8]");
        return sb.toString();
    }

    private String niceJob(JobType job) {
        return switch (job) {
            case MINER -> "Miner";
            case WOODCUTTER -> "Holzfäller";
            case HUNTER -> "Jäger";
            case DIGGER -> "Gräber";
            case FISHER -> "Fischer";
        };
    }

    private String formatCoins(double v) {
        if (v <= 0.0) return "0";
        double rounded = Math.rint(v);
        if (Math.abs(v - rounded) < 1e-9) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.US, "%.1f", v);
    }
}
