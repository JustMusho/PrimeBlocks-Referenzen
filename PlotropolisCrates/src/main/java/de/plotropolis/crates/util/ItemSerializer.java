package de.plotropolis.crates.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;

public final class ItemSerializer {

    private ItemSerializer() {}

    public static String toBase64(ItemStack item) {
        if (item == null) return "";
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {

            oos.writeObject(item);
            oos.flush();

            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack fromBase64(String base64) {
        if (base64 == null || base64.isBlank()) return null;

        try {
            String clean = base64.replaceAll("\\s+", "");

            byte[] data = Base64.getMimeDecoder().decode(clean);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                 BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {

                Object o = ois.readObject();
                if (o instanceof ItemStack it) return it;
                return null;
            }

        } catch (Exception e) {
            return null;
        }
    }
}
