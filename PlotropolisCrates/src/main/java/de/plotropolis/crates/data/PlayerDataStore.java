package de.plotropolis.crates.data;

import de.plotropolis.crates.PlotropolisCratesPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class PlayerDataStore {

    private final PlotropolisCratesPlugin plugin;
    private File file;
    private YamlConfiguration yml;

    public PlayerDataStore(PlotropolisCratesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String fn = plugin.getConfig().getString("settings.players-file", "players.yml");
        file = new File(plugin.getDataFolder(), fn);
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {}
        }
        yml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { yml.save(file); }
        catch (IOException ignored) {}
    }

    public int getCrates(UUID uuid, String crateName) {
        return yml.getInt("players." + uuid + ".crates." + crateName, 0);
    }

    public void addCrates(UUID uuid, String crateName, int add) {
        int cur = getCrates(uuid, crateName);
        yml.set("players." + uuid + ".crates." + crateName, Math.max(0, cur + add));
        save();
    }

    public boolean takeCrate(UUID uuid, String crateName, int amount) {
        int cur = getCrates(uuid, crateName);
        if (cur < amount) return false;
        yml.set("players." + uuid + ".crates." + crateName, cur - amount);
        save();
        return true;
    }

    public int getMegaJackpots(UUID uuid) {
        return yml.getInt("players." + uuid + ".stats.mega", 0);
    }

    public int getJackpots(UUID uuid) {
        return yml.getInt("players." + uuid + ".stats.jackpot", 0);
    }

    public void addMega(UUID uuid) {
        yml.set("players." + uuid + ".stats.mega", getMegaJackpots(uuid) + 1);
        save();
    }

    public void addJackpot(UUID uuid) {
        yml.set("players." + uuid + ".stats.jackpot", getJackpots(uuid) + 1);
        save();
    }
}
