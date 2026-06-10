package de.plotropolis.jobs.listener;

import de.plotropolis.jobs.PlotropolisJobs;
import de.plotropolis.jobs.jobs.JobConfig;
import de.plotropolis.jobs.jobs.JobType;
import de.plotropolis.jobs.jobs.Progression;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public final class JobsListener implements Listener {

    private final PlotropolisJobs plugin;
    private final JobConfig cfg;

    public JobsListener(PlotropolisJobs plugin) {
        this.plugin = plugin;
        this.cfg = new JobConfig(plugin);
    }

    private boolean allowedWorld(Player p) {
        List<String> worlds = plugin.getConfig().getStringList("settings.allowed-worlds");
        if (worlds == null || worlds.isEmpty()) return true;
        return worlds.contains(p.getWorld().getName());
    }

    private boolean ignore(Player p) {
        if (!allowedWorld(p)) return true;
        return cfg.ignoreCreative() && p.getGameMode() == GameMode.CREATIVE;
    }


    private Material normalizeForJobs(Material m) {
        if (m == null) return null;

        return switch (m) {
            case DEEPSLATE_COAL_ORE -> Material.COAL_ORE;
            case DEEPSLATE_COPPER_ORE -> Material.COPPER_ORE;
            case DEEPSLATE_IRON_ORE -> Material.IRON_ORE;
            case DEEPSLATE_GOLD_ORE -> Material.GOLD_ORE;
            case DEEPSLATE_REDSTONE_ORE -> Material.REDSTONE_ORE;
            case DEEPSLATE_LAPIS_ORE -> Material.LAPIS_ORE;
            case DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND_ORE;
            case DEEPSLATE_EMERALD_ORE -> Material.EMERALD_ORE;


            default -> m;
        };
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.data().get(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.data().save(e.getPlayer().getUniqueId());
        plugin.data().unload(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (ignore(p)) return;

        if (cfg.ignoreSilkTouch()) {
            ItemStack it = p.getInventory().getItemInMainHand();
            if (it != null && it.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        }

        Material m = normalizeForJobs(e.getBlock().getType());

        if (p.hasPermission(JobType.MINER.permission()) && cfg.jobEnabled(JobType.MINER)) {
            int xp = cfg.xpForBlock(JobType.MINER, m);
            if (xp > 0) reward(p, JobType.MINER, xp);
        }

        if (p.hasPermission(JobType.WOODCUTTER.permission()) && cfg.jobEnabled(JobType.WOODCUTTER)) {
            int xp = cfg.xpForBlock(JobType.WOODCUTTER, m);
            if (xp > 0) reward(p, JobType.WOODCUTTER, xp);
        }

        if (p.hasPermission(JobType.DIGGER.permission()) && cfg.jobEnabled(JobType.DIGGER)) {
            int xp = cfg.xpForBlock(JobType.DIGGER, m);
            if (xp > 0) reward(p, JobType.DIGGER, xp);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) return;
        if (ignore(p)) return;

        if (!p.hasPermission(JobType.HUNTER.permission()) || !cfg.jobEnabled(JobType.HUNTER)) return;

        EntityType type = e.getEntityType();
        int xp = cfg.xpForMob(JobType.HUNTER, type);
        if (xp > 0) reward(p, JobType.HUNTER, xp);
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (ignore(p)) return;

        if (!p.hasPermission(JobType.FISHER.permission()) || !cfg.jobEnabled(JobType.FISHER)) return;

        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH
                || e.getState() == PlayerFishEvent.State.CAUGHT_ENTITY
                || e.getState() == PlayerFishEvent.State.IN_GROUND) {
            reward(p, JobType.FISHER, cfg.fisherXp());
        }
    }

    private void reward(Player p, JobType job, int xp) {
        UUID uuid = p.getUniqueId();

        int level = plugin.data().getLevel(uuid, job);
        double coins = Progression.coinsPerAction(level);

        plugin.data().addXpAndCoins(uuid, job, xp, coins);

        if (plugin.bossBars() != null) {
            plugin.bossBars().showOrUpdate(p, job);
        }
    }
}
