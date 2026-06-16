package de.plotropolis.crates.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class LocKey {

    private LocKey() {}

    public static String of(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public static Location toLocation(String key) {
        try {
            String[] p = key.split(";");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            int z = Integer.parseInt(p[3]);
            return new Location(w, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
}
