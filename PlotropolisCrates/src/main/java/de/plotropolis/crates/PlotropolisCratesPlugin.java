package de.plotropolis.crates;

import de.plotropolis.crates.commands.CrateCommand;
import de.plotropolis.crates.crates.Crate;
import de.plotropolis.crates.data.PlayerDataStore;
import de.plotropolis.crates.economy.PlotropolisCoinsHook;
import de.plotropolis.crates.gui.GuiManager;
import de.plotropolis.crates.hologram.HologramService;
import de.plotropolis.crates.listener.BlockInteractListener;
import de.plotropolis.crates.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class PlotropolisCratesPlugin extends JavaPlugin {

    private PlayerDataStore playerData;
    private PlotropolisCoinsHook economy;

    private HologramService holograms;
    private GuiManager gui;

    private final Map<String, Crate> crates = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        ColorUtil.init(this);

        Plugin coins = Bukkit.getPluginManager().getPlugin("PlotropolisCoins");
        if (coins == null || !coins.isEnabled()) {
            getLogger().severe("PlotropolisCoins nicht gefunden oder nicht aktiviert! PlotropolisCrates wird deaktiviert.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.playerData = new PlayerDataStore(this);
        this.playerData.load();

        this.economy = new PlotropolisCoinsHook(this);
        if (!economy.init()) {
            getLogger().severe("PlotropolisCoins Hook konnte nicht initialisiert werden. PlotropolisCrates wird deaktiviert.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        loadAllCrates();

        this.holograms = new HologramService(this);
        this.gui = new GuiManager(this);

        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(new BlockInteractListener(this), this);

        PluginCommand cmd = getCommand("pcrate");
        if (cmd != null) {
            CrateCommand executor = new CrateCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'pcrate' nicht in plugin.yml gefunden!");
        }

        holograms.loadAll();

        getLogger().info("PlotropolisCrates enabled.");
    }

    @Override
    public void onDisable() {
        if (holograms != null) holograms.shutdown();
        if (playerData != null) playerData.save();
        crates.clear();
        getLogger().info("PlotropolisCrates disabled.");
    }


    public Crate getCrate(String name) {
        if (name == null) return null;
        return crates.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Crate> getCrates() {
        return Collections.unmodifiableCollection(crates.values());
    }

    public void loadAllCrates() {
        crates.clear();

        List<String> names = Crate.listCrates(this);
        int loaded = 0;

        for (String n : names) {
            Crate c = Crate.load(this, n);
            if (c == null) continue;
            crates.put(c.getName().toLowerCase(Locale.ROOT), c);
            loaded++;
        }

        getLogger().info("Crate-Cache geladen: " + loaded + " Crates");
    }

    public void putCrate(Crate c) {
        if (c == null || c.getName() == null) return;
        crates.put(c.getName().toLowerCase(Locale.ROOT), c);
    }

    public void removeCrate(String name) {
        if (name == null) return;
        crates.remove(name.toLowerCase(Locale.ROOT));
    }


    public PlayerDataStore playerData() { return playerData; }
    public PlotropolisCoinsHook economy() { return economy; }

    public HologramService holograms() { return holograms; }
    public GuiManager gui() { return gui; }

    public String prefix() {
        return ColorUtil.color(getConfig().getString("prefix", "&7"));
    }
}