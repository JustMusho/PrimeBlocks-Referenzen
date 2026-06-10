package de.plotropolis.clan.scoreboard;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.model.Clan;
import de.plotropolis.clan.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ClanTagScoreboardService {

    private static final int TEAM_NAME_MAX = 16;

    private static final Pattern PR_RANK_TEAM = Pattern.compile("^pr\\d{3}_.*", Pattern.CASE_INSENSITIVE);

    private static final Pattern PR_FAKE_TEAM = Pattern.compile("^pr\\d{3}c.*", Pattern.CASE_INSENSITIVE);

    private final PlotropolisClan plugin;
    private int taskId = -1;

    private final Map<UUID, Map<UUID, String>> lastSuffix = new ConcurrentHashMap<>();

    private final Map<UUID, Map<UUID, String>> lastRankTeamName = new ConcurrentHashMap<>();

    public ClanTagScoreboardService(PlotropolisClan plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("settings.clan-tags.enabled", true)) return;

        int ticks = Math.max(10, plugin.getConfig().getInt("settings.clan-tags.ticks", 10));
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, ticks);
    }

    public void shutdown() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
        lastSuffix.clear();
        lastRankTeamName.clear();
    }

    public void refreshAllForEveryone() {
        lastSuffix.clear();
        lastRankTeamName.clear();
        tick();
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("settings.clan-tags.enabled", true)) return;

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = viewer.getScoreboard();
            if (sb == null) continue;

            Map<UUID, String> viewerSuffixCache = lastSuffix.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>());
            Map<UUID, String> viewerRankCache = lastRankTeamName.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>());

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target == null) continue;

                String desiredSuffixRaw = buildVerifiedClanSuffix(target);
                String desiredSuffix = desiredSuffixRaw == null ? "" : desiredSuffixRaw;

                String old = viewerSuffixCache.get(target.getUniqueId());
                if (Objects.equals(old, desiredSuffix)) {
                    continue;
                }

                Team currentTeam = sb.getEntryTeam(target.getName());
                if (currentTeam == null) {
                    viewerSuffixCache.put(target.getUniqueId(), desiredSuffix);
                    continue;
                }

                String baseRankTeamName = null;
                Team baseRankTeam = null;

                String curName = currentTeam.getName();

                if (isRealRankTeam(curName)) {
                    baseRankTeamName = curName;
                    baseRankTeam = currentTeam;
                    viewerRankCache.put(target.getUniqueId(), baseRankTeamName);
                } else if (isFakeTeam(curName)) {
                    baseRankTeamName = viewerRankCache.get(target.getUniqueId());
                    if (baseRankTeamName != null) {
                        baseRankTeam = sb.getTeam(baseRankTeamName);
                    }
                } else {
                    viewerSuffixCache.put(target.getUniqueId(), desiredSuffix);
                    continue;
                }

                apply(sb, target, baseRankTeamName, baseRankTeam, desiredSuffix);
                viewerSuffixCache.put(target.getUniqueId(), desiredSuffix);
            }
        }
    }

    private void apply(Scoreboard sb, Player target, String baseRankTeamName, Team baseRankTeam, String desiredSuffix) {
        if (desiredSuffix == null) desiredSuffix = "";

        String sortKey = buildSortKey(baseRankTeamName);

        String fakeTeamName = buildFakeTeamName(sortKey, target.getUniqueId());
        Team fake = sb.getTeam(fakeTeamName);

        if (desiredSuffix.isEmpty()) {
            if (fake != null && fake.hasEntry(target.getName())) {
                fake.removeEntry(target.getName());
            }

            if (baseRankTeam != null && !baseRankTeam.hasEntry(target.getName())) {
                baseRankTeam.addEntry(target.getName());
            }

            if (fake != null && fake.getEntries().isEmpty()) {
                try { fake.unregister(); } catch (Throwable ignored) {}
            }
            return;
        }

        if (fake == null) {
            fake = sb.registerNewTeam(fakeTeamName);
            try {
                fake.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            } catch (Throwable ignored) {}
        }

        String rankPrefix = "";
        if (baseRankTeam != null && baseRankTeam.getPrefix() != null) {
            rankPrefix = baseRankTeam.getPrefix();
        }
        if (!Objects.equals(fake.getPrefix(), rankPrefix)) fake.setPrefix(rankPrefix);

        String suffix = colorize(desiredSuffix);
        if (!Objects.equals(fake.getSuffix(), suffix)) fake.setSuffix(suffix);

        if (!fake.hasEntry(target.getName())) fake.addEntry(target.getName());

        if (baseRankTeam != null && baseRankTeam.hasEntry(target.getName())) {
            baseRankTeam.removeEntry(target.getName());
        }
    }

    private boolean isRealRankTeam(String teamName) {
        if (teamName == null) return false;

        if (teamName.toLowerCase(Locale.ROOT).startsWith("pr")) {
            return PR_RANK_TEAM.matcher(teamName).matches();
        }

        List<String> prefixes = plugin.getConfig().getStringList("settings.clan-tags.rank-team-prefixes");
        if (prefixes == null || prefixes.isEmpty()) prefixes = List.of("pb_rank_");

        for (String p : prefixes) {
            if (p == null || p.isEmpty()) continue;
            if (teamName.startsWith(p)) return true;
        }
        return false;
    }

    private boolean isFakeTeam(String teamName) {
        if (teamName == null) return false;
        return PR_FAKE_TEAM.matcher(teamName).matches();
    }

    private String buildSortKey(String baseRankTeamName) {
        if (baseRankTeamName == null) return "pr999c";

        String low = baseRankTeamName.toLowerCase(Locale.ROOT);
        if (PR_RANK_TEAM.matcher(baseRankTeamName).matches()) {
            String num = baseRankTeamName.substring(2, 5);
            return "pr" + num + "c";
        }

        int keep = Math.min(5, low.length());
        return low.substring(0, keep) + "c";
    }

    private String buildFakeTeamName(String sortKey, UUID uuid) {
        int remaining = TEAM_NAME_MAX - sortKey.length();
        if (remaining < 1) remaining = 1;
        return sortKey + shortHash(uuid.toString(), remaining);
    }

    private String shortHash(String in, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(in.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(Integer.toHexString((b & 0xFF) | 0x100), 1, 3);
            String out = sb.toString().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            if (out.length() > len) out = out.substring(0, len);
            if (out.isBlank()) out = "X";
            return out;
        } catch (Exception e) {
            String out = ("X" + Math.abs(in.hashCode())).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            if (out.length() > len) out = out.substring(0, len);
            if (out.isBlank()) out = "X";
            return out;
        }
    }

    private String buildVerifiedClanSuffix(Player p) {
        Clan c = plugin.data().getClanOf(p.getUniqueId());
        if (c == null) return "";
        if (!c.verified()) return "";

        String format = plugin.getConfig().getString("settings.clan-tags.verified-format", "&f[&b{tag}&f]");
        format = format.replace("{tag}", c.tag());

        boolean space = plugin.getConfig().getBoolean("settings.clan-tags.space-before-tag", true);
        String out = (space ? " " : "") + format;

        return ColorUtil.c(out);
    }

    private String colorize(String s) {
        if (s == null) return "";
        return s;
    }
}
