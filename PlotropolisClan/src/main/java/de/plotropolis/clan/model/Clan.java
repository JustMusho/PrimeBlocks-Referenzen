package de.plotropolis.clan.model;

import java.util.*;

public final class Clan {

    private final String tag;
    private final String name;
    private final UUID owner;

    private final String createdServer;

    private boolean verified;
    private boolean publicJoin;

    private long bankMoney;

    private final Map<UUID, String> members = new HashMap<>();
    private final Map<String, ClanRole> roles = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    private long kills;
    private final Map<Long, Long> activitySecondsByDay = new HashMap<>();

    public Clan(String tag, String name, UUID owner, String createdServer) {
        this.tag = tag;
        this.name = name;
        this.owner = owner;
        this.createdServer = (createdServer == null || createdServer.trim().isEmpty())
                ? "default"
                : createdServer.trim();

        this.verified = false;
        this.publicJoin = false;

        ClanRole leader = new ClanRole("Anführer", 100);
        leader.set(ClanPermission.INVITE, true);
        leader.set(ClanPermission.KICK, true);
        leader.set(ClanPermission.BANK_WITHDRAW, true);
        leader.set(ClanPermission.ROLE_MANAGE, true);
        leader.set(ClanPermission.ROLE_ASSIGN, true);
        leader.set(ClanPermission.SETTINGS_PUBLIC_JOIN, true);
        roles.put(key(leader.name()), leader);

        ClanRole member = new ClanRole("Mitglied", 10);
        roles.put(key(member.name()), member);

        members.put(owner, leader.name());
    }

    public Clan(String tag, String name, UUID owner) {
        this(tag, name, owner, "default");
    }

    private String key(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    public String tag() { return tag; }
    public String name() { return name; }
    public UUID owner() { return owner; }

    public String createdServer() { return createdServer; }

    public boolean verified() { return verified; }
    public void verified(boolean v) { this.verified = v; }

    public boolean publicJoin() { return publicJoin; }
    public void publicJoin(boolean v) { this.publicJoin = v; }

    public long bankMoney() { return bankMoney; }
    public void bankMoney(long v) { this.bankMoney = Math.max(0, v); }

    public Map<UUID, String> members() { return members; }

    public Map<String, ClanRole> roles() { return roles; }

    public ClanRole roleOf(UUID player) {
        String rn = members.get(player);
        if (rn == null) return null;
        return roles.get(key(rn));
    }

    public boolean isMember(UUID u) { return members.containsKey(u); }

    public void addMember(UUID u, String roleName) { members.put(u, roleName); }
    public void removeMember(UUID u) { members.remove(u); }

    public boolean isOwner(UUID u) { return owner.equals(u); }

    public Map<UUID, Invite> invites() { return invites; }

    public long kills() { return kills; }
    public void addKill() { kills++; }
    public void kills(long k) { kills = Math.max(0, k); }

    public Map<Long, Long> activitySecondsByDay() { return activitySecondsByDay; }

    public void addActivitySeconds(long dayEpoch, long seconds) {
        activitySecondsByDay.merge(dayEpoch, seconds, Long::sum);
    }

    public boolean canAssignRole(UUID actor, ClanRole targetRole) {
        if (actor == null || targetRole == null) return false;

        if (isOwner(actor)) return true;

        ClanRole self = roleOf(actor);
        if (self == null) return false;

        if (!self.has(ClanPermission.ROLE_ASSIGN)) return false;

        if ("Anführer".equalsIgnoreCase(targetRole.name())) return false;

        return self.weight() > targetRole.weight();
    }
}
