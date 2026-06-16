package de.plotropolis.chestshop;

import de.plotropolis.chestshop.command.ChestShopCommand;
import de.plotropolis.chestshop.command.ShopInfoCommand;
import de.plotropolis.chestshop.economy.EconomyHook;
import de.plotropolis.chestshop.economy.PlotropolisCoinsHook;
import de.plotropolis.chestshop.gui.ShopInfoGuiListener;
import de.plotropolis.chestshop.holo.HologramManager;
import de.plotropolis.chestshop.nexo.NexoHook;
import de.plotropolis.chestshop.shop.ShopBreakListener;
import de.plotropolis.chestshop.shop.ShopManager;
import de.plotropolis.chestshop.shop.ShopStorage;
import de.plotropolis.chestshop.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlotropolisChestShopPlugin extends JavaPlugin {

    private ColorUtil color;
    private EconomyHook economy;
    private NexoHook nexo;
    private ShopStorage storage;
    private ShopManager shopManager;
    private HologramManager holograms;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.color = new ColorUtil(this);
        this.nexo = new NexoHook(this);
        this.economy = new PlotropolisCoinsHook(this);

        this.storage = new ShopStorage(this);
        this.shopManager = new ShopManager(this, storage, economy, nexo);

        this.holograms = new HologramManager(this, shopManager);

        // Listener
        Bukkit.getPluginManager().registerEvents(shopManager, this);
        Bukkit.getPluginManager().registerEvents(holograms, this);

        // ✅ Shop abbauen (Shift + Owner) + Kiste abbauen -> Shop löschen
        Bukkit.getPluginManager().registerEvents(new ShopBreakListener(this, shopManager), this);

        // ✅ GUI read-only (keiner kann Items rausziehen)
        Bukkit.getPluginManager().registerEvents(new ShopInfoGuiListener(this), this);

        // Commands
        var pcsCmd = getCommand("plotropolischestshop");
        if (pcsCmd != null) {
            ChestShopCommand executor = new ChestShopCommand(this);
            pcsCmd.setExecutor(executor);
            pcsCmd.setTabCompleter(executor);
        }

        var infoCmd = getCommand("shopinfo");
        if (infoCmd != null) {
            ShopInfoCommand executor = new ShopInfoCommand(this, shopManager);
            infoCmd.setExecutor(executor);
            infoCmd.setTabCompleter(executor);
        }

        // Data load
        storage.loadAll(shopManager);

        // Holograms
        if (getConfig().getBoolean("hologram.enabled", true)) {
            holograms.start();
        }

        getLogger().info("PlotropolisChestShop enabled.");
    }

    @Override
    public void onDisable() {
        try { storage.saveAll(shopManager); } catch (Exception ignored) {}
        try { holograms.shutdown(); } catch (Exception ignored) {}
    }

    public ColorUtil color() { return color; }
    public EconomyHook economy() { return economy; }
    public NexoHook nexo() { return nexo; }
    public ShopStorage storage() { return storage; }
    public ShopManager shops() { return shopManager; }

    public void reloadAll() {
        reloadConfig();
        color.reload();

        // Save/Reload shops
        try { storage.saveAll(shopManager); } catch (Exception ignored) {}
        shopManager.clear();
        storage.loadAll(shopManager);

        // Reload holograms
        try { holograms.shutdown(); } catch (Exception ignored) {}
        if (getConfig().getBoolean("hologram.enabled", true)) holograms.start();
    }
}