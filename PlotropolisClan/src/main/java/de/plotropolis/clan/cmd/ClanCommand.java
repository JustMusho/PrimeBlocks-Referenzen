package de.plotropolis.clan.cmd;

import de.plotropolis.clan.PlotropolisClan;
import de.plotropolis.clan.gui.MembersGui;
import de.plotropolis.clan.gui.RolesGui;
import de.plotropolis.clan.model.*;
import de.plotropolis.clan.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

public final class ClanCommand implements CommandExecutor {

    private final PlotropolisClan plugin;

    public ClanCommand(PlotropolisClan plugin) {
        this.plugin = plugin;
    }

    private String pfx() { return ColorUtil.c(plugin.getConfig().getString("prefix", "&7[Clan] &7")); }
    private String msg(String key) { return plugin.getConfig().getString("messages." + key, "&cMissing message: " + key); }
    private void send(CommandSender s, String m) { s.sendMessage(pfx() + ColorUtil.c(m)); }

    private long dayEpochUTC() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    private String serverId() {
        String id = plugin.getConfig().getString("network.server-id", "default");
        if (id == null || id.trim().isEmpty()) return "default";
        return id.trim();
    }

    private boolean has(CommandSender s, String perm) {
        if (s.hasPermission(perm) || s.hasPermission("plotropolisclan.*")) return true;
        send(s, msg("no-permission"));
        return false;
    }

    private boolean can(Clan clan, UUID player, ClanPermission p) {
        if (clan == null) return false;
        if (clan.isOwner(player)) return true;
        ClanRole r = clan.roleOf(player);
        return r != null && r.has(p);
    }

    private void refreshTagsSoon() {
        if (plugin.clanTagScoreboard() == null) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEnabled()) return;
            if (plugin.clanTagScoreboard() == null) return;

            try {
                plugin.clanTagScoreboard().refreshAllForEveryone();
            } catch (Throwable ignored) {}
        }, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            if (!has(sender, "plotropolisclan.cmd.help")) return true;
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("help") || sub.equals("hilfe")) {
            if (!has(sender, "plotropolisclan.cmd.help")) return true;
            help(sender);
            return true;
        }

        if (sub.equals("admin")) {
            return handleAdmin(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        if (!(sender instanceof Player player)) {
            send(sender, msg("only-player"));
            return true;
        }

        if (sub.equals("create") || sub.equals("erstellen")) {
            if (!has(sender, "plotropolisclan.cmd.create")) return true;
            if (args.length < 3) {
                send(player, "&cBenutzung: /clan create <TAG> <Clan_Name...>");
                return true;
            }
            if (plugin.data().getClanOf(player.getUniqueId()) != null) {
                send(player, msg("already-in-clan"));
                return true;
            }

            String tag = args[1];
            int max = plugin.getConfig().getInt("settings.tag-max-length", 5);
            if (tag.length() > max) {
                send(player, msg("invalid-tag").replace("{max}", String.valueOf(max)));
                return true;
            }
            if (!tag.matches("[A-Za-z0-9]+")) {
                send(player, "&cTAG darf nur Buchstaben/Zahlen enthalten.");
                return true;
            }

            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            String tagLower = tag.toLowerCase(Locale.ROOT);

            if (plugin.data().clansByTag().containsKey(tagLower)) {
                send(player, msg("tag-taken"));
                return true;
            }

            boolean requireUniqueName = plugin.getConfig().getBoolean("settings.require-unique-name", true);
            if (requireUniqueName) {
                for (Clan c : plugin.data().clansByTag().values()) {
                    if (c.name().equalsIgnoreCase(name)) {
                        send(player, msg("name-taken"));
                        return true;
                    }
                }
            }

            long cost = plugin.getConfig().getLong("settings.create-cost", 250000);

            long before = plugin.economy().getMoney(player.getUniqueId());

            boolean ok = plugin.economy().withdrawMoney(player.getUniqueId(), cost);
            if (!ok) {
                send(player, msg("create-not-enough").replace("{cost}", plugin.economy().formatMoney(cost)));
                return true;
            }

            long after = plugin.economy().getMoney(player.getUniqueId());
            if (after > before - cost) {
                plugin.economy().depositMoney(player.getUniqueId(), cost);
                plugin.getLogger().warning("[ClanCreate] Withdraw wurde gemeldet, aber Balance hat sich nicht korrekt geändert. Refund gemacht. UUID="
                        + player.getUniqueId() + " before=" + before + " after=" + after + " cost=" + cost);
                send(player, "&cFehler beim Abbuchen. Bitte versuche es erneut.");
                return true;
            }

            Clan clan = new Clan(tag, name, player.getUniqueId(), serverId());
            clan.publicJoin(plugin.getConfig().getBoolean("settings.join.default-open", false));

            plugin.data().clansByTag().put(tagLower, clan);
            plugin.data().playerClanTag().put(player.getUniqueId(), tagLower);
            plugin.data().save();

            send(player, msg("create-success")
                    .replace("{tag}", clan.tag())
                    .replace("{name}", clan.name())
                    .replace("{cost}", plugin.economy().formatMoney(cost)));

            refreshTagsSoon();
            return true;
        }

        if (sub.equals("delete") || sub.equals("löschen") || sub.equals("loeschen")) {
            if (!has(sender, "plotropolisclan.cmd.delete")) return true;
            Clan clan = plugin.data().getClanOf(player.getUniqueId());
            if (clan == null) { send(player, msg("not-in-clan")); return true; }

            if (!clan.isOwner(player.getUniqueId())) {
                send(player, msg("delete-only-owner"));
                return true;
            }

            String tagLower = plugin.data().playerClanTag().get(player.getUniqueId());
            plugin.data().clansByTag().remove(tagLower);

            for (UUID u : new ArrayList<>(clan.members().keySet())) {
                plugin.data().playerClanTag().remove(u);
            }
            plugin.data().save();
            send(player, msg("delete-success"));

            refreshTagsSoon();
            return true;
        }

        if (sub.equals("info")) {
            if (!has(sender, "plotropolisclan.cmd.info")) return true;
            if (args.length < 2) {
                send(player, "&cBenutzung: /clan info <TAG>");
                return true;
            }

            Clan clan = plugin.data().getClanByTag(args[1]);
            if (clan == null) { send(player, msg("clan-not-found")); return true; }

            OfflinePlayer owner = Bukkit.getOfflinePlayer(clan.owner());

            send(player, "&#2fd9ff&l━━━━━━━━━━━━ &#ffffff&lCLAN INFO &#2fd9ff&l━━━━━━━━━━━━");
            send(player, "&7Name: &f" + clan.name() + " &8• &7TAG: &f[" + clan.tag() + "]");
            send(player, "&7Anführer: &f" + (owner.getName() == null ? clan.owner() : owner.getName()));
            send(player, "&7Mitglieder: &f" + clan.members().size());
            send(player, "&7Clan Bank: &f" + plugin.economy().formatMoney(clan.bankMoney()));
            send(player, "&7Öffentlich: " + (clan.publicJoin() ? "&a✔ Ja" : "&c✘ Nein"));
            send(player, "&7Verifiziert: " + (clan.verified() ? "&a✔ Ja" : "&c✘ Nein"));
            send(player, "&#2fd9ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return true;
        }

        if (sub.equals("mitglieder") || sub.equals("members")) {
            if (!has(sender, "plotropolisclan.cmd.members")) return true;

            Clan clan;
            if (args.length >= 2) clan = plugin.data().getClanByTag(args[1]);
            else clan = plugin.data().getClanOf(player.getUniqueId());

            if (clan == null) { send(player, msg("clan-not-found")); return true; }
            plugin.gui().open(player, new MembersGui(plugin, clan));
            return true;
        }

        if (sub.equals("bestenliste") || sub.equals("top")) {
            if (!has(sender, "plotropolisclan.cmd.top")) return true;

            String sid = serverId();

            send(player, msg("top-header")
                    .replace("{server}", sid));

            plugin.data().clansByTag().values().stream()
                    .filter(c -> sid.equalsIgnoreCase(c.createdServer()))
                    .sorted((a, b) -> Long.compare(b.bankMoney(), a.bankMoney()))
                    .limit(10)
                    .forEachOrdered(c -> player.sendMessage(ColorUtil.c("&8- &f[" + c.tag() + "] &7" + c.name()
                            + " &8| &f" + plugin.economy().formatMoney(c.bankMoney()))));
            return true;
        }

        if (sub.equals("einladen") || sub.equals("invite")) {
            if (!has(sender, "plotropolisclan.cmd.invite")) return true;
            if (args.length < 2) {
                send(player, "&cBenutzung: /clan einladen <Spieler>");
                return true;
            }
            Clan clan = plugin.data().getClanOf(player.getUniqueId());
            if (clan == null) { send(player, msg("not-in-clan")); return true; }

            if (!can(clan, player.getUniqueId(), ClanPermission.INVITE)) {
                send(player, "&cDu darfst niemanden einladen.");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { send(player, "&cSpieler offline/nicht gefunden."); return true; }
            if (plugin.data().getClanOf(target.getUniqueId()) != null) {
                send(player, "&cDieser Spieler ist bereits in einem Clan.");
                return true;
            }
            if (clan.invites().containsKey(target.getUniqueId())) {
                send(player, msg("invite-already"));
                return true;
            }

            clan.invites().put(target.getUniqueId(), new Invite(clan.tag(), player.getUniqueId(), System.currentTimeMillis()));
            plugin.data().save();

            send(player, msg("invite-sent").replace("{player}", target.getName()));
            send(target, msg("invite-received").replace("{tag}", clan.tag()).replace("{name}", clan.name()));
            return true;
        }

        if (sub.equals("einladungen") || sub.equals("invites")) {
            if (!has(sender, "plotropolisclan.cmd.invites")) return true;

            List<Clan> incoming = new ArrayList<>();
            for (Clan c : plugin.data().clansByTag().values()) {
                if (c.invites().containsKey(player.getUniqueId())) incoming.add(c);
            }
            if (incoming.isEmpty()) {
                send(player, msg("invite-none"));
                return true;
            }
            send(player, "&fDeine Einladungen:");
            for (Clan c : incoming) {
                send(player, "&8- &f[" + c.tag() + "] &7" + c.name());
            }
            return true;
        }

        if (sub.equals("einladung") || sub.equals("invitation")) {
            if (args.length < 3) {
                send(player, "&cBenutzung: /clan einladung annehmen|ablehnen <TAG>");
                return true;
            }
            String action = args[1].toLowerCase(Locale.ROOT);
            String tag = args[2];

            Clan clan = plugin.data().getClanByTag(tag);
            if (clan == null) { send(player, msg("clan-not-found")); return true; }

            Invite inv = clan.invites().get(player.getUniqueId());
            if (inv == null) { send(player, "&cDu hast keine Einladung von diesem Clan."); return true; }

            if (action.equals("annehmen") || action.equals("accept")) {
                if (!has(sender, "plotropolisclan.cmd.invite.accept")) return true;
                if (plugin.data().getClanOf(player.getUniqueId()) != null) {
                    send(player, msg("already-in-clan"));
                    return true;
                }

                clan.invites().remove(player.getUniqueId());
                clan.addMember(player.getUniqueId(), "Mitglied");
                plugin.data().playerClanTag().put(player.getUniqueId(), clan.tag().toLowerCase(Locale.ROOT));
                plugin.data().save();

                send(player, msg("invite-accepted").replace("{tag}", clan.tag()).replace("{name}", clan.name()));

                refreshTagsSoon();
                return true;
            }

            if (action.equals("ablehnen") || action.equals("deny")) {
                if (!has(sender, "plotropolisclan.cmd.invite.deny")) return true;
                clan.invites().remove(player.getUniqueId());
                plugin.data().save();
                send(player, msg("invite-denied"));
                return true;
            }

            send(player, "&cUnbekannte Aktion. Nutze annehmen/ablehnen.");
            return true;
        }

        if (sub.equals("beitreten") || sub.equals("join")) {
            if (!has(sender, "plotropolisclan.cmd.join")) return true;
            if (args.length < 2) {
                send(player, "&cBenutzung: /clan beitreten <TAG>");
                return true;
            }
            if (plugin.data().getClanOf(player.getUniqueId()) != null) {
                send(player, msg("already-in-clan"));
                return true;
            }

            Clan clan = plugin.data().getClanByTag(args[1]);
            if (clan == null) { send(player, msg("clan-not-found")); return true; }

            if (!clan.publicJoin()) {
                send(player, msg("join-closed"));
                return true;
            }

            clan.addMember(player.getUniqueId(), "Mitglied");
            plugin.data().playerClanTag().put(player.getUniqueId(), clan.tag().toLowerCase(Locale.ROOT));
            plugin.data().save();

            send(player, msg("join-success").replace("{tag}", clan.tag()).replace("{name}", clan.name()));

            refreshTagsSoon();
            return true;
        }

        if (sub.equals("kick")) {
            if (!has(sender, "plotropolisclan.cmd.kick")) return true;
            if (args.length < 2) {
                send(player, "&cBenutzung: /clan kick <Spieler>");
                return true;
            }
            Clan clan = plugin.data().getClanOf(player.getUniqueId());
            if (clan == null) { send(player, msg("not-in-clan")); return true; }

            if (!can(clan, player.getUniqueId(), ClanPermission.KICK)) {
                send(player, "&cDu darfst niemanden kicken.");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId() == null) { send(player, "&cSpieler nicht gefunden."); return true; }
            if (!clan.isMember(target.getUniqueId())) { send(player, "&cSpieler ist nicht in deinem Clan."); return true; }
            if (clan.isOwner(target.getUniqueId())) { send(player, "&cDu kannst den Anführer nicht kicken."); return true; }

            clan.removeMember(target.getUniqueId());
            plugin.data().playerClanTag().remove(target.getUniqueId());
            plugin.data().save();

            send(player, msg("kick-success").replace("{player}", target.getName() == null ? args[1] : target.getName()));

            refreshTagsSoon();
            return true;
        }

        if (sub.equals("bank")) {
            return handleBank(player, Arrays.copyOfRange(args, 1, args.length));
        }

        if (sub.equals("aktivität") || sub.equals("aktivitaet") || sub.equals("activity")) {
            if (!has(sender, "plotropolisclan.cmd.activity")) return true;

            Clan clan;
            if (args.length >= 2) clan = plugin.data().getClanByTag(args[1]);
            else clan = plugin.data().getClanOf(player.getUniqueId());

            if (clan == null) { send(player, msg("clan-not-found")); return true; }

            int lastDays = plugin.getConfig().getInt("verification.required.last-days", 14);
            int reqHours = plugin.getConfig().getInt("verification.required.activity-hours", 100);
            long reqKills = plugin.getConfig().getLong("verification.required.kills", 500);
            long reqBank = plugin.getConfig().getLong("verification.required.bank-money", 1_000_000);

            long today = dayEpochUTC();
            long from = today - (lastDays - 1);

            long sec = 0;
            for (var entry : clan.activitySecondsByDay().entrySet()) {
                long d = entry.getKey();
                if (d >= from && d <= today) sec += entry.getValue();
            }
            double hours = sec / 3600.0;

            send(player, "&fAktivität/Verify Check &8(&7" + clan.tag() + "&8)");
            player.sendMessage(ColorUtil.c("&8- &7Letzte " + lastDays + " Tage Aktivität: &f" + String.format(Locale.US, "%.1f", hours) + "h &8/ &f" + reqHours + "h"));
            player.sendMessage(ColorUtil.c("&8- &7Kills: &f" + clan.kills() + " &8/ &f" + reqKills));
            player.sendMessage(ColorUtil.c("&8- &7Clan Bank: &f" + plugin.economy().formatMoney(clan.bankMoney()) + " &8/ &f" + plugin.economy().formatMoney(reqBank)));
            player.sendMessage(ColorUtil.c("&8- &7Verifiziert: &f" + (clan.verified() ? "&aJA" : "&cNEIN")));
            return true;
        }

        if (sub.equals("rolle") || sub.equals("role")) {
            return handleRole(player, Arrays.copyOfRange(args, 1, args.length));
        }

        send(player, "&cUnbekannter Befehl. Nutze /clan help");
        return true;
    }

    private boolean handleBank(Player player, String[] args) {
        Clan clan = plugin.data().getClanOf(player.getUniqueId());
        if (clan == null) { send(player, msg("not-in-clan")); return true; }

        if (args.length == 0) {
            send(player, "&cBenutzung: /clan bank einzahlen|auszahlen|info <Betrag>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("info")) {
            if (!player.hasPermission("plotropolisclan.cmd.bank.info")) { send(player, msg("no-permission")); return true; }
            send(player, msg("bank-info").replace("{bank}", plugin.economy().formatMoney(clan.bankMoney())));
            return true;
        }

        if (sub.equals("einzahlen") || sub.equals("deposit")) {
            if (!player.hasPermission("plotropolisclan.cmd.bank.deposit")) { send(player, msg("no-permission")); return true; }
            if (args.length < 2) { send(player, "&cBenutzung: /clan bank einzahlen <Betrag>"); return true; }

            long amount = parseLong(args[1]);
            if (amount <= 0) { send(player, "&cUngültiger Betrag."); return true; }

            long bal = plugin.economy().getMoney(player.getUniqueId());
            if (bal < amount) { send(player, "&cDu hast nicht genug Geld."); return true; }
            if (!plugin.economy().withdrawMoney(player.getUniqueId(), amount)) { send(player, "&cKonnte nicht abbuchen."); return true; }

            clan.bankMoney(clan.bankMoney() + amount);
            plugin.data().save();

            send(player, msg("bank-deposit-success").replace("{amount}", plugin.economy().formatMoney(amount)));
            refreshTagsSoon();
            return true;
        }

        if (sub.equals("auszahlen") || sub.equals("withdraw")) {
            if (!player.hasPermission("plotropolisclan.cmd.bank.withdraw")) { send(player, msg("no-permission")); return true; }
            if (args.length < 2) { send(player, "&cBenutzung: /clan bank auszahlen <Betrag>"); return true; }

            if (!can(clan, player.getUniqueId(), ClanPermission.BANK_WITHDRAW)) {
                send(player, msg("bank-no-rights"));
                return true;
            }

            long amount = parseLong(args[1]);
            if (amount <= 0) { send(player, "&cUngültiger Betrag."); return true; }
            if (clan.bankMoney() < amount) { send(player, msg("bank-not-enough")); return true; }

            clan.bankMoney(clan.bankMoney() - amount);
            plugin.economy().depositMoney(player.getUniqueId(), amount);
            plugin.data().save();

            send(player, msg("bank-withdraw-success").replace("{amount}", plugin.economy().formatMoney(amount)));
            refreshTagsSoon();
            return true;
        }

        send(player, "&cBenutzung: /clan bank einzahlen|auszahlen|info <Betrag>");
        return true;
    }

    private boolean handleRole(Player player, String[] args) {
        Clan clan = plugin.data().getClanOf(player.getUniqueId());
        if (clan == null) { send(player, msg("not-in-clan")); return true; }
        if (args.length == 0) { send(player, "&cBenutzung: /clan rolle gui|erstellen|löschen|setzen"); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("gui")) {
            plugin.gui().open(player, new RolesGui(plugin, clan));
            return true;
        }

        if (sub.equals("erstellen") || sub.equals("create")) {
            if (!player.hasPermission("plotropolisclan.cmd.role.create")) { send(player, msg("no-permission")); return true; }
            if (!can(clan, player.getUniqueId(), ClanPermission.ROLE_MANAGE)) { send(player, "&cDu darfst keine Rollen erstellen."); return true; }
            if (args.length < 2) { send(player, "&cBenutzung: /clan rolle erstellen <Name>"); return true; }

            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            name = name.replaceAll("\\s+", " ");

            String key = name.toLowerCase(Locale.ROOT);
            if (clan.roles().containsKey(key)) { send(player, "&cDiese Rolle existiert bereits."); return true; }

            int startWeight = plugin.getConfig().getInt("settings.roles.default-new-weight", 20);

            ClanRole role = new ClanRole(name, startWeight);
            clan.roles().put(key, role);
            plugin.data().save();

            send(player, msg("role-created").replace("{role}", name));
            plugin.gui().open(player, new RolesGui(plugin, clan));

            refreshTagsSoon();
            return true;
        }

        if (sub.equals("löschen") || sub.equals("loeschen") || sub.equals("delete")) {
            if (!player.hasPermission("plotropolisclan.cmd.role.delete")) { send(player, msg("no-permission")); return true; }
            if (!can(clan, player.getUniqueId(), ClanPermission.ROLE_MANAGE)) { send(player, "&cDu darfst keine Rollen löschen."); return true; }
            if (args.length < 2) { send(player, "&cBenutzung: /clan rolle löschen <Name>"); return true; }

            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            String key = name.toLowerCase(Locale.ROOT);

            ClanRole role = clan.roles().get(key);
            if (role == null) { send(player, msg("role-not-found")); return true; }
            if (role.name().equalsIgnoreCase("Anführer") || role.name().equalsIgnoreCase("Mitglied")) {
                send(player, "&cDefault-Rollen können nicht gelöscht werden.");
                return true;
            }

            for (var m : clan.members().entrySet()) {
                if (m.getValue().equalsIgnoreCase(role.name())) m.setValue("Mitglied");
            }

            clan.roles().remove(key);
            plugin.data().save();

            send(player, msg("role-deleted").replace("{role}", role.name()));
            refreshTagsSoon();
            return true;
        }

        if (sub.equals("setzen") || sub.equals("set")) {
            if (!player.hasPermission("plotropolisclan.cmd.role.set")) { send(player, msg("no-permission")); return true; }
            if (!can(clan, player.getUniqueId(), ClanPermission.ROLE_ASSIGN)) { send(player, "&cDu darfst keine Rollen vergeben."); return true; }
            if (args.length < 3) { send(player, "&cBenutzung: /clan rolle setzen <RolleName> <Spieler>"); return true; }

            String roleName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
            String roleKey = roleName.toLowerCase(Locale.ROOT);

            ClanRole role = clan.roles().get(roleKey);
            if (role == null) { send(player, msg("role-not-found")); return true; }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[args.length - 1]);
            if (target.getUniqueId() == null) { send(player, "&cSpieler nicht gefunden."); return true; }
            if (!clan.isMember(target.getUniqueId())) { send(player, "&cSpieler ist nicht in deinem Clan."); return true; }
            if (clan.isOwner(target.getUniqueId())) { send(player, "&cDer Anführer behält Anführer-Rolle."); return true; }

            if (!clan.isOwner(player.getUniqueId())) {
                ClanRole self = clan.roleOf(player.getUniqueId());
                if (self == null) { send(player, "&cDu hast keine Clan-Rolle."); return true; }

                ClanRole targetCurrent = clan.roleOf(target.getUniqueId());
                if (targetCurrent != null && targetCurrent.weight() >= self.weight()) {
                    send(player, "&cDu darfst diesen Spieler nicht verwalten (gleich/über dir).");
                    return true;
                }

                if (role.weight() >= self.weight()) {
                    send(player, "&cDu darfst keine Rolle vergeben die gleich/über dir ist.");
                    return true;
                }

                if (role.name().equalsIgnoreCase("Anführer")) {
                    send(player, "&cNur der Clan-Owner kann Anführer vergeben.");
                    return true;
                }
            }

            clan.members().put(target.getUniqueId(), role.name());
            plugin.data().save();

            send(player, msg("role-set")
                    .replace("{role}", role.name())
                    .replace("{player}", target.getName() == null ? args[args.length - 1] : target.getName()));

            refreshTagsSoon();
            return true;
        }

        send(player, "&cBenutzung: /clan rolle gui|erstellen|löschen|setzen");
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (args.length == 0) { send(sender, "&cBenutzung: /clan admin verify|löschen|bank|kick ..."); return true; }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("verify")) {
            if (!has(sender, "plotropolisclan.admin.verify")) return true;
            if (args.length < 2) { send(sender, "&cBenutzung: /clan admin verify <TAG>"); return true; }

            Clan clan = plugin.data().getClanByTag(args[1]);
            if (clan == null) { send(sender, msg("clan-not-found")); return true; }

            clan.verified(true);
            plugin.data().save();

            send(sender, msg("verified-now").replace("{tag}", clan.tag()).replace("{name}", clan.name()));
            refreshTagsSoon();
            return true;
        }

        if (sub.equals("löschen") || sub.equals("loeschen") || sub.equals("delete")) {
            if (!has(sender, "plotropolisclan.admin.delete")) return true;
            if (args.length < 2) { send(sender, "&cBenutzung: /clan admin löschen <TAG>"); return true; }

            Clan clan = plugin.data().getClanByTag(args[1]);
            if (clan == null) { send(sender, msg("clan-not-found")); return true; }

            String tagLower = clan.tag().toLowerCase(Locale.ROOT);
            plugin.data().clansByTag().remove(tagLower);
            for (UUID u : new ArrayList<>(clan.members().keySet())) plugin.data().playerClanTag().remove(u);
            plugin.data().save();

            send(sender, "&aClan gelöscht: &f[" + clan.tag() + "] " + clan.name());
            refreshTagsSoon();
            return true;
        }

        if (sub.equals("bank")) {
            if (!has(sender, "plotropolisclan.admin.bank.set")) return true;
            if (args.length < 4 || !args[1].equalsIgnoreCase("setzen")) {
                send(sender, "&cBenutzung: /clan admin bank setzen <TAG> <Betrag>");
                return true;
            }
            Clan clan = plugin.data().getClanByTag(args[2]);
            if (clan == null) { send(sender, msg("clan-not-found")); return true; }
            long amount = parseLong(args[3]);
            if (amount < 0) amount = 0;
            clan.bankMoney(amount);
            plugin.data().save();
            send(sender, "&aClan Bank gesetzt: &f" + plugin.economy().formatMoney(amount));
            refreshTagsSoon();
            return true;
        }

        if (sub.equals("kick")) {
            if (!has(sender, "plotropolisclan.admin.kick")) return true;
            if (args.length < 2) { send(sender, "&cBenutzung: /clan admin kick <Spieler>"); return true; }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId() == null) { send(sender, "&cSpieler nicht gefunden."); return true; }

            Clan clan = plugin.data().getClanOf(target.getUniqueId());
            if (clan == null) { send(sender, "&cSpieler ist in keinem Clan."); return true; }

            if (clan.isOwner(target.getUniqueId())) {
                send(sender, "&cOwner kann nicht per admin kick entfernt werden (nutze admin löschen Clan).");
                return true;
            }

            clan.removeMember(target.getUniqueId());
            plugin.data().playerClanTag().remove(target.getUniqueId());
            plugin.data().save();

            send(sender, "&aSpieler aus Clan entfernt: &f" + (target.getName() == null ? args[1] : target.getName()));
            refreshTagsSoon();
            return true;
        }

        send(sender, "&cAdmin: /clan admin verify|löschen|bank setzen|kick");
        return true;
    }

    private void help(CommandSender s) {
        send(s, "&f&lPlotropolisClan Hilfe");
        send(s, "&8- &f/clan create <TAG> <Name> &7(erstellt Clan, kostet Geld)");
        send(s, "&8- &f/clan delete &7(nur Owner)");
        send(s, "&8- &f/clan info <TAG>");
        send(s, "&8- &f/clan beitreten <TAG> &7(nur wenn öffentlich)");
        send(s, "&8- &f/clan einladen <Spieler>");
        send(s, "&8- &f/clan einladungen");
        send(s, "&8- &f/clan einladung annehmen|ablehnen <TAG>");
        send(s, "&8- &f/clan kick <Spieler>");
        send(s, "&8- &f/clan mitglieder [TAG] &7(GUI)");
        send(s, "&8- &f/clan bestenliste");
        send(s, "&8- &f/clan bank einzahlen|auszahlen|info <Betrag>");
        send(s, "&8- &f/clan aktivität [TAG]");
        send(s, "&8- &f/clan rolle gui");
        send(s, "&8- &f@clan <Text> &7(Clan-Chat)");
        send(s, "&8- &cAdmin: /clan admin verify|löschen|bank setzen|kick");
    }

    private long parseLong(String s) {
        try {
            s = s.replace(".", "").replace(",", "");
            return Long.parseLong(s);
        } catch (Exception e) {
            return -1;
        }
    }
}
