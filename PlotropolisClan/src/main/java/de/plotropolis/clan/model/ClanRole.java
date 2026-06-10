package de.plotropolis.clan.model;

import java.util.EnumSet;
import java.util.Set;

public final class ClanRole {
    private final String name;
    private int weight;
    private final Set<ClanPermission> perms = EnumSet.noneOf(ClanPermission.class);

    public ClanRole(String name, int weight) {
        this.name = name;
        this.weight = weight;
    }

    public String name() { return name; }
    public int weight() { return weight; }
    public void weight(int w) { this.weight = w; }

    public Set<ClanPermission> perms() { return perms; }
    public boolean has(ClanPermission p) { return perms.contains(p); }

    public void set(ClanPermission p, boolean value) {
        if (value) perms.add(p); else perms.remove(p);
    }
}
