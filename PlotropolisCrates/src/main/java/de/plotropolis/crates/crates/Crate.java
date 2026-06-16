package de.plotropolis.crates.crates;

import de.plotropolis.crates.PlotropolisCratesPlugin;
import de.plotropolis.crates.util.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Crate {

    private final String name;
    private String displayName;
    private long price;
    private final Set<String> placements = new HashSet<>();
    private final Map<Integer, CrateItem> itemsBySlot = new HashMap<>();

    private Crate(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public long getPrice() { return price; }
    public Set<String> getPlacements() { return placements; }

    public Collection<CrateItem> getItems() { return itemsBySlot.values(); }
    public CrateItem getItemAt(int slot) { return itemsBySlot.get(slot); }

    public void setItemAt(int slot, CrateItem item) {
        if (item == null) { itemsBySlot.remove(slot); return; }
        ItemStack it = item.getItem();
        if (it == null || it.getType() == Material.AIR) { itemsBySlot.remove(slot); return; }
        itemsBySlot.put(slot, item);
    }

    public void removeItemAt(int slot) { itemsBySlot.remove(slot); }

    public static File folder(PlotropolisCratesPlugin plugin) {
        String f = plugin.getConfig().getString("settings.crates-folder", "crates");
        File dir = new File(plugin.getDataFolder(), f);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File file(PlotropolisCratesPlugin plugin, String name) {
        return new File(folder(plugin), name + ".yml");
    }

    public static boolean exists(PlotropolisCratesPlugin plugin, String name) {
        return file(plugin, name).exists();
    }

    public static List<String> listCrates(PlotropolisCratesPlugin plugin) {
        File dir = folder(plugin);
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return List.of();
        List<String> out = new ArrayList<>();
        for (File f : files) out.add(f.getName().substring(0, f.getName().length() - 4));
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    public static Crate create(PlotropolisCratesPlugin plugin, String name, long price) {
        Crate c = new Crate(name);
        c.displayName = name;
        c.price = price;
        c.save(plugin);
        return c;
    }

    public static Crate load(PlotropolisCratesPlugin plugin, String name) {
        File f = file(plugin, name);
        if (!f.exists()) return null;

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        Crate c = new Crate(y.getString("name", name));
        c.displayName = y.getString("display-name", name);
        c.price = y.getLong("price", 0L);

        c.placements.clear();
        c.placements.addAll(y.getStringList("placements"));

        c.itemsBySlot.clear();

        ConfigurationSection sec = y.getConfigurationSection("items");
        if (sec != null) {
            int loaded = 0;

            for (String key : sec.getKeys(false)) {
                int slot;
                try {
                    slot = Integer.parseInt(key);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Crate '" + name + "': Ungültiger Item-Key '" + key + "' (kein Slot-Integer).");
                    continue;
                }

                String rarityStr = sec.getString(key + ".rarity", "NORMAL");
                Rarity r;
                try { r = Rarity.valueOf(rarityStr.toUpperCase(Locale.ROOT)); }
                catch (Exception ex) { r = Rarity.NORMAL; }

                String base64 = sec.getString(key + ".item", "");
                String cmd = sec.getString(key + ".command", "");

                if (base64 == null || base64.isBlank()) {
                    plugin.getLogger().warning("Crate '" + name + "': Slot " + slot + " hat kein 'item' (Base64 leer).");
                    continue;
                }

                ItemStack item = ItemSerializer.fromBase64(base64);
                if (item == null) {
                    plugin.getLogger().warning("Crate '" + name + "': Slot " + slot + " konnte nicht geladen werden (Base64/Deserialize fehlgeschlagen).");
                    continue;
                }
                if (item.getType() == Material.AIR) {
                    plugin.getLogger().warning("Crate '" + name + "': Slot " + slot + " ist AIR (wird ignoriert).");
                    continue;
                }

                c.itemsBySlot.put(slot, new CrateItem(slot, r, item, cmd));
                loaded++;
            }

            plugin.getLogger().info("Crate '" + name + "' geladen. Items: " + loaded);
        } else {
            plugin.getLogger().info("Crate '" + name + "' geladen. Items: 0 (keine items-section)");
        }

        return c;
    }

    public void save(PlotropolisCratesPlugin plugin) {
        File f = file(plugin, name);
        YamlConfiguration y = new YamlConfiguration();

        y.set("name", name);
        y.set("display-name", displayName);
        y.set("price", price);
        y.set("placements", new ArrayList<>(placements));

        y.set("items", null);

        int saved = 0;

        List<Integer> slots = new ArrayList<>(itemsBySlot.keySet());
        Collections.sort(slots);

        for (int slot : slots) {
            CrateItem ci = itemsBySlot.get(slot);
            if (ci == null) continue;

            ItemStack it = ci.getItem();
            if (it == null || it.getType() == Material.AIR) continue;

            String b64 = ItemSerializer.toBase64(it);
            if (b64.isBlank()) {
                plugin.getLogger().warning("Crate '" + name + "': Item in Slot " + slot + " konnte nicht serialisiert werden.");
                continue;
            }

            String path = "items." + slot;
            y.set(path + ".rarity", ci.getRarity().name());
            y.set(path + ".command", ci.getCommand() == null ? "" : ci.getCommand());
            y.set(path + ".item", b64);

            saved++;
        }

        try {
            y.save(f);
            plugin.getLogger().info("Crate '" + name + "' gespeichert. Items: " + saved);
        } catch (IOException ex) {
            plugin.getLogger().severe("Konnte Crate nicht speichern: " + name + " (" + ex.getMessage() + ")");
        }
    }
}
