package de.plotropolis.chestshop.shop;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import de.plotropolis.chestshop.economy.EconomyHook;
import de.plotropolis.chestshop.nexo.NexoHook;
import de.plotropolis.chestshop.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.regex.Pattern;

public final class ShopManager implements Listener {

    private final PlotropolisChestShopPlugin plugin;
    private final ShopStorage storage;
    private final EconomyHook economy;
    private final NexoHook nexo;
    private final ColorUtil c;

    private final Map<String, Shop> bySign = new HashMap<>();

    private static final Pattern SINGLE_PART = Pattern.compile("(?i)^[BS]\\s*\\d+$");
    private static final Pattern BOTH_PARTS = Pattern.compile("(?i)^B\\s*\\d+\\s*:?\\s*S\\s*\\d+$");

    public ShopManager(PlotropolisChestShopPlugin plugin, ShopStorage storage, EconomyHook economy, NexoHook nexo) {
        this.plugin = plugin;
        this.storage = storage;
        this.economy = economy;
        this.nexo = nexo;
        this.c = plugin.color();
    }

    public void clear() { bySign.clear(); }
    public int count() { return bySign.size(); }
    public Collection<Shop> all() { return Collections.unmodifiableCollection(bySign.values()); }

    public void addLoaded(Shop s) {
        bySign.put(key(s.signLoc()), s);
    }

    public void remove(Shop shop) {
        if (shop == null) return;
        bySign.remove(key(shop.signLoc()));
        storage.saveAll(this);
    }

    public Shop getBySign(Block signBlock) {
        return bySign.get(key(signBlock.getLocation()));
    }

    public Shop getByChest(Block chestBlock) {
        String world = chestBlock.getWorld().getName();
        int x = chestBlock.getX();
        int y = chestBlock.getY();
        int z = chestBlock.getZ();

        for (Shop s : bySign.values()) {
            if (s.chestLoc() == null) continue;
            if (!s.chestLoc().getWorld().getName().equals(world)) continue;

            if (s.chestLoc().getBlockX() == x
                    && s.chestLoc().getBlockY() == y
                    && s.chestLoc().getBlockZ() == z) {
                return s;
            }
        }
        return null;
    }

    public Shop getByTargeted(Player p) {
        int dist = plugin.getConfig().getInt("settings.target-distance", 6);
        Block b = p.getTargetBlockExact(dist);
        if (b == null) return null;
        if (!(b.getState() instanceof Sign)) return null;
        return getBySign(b);
    }

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        Player p = e.getPlayer();

        String l1 = safe(e.getLine(0));
        String l2 = safe(e.getLine(1));
        String l3 = safe(e.getLine(2));
        String l4 = safe(e.getLine(3));

        // ✅ Leere Schilder ignorieren
        if (l1.isBlank() && l2.isBlank() && l3.isBlank() && l4.isBlank()) return;

        // ✅ BUG 2 FIX:
        // Normale Schilder komplett ignorieren.
        // Shop-Logik nur, wenn es wie ein Shop aussieht: Line2 = Zahl und Line3 = B/S Pattern
        boolean looksLikeShop =
                l2.matches("\\d+")
                        && (BOTH_PARTS.matcher(l3.trim()).matches() || SINGLE_PART.matcher(l3.trim()).matches());

        if (!looksLikeShop) return;

        // Ab hier ist es ein echter Shop-Versuch → Permission checken
        if (!p.hasPermission("plotropolis.chestshop.create")) {
            p.sendMessage(c.msg(plugin.getConfig().getString("messages.no-permission")));
            return;
        }

        // ✅ Playername muss auf Line 1 stehen (wie du willst)
        if (!l1.equalsIgnoreCase(p.getName())) {
            p.sendMessage(c.msg(plugin.getConfig().getString("messages.must-be-owner")));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(l2.trim());
            if (amount <= 0 || amount > 64 * 54) throw new NumberFormatException();
        } catch (Exception ex) {
            p.sendMessage(c.msg(plugin.getConfig().getString("messages.invalid-sign")));
            return;
        }

        long buy = 0, sell = 0;

        if (BOTH_PARTS.matcher(l3.trim()).matches()) {
            String up = l3.toUpperCase(Locale.ROOT);
            buy = parseAfter(up, "B");
            sell = parseAfter(up, "S");
        } else if (SINGLE_PART.matcher(l3.trim()).matches()) {
            String up = l3.trim().toUpperCase(Locale.ROOT);
            if (up.startsWith("B")) buy = Long.parseLong(up.substring(1).trim());
            else if (up.startsWith("S")) sell = Long.parseLong(up.substring(1).trim());
        } else {
            p.sendMessage(c.msg(plugin.getConfig().getString("messages.invalid-sign")));
            return;
        }

        Block signBlock = e.getBlock();
        Block chestBlock = findNearbyChest(signBlock);
        if (chestBlock == null) {
            p.sendMessage(c.msg(plugin.getConfig().getString("messages.invalid-sign")));
            return;
        }

        String itemKey;
        String niceName;

        if (l4.equals("?")) {
            ItemStack detected = detectFirstItem(chestBlock);
            if (detected == null) {
                p.sendMessage(c.msg(plugin.getConfig().getString("messages.no-item-detected")));
                return;
            }

            itemKey = toItemKey(detected);
            niceName = niceItemName(detected, itemKey);

            e.setLine(3, c.c("&f" + niceName));
        } else {
            itemKey = normalizeItemKey(l4);

            ItemStack preview = buildFromKey(itemKey);
            niceName = (preview != null) ? niceItemName(preview, itemKey) : displayFallbackFromKey(itemKey);

            e.setLine(3, c.c("&f" + niceName));
        }

        // ✅ Line 1 bleibt Playername (nur farbig gemacht)
        e.setLine(0, c.c("&b" + p.getName()));

        Shop shop = new Shop(
                signBlock.getLocation(),
                chestBlock.getLocation(),
                p.getUniqueId(),
                p.getName(),
                amount,
                buy,
                sell,
                itemKey
        );

        bySign.put(key(shop.signLoc()), shop);
        storage.saveAll(this);

        p.sendMessage(c.msg(plugin.getConfig().getString("messages.created")));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (!(e.getClickedBlock().getState() instanceof Sign)) return;

        Shop shop = getBySign(e.getClickedBlock());
        if (shop == null) return;

        Player p = e.getPlayer();

        // ✅ BUG 1 FIX:
        // Owner soll Shop-Schild abbauen können wenn er schleicht + linksklickt.
        // -> Interact NICHT canceln, sonst kann man nicht abbauen.
        if (e.getAction() == Action.LEFT_CLICK_BLOCK
                && p.isSneaking()
                && shop.ownerUuid().equals(p.getUniqueId())) {
            e.setCancelled(false);
            return;
        }

        if (!p.hasPermission("plotropolis.chestshop.use")) {
            p.sendMessage(c.msg(plugin.getConfig().getString("messages.no-permission")));
            e.setCancelled(true);
            return;
        }

        boolean ownerCan = plugin.getConfig().getBoolean("settings.owner-can-trade", false);
        if (!ownerCan && shop.ownerUuid().equals(p.getUniqueId())) {
            p.sendMessage(c.msg(plugin.getConfig().getString("messages.owner-cant-trade")));
            e.setCancelled(true);
            return;
        }

        boolean rightClick = e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        String mode = plugin.getConfig().getString("settings.click-mode", "RIGHT_BUY_LEFT_SELL");
        boolean doBuy;
        if ("RIGHT_SELL_LEFT_BUY".equalsIgnoreCase(mode)) {
            doBuy = !rightClick; // left = buy
        } else {
            doBuy = rightClick;  // right = buy
        }

        if (doBuy) tryBuy(p, shop);
        else trySell(p, shop);

        e.setCancelled(true);
    }

    private void tryBuy(Player buyer, Shop s) {
        if (s.buyPrice() <= 0) return;

        Chest chest = getChest(s.chestLoc());
        if (chest == null) return;

        Inventory inv = chest.getBlockInventory();

        int stock = countMatching(inv, s.itemKey());
        if (stock < s.amount()) {
            buyer.sendMessage(c.msg(plugin.getConfig().getString("messages.not-enough-stock")));
            return;
        }

        if (economy.isReady() && economy.balance(buyer.getUniqueId()) < s.buyPrice()) {
            buyer.sendMessage(c.msg(plugin.getConfig().getString("messages.not-enough-money")));
            return;
        }

        ItemStack toGive = buildStackFromChest(inv, s.itemKey(), s.amount());
        if (toGive == null) {
            buyer.sendMessage(c.msg(plugin.getConfig().getString("messages.not-enough-stock")));
            return;
        }

        Map<Integer, ItemStack> left = buyer.getInventory().addItem(toGive.clone());
        if (!left.isEmpty()) {
            buyer.sendMessage(c.msg(plugin.getConfig().getString("messages.inventory-full")));
            for (ItemStack rem : left.values()) buyer.getInventory().removeItem(rem);
            return;
        }

        removeMatching(inv, s.itemKey(), s.amount());

        boolean paid = true;
        if (economy.isReady()) {
            if (!economy.withdraw(buyer.getUniqueId(), s.buyPrice())) {
                buyer.getInventory().removeItem(toGive);
                inv.addItem(toGive);
                buyer.sendMessage(c.msg(plugin.getConfig().getString("messages.not-enough-money")));
                return;
            }
            economy.deposit(s.ownerUuid(), s.buyPrice());
        } else {
            paid = false;
        }

        String itemNice = niceItemName(toGive, s.itemKey());

        String msg = plugin.getConfig().getString("messages.bought");
        buyer.sendMessage(c.msg(msg
                .replace("%amount%", String.valueOf(s.amount()))
                .replace("%item%", itemNice)
                .replace("%price%", String.valueOf(s.buyPrice()))
        ));

        if (paid) notifyOwnerSold(s, buyer.getName(), itemNice, s.amount(), s.buyPrice());
    }

    private void trySell(Player seller, Shop s) {
        if (s.sellPrice() <= 0) return;

        Chest chest = getChest(s.chestLoc());
        if (chest == null) return;

        Inventory chestInv = chest.getBlockInventory();

        int have = countMatching(seller.getInventory(), s.itemKey());
        if (have < s.amount()) {
            seller.sendMessage(c.msg(plugin.getConfig().getString("messages.not-enough-items")));
            return;
        }

        if (economy.isReady() && economy.balance(s.ownerUuid()) < s.sellPrice()) {
            seller.sendMessage(c.msg(plugin.getConfig().getString("messages.owner-not-enough-money")));
            return;
        }

        ItemStack toStore = extractPrototype(seller.getInventory(), s.itemKey(), s.amount());
        if (toStore == null) {
            seller.sendMessage(c.msg(plugin.getConfig().getString("messages.not-enough-items")));
            return;
        }

        Map<Integer, ItemStack> left = chestInv.addItem(toStore.clone());
        if (!left.isEmpty()) {
            seller.sendMessage(c.msg(plugin.getConfig().getString("messages.chest-full")));
            for (ItemStack rem : left.values()) chestInv.removeItem(rem);
            return;
        }

        removeMatching(seller.getInventory(), s.itemKey(), s.amount());

        boolean paid = true;
        if (economy.isReady()) {
            if (!economy.withdraw(s.ownerUuid(), s.sellPrice())) {
                seller.getInventory().addItem(toStore);
                removeMatching(chestInv, s.itemKey(), s.amount());
                seller.sendMessage(c.msg(plugin.getConfig().getString("messages.owner-not-enough-money")));
                return;
            }
            economy.deposit(seller.getUniqueId(), s.sellPrice());
        } else {
            paid = false;
        }

        String itemNice = niceItemName(toStore, s.itemKey());

        String msg = plugin.getConfig().getString("messages.sold");
        seller.sendMessage(c.msg(msg
                .replace("%amount%", String.valueOf(s.amount()))
                .replace("%item%", itemNice)
                .replace("%price%", String.valueOf(s.sellPrice()))
        ));

        if (paid) notifyOwnerBought(s, seller.getName(), itemNice, s.amount(), s.sellPrice());
    }

    private void notifyOwnerSold(Shop s, String buyerName, String itemNice, int amount, long price) {
        Player owner = Bukkit.getPlayer(s.ownerUuid());
        if (owner == null || !owner.isOnline()) return;

        String msg = plugin.getConfig().getString("messages.owner-sold",
                "%prefix%&b%buyer% &7hat &f%amount%x %item% &7gekauft. &8(+&a%price%&8)");

        owner.sendMessage(c.msg(msg
                .replace("%buyer%", buyerName)
                .replace("%amount%", String.valueOf(amount))
                .replace("%item%", itemNice)
                .replace("%price%", String.valueOf(price))
        ));
    }

    private void notifyOwnerBought(Shop s, String sellerName, String itemNice, int amount, long price) {
        Player owner = Bukkit.getPlayer(s.ownerUuid());
        if (owner == null || !owner.isOnline()) return;

        String msg = plugin.getConfig().getString("messages.owner-bought",
                "%prefix%&b%seller% &7hat &f%amount%x %item% &7verkauft. &8(-&c%price%&8)");

        owner.sendMessage(c.msg(msg
                .replace("%seller%", sellerName)
                .replace("%amount%", String.valueOf(amount))
                .replace("%item%", itemNice)
                .replace("%price%", String.valueOf(price))
        ));
    }

    // ---------------- helpers ----------------

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private long parseAfter(String up, String letter) {
        int idx = up.indexOf(letter);
        if (idx == -1) return 0;
        String sub = up.substring(idx + 1).replace(":", " ").trim();
        String[] parts = sub.split("\\s+");
        return Long.parseLong(parts[0]);
    }

    private String key(org.bukkit.Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private Block findNearbyChest(Block signBlock) {
        // Optional: safer faces only (prevents weird attachments)
        org.bukkit.block.BlockFace[] faces = {
                org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.DOWN
        };

        for (org.bukkit.block.BlockFace face : faces) {
            Block b = signBlock.getRelative(face);
            if (b.getState() instanceof Chest) return b;
        }
        return null;
    }

    private ItemStack detectFirstItem(Block chestBlock) {
        Chest chest = (Chest) chestBlock.getState();
        Inventory inv = chest.getBlockInventory();
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() != Material.AIR && it.getAmount() > 0) return it;
        }
        return null;
    }

    private String normalizeItemKey(String input) {
        String s = input.trim();
        if (s.contains(":")) return s.toLowerCase(Locale.ROOT);

        Material m = Material.matchMaterial(s);
        if (m != null) return "minecraft:" + m.name().toLowerCase(Locale.ROOT);
        return "minecraft:" + s.toLowerCase(Locale.ROOT);
    }

    private String toItemKey(ItemStack it) {
        String nexoId = nexo.isReady() ? nexo.idFromItem(it) : null;
        if (nexoId != null) return "nexo:" + nexoId;
        return "minecraft:" + it.getType().name().toLowerCase(Locale.ROOT);
    }

    private ItemStack buildFromKey(String itemKey) {
        if (itemKey == null) return null;

        if (itemKey.startsWith("nexo:")) {
            if (!nexo.isReady()) return null;
            String id = itemKey.substring("nexo:".length());
            return nexo.itemFromId(id, 1);
        }

        if (itemKey.startsWith("minecraft:")) {
            String matName = itemKey.substring("minecraft:".length()).toUpperCase(Locale.ROOT);
            Material m = Material.matchMaterial(matName);
            if (m != null) return new ItemStack(m);
        }

        return null;
    }

    private String niceItemName(ItemStack it, String itemKey) {
        if (it == null) return displayFallbackFromKey(itemKey);

        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        String n = it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (n.isEmpty()) return displayFallbackFromKey(itemKey);
        return n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1);
    }

    private String displayFallbackFromKey(String itemKey) {
        if (itemKey == null) return "?";
        if (itemKey.startsWith("minecraft:")) return itemKey.substring("minecraft:".length()).toUpperCase(Locale.ROOT);
        if (itemKey.startsWith("nexo:")) return "Custom Item";
        return itemKey;
    }

    private Chest getChest(org.bukkit.Location l) {
        if (l.getBlock().getState() instanceof Chest c) return c;
        return null;
    }

    private boolean matches(ItemStack it, String itemKey) {
        if (it == null || it.getType() == Material.AIR) return false;
        String key = toItemKey(it);
        return key.equalsIgnoreCase(itemKey);
    }

    private int countMatching(Inventory inv, String itemKey) {
        int c = 0;
        for (ItemStack it : inv.getContents()) {
            if (matches(it, itemKey)) c += it.getAmount();
        }
        return c;
    }

    private ItemStack buildStackFromChest(Inventory inv, String itemKey, int amount) {
        for (ItemStack it : inv.getContents()) {
            if (matches(it, itemKey)) {
                ItemStack copy = it.clone();
                copy.setAmount(amount);
                return copy;
            }
        }
        return null;
    }

    private ItemStack extractPrototype(Inventory inv, String itemKey, int amount) {
        for (ItemStack it : inv.getContents()) {
            if (matches(it, itemKey)) {
                ItemStack copy = it.clone();
                copy.setAmount(amount);
                return copy;
            }
        }
        return null;
    }

    private void removeMatching(Inventory inv, String itemKey, int amount) {
        int left = amount;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (!matches(it, itemKey)) continue;

            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) inv.setItem(i, null);
            left -= take;
            if (left <= 0) break;
        }
    }
}