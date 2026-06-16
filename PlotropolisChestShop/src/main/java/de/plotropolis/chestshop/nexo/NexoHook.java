package de.plotropolis.chestshop.nexo;

import de.plotropolis.chestshop.PlotropolisChestShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

public final class NexoHook {

    private final PlotropolisChestShopPlugin plugin;
    private boolean ready;

    private Class<?> cNexoItems;

    private Method mIdFromItem;

    // verschiedene Varianten je nach Nexo Version
    private Method mItemFromId1;          // itemFromId(String)
    private Method mItemFromId2;          // itemFromId(String, int)
    private Method mItemBuilderFromId;    // itemBuilderFromId(String) o.ä.

    public NexoHook(PlotropolisChestShopPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            ready = false;
            return;
        }

        try {
            cNexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");

            // idFromItem(ItemStack) kann String oder Optional<String> zurückgeben
            mIdFromItem = findMethod(cNexoItems, "idFromItem", ItemStack.class);

            // itemFromId(String) / itemFromId(String,int)
            mItemFromId1 = findMethod(cNexoItems, "itemFromId", String.class);
            mItemFromId2 = findMethod(cNexoItems, "itemFromId", String.class, int.class);

            // manche Nexo Versionen haben Builder-Methoden
            mItemBuilderFromId = findMethod(cNexoItems, "itemBuilderFromId", String.class);
            if (mItemBuilderFromId == null) mItemBuilderFromId = findMethod(cNexoItems, "builderFromId", String.class);
            if (mItemBuilderFromId == null) mItemBuilderFromId = findMethod(cNexoItems, "itemBuilder", String.class);

            ready = true;
            plugin.getLogger().info("Nexo detected. Custom items supported.");
        } catch (Exception e) {
            ready = false;
            plugin.getLogger().warning("Nexo detected but API not found: " + e.getMessage());
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            Method m = clazz.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Exception ignored) { }
        return null;
    }

    public boolean isReady() { return ready; }

    public String idFromItem(ItemStack item) {
        if (!ready || item == null) return null;

        try {
            if (mIdFromItem == null) return null;
            Object r = mIdFromItem.invoke(null, item);

            // String
            if (r instanceof String s && !s.isBlank()) return s;

            // Optional<String>
            if (r instanceof Optional<?> opt) {
                Object v = opt.orElse(null);
                if (v instanceof String s && !s.isBlank()) return s;
            }
        } catch (Exception ignored) { }

        return null;
    }

    public ItemStack itemFromId(String id, int amount) {
        if (!ready || id == null || id.isBlank()) return null;

        int a = Math.max(1, amount);

        try {
            // 1) itemFromId(String, int)
            if (mItemFromId2 != null) {
                Object r = mItemFromId2.invoke(null, id, a);
                ItemStack it = unwrapToItemStack(r);
                if (it != null) return it;
            }

            // 2) itemFromId(String)
            if (mItemFromId1 != null) {
                Object r = mItemFromId1.invoke(null, id);
                ItemStack it = unwrapToItemStack(r);
                if (it != null) {
                    it.setAmount(a);
                    return it;
                }
            }

            // 3) Builder -> build() / toItemStack()
            if (mItemBuilderFromId != null) {
                Object builder = mItemBuilderFromId.invoke(null, id);
                if (builder != null) {
                    // versuche build()
                    Method build = findMethod(builder.getClass(), "build");
                    if (build == null) build = findMethod(builder.getClass(), "toItemStack");
                    if (build == null) build = findMethod(builder.getClass(), "itemStack");
                    if (build != null) {
                        Object r = build.invoke(builder);
                        ItemStack it = unwrapToItemStack(r);
                        if (it != null) {
                            it.setAmount(a);
                            return it;
                        }
                    }
                }
            }
        } catch (Exception ignored) { }

        return null;
    }

    private ItemStack unwrapToItemStack(Object r) {
        if (r == null) return null;

        // direkt ItemStack
        if (r instanceof ItemStack it) return it;

        // Optional<ItemStack>
        if (r instanceof Optional<?> opt) {
            Object v = opt.orElse(null);
            if (v instanceof ItemStack it) return it;
        }

        // manche APIs geben Wrapper zurück, der getItemStack()/getStack()/build() hat
        try {
            Method m = findMethod(r.getClass(), "getItemStack");
            if (m == null) m = findMethod(r.getClass(), "getStack");
            if (m == null) m = findMethod(r.getClass(), "build");
            if (m == null) m = findMethod(r.getClass(), "toItemStack");
            if (m != null) {
                Object v = m.invoke(r);
                if (v instanceof ItemStack it) return it;
                if (v instanceof Optional<?> opt2) {
                    Object vv = opt2.orElse(null);
                    if (vv instanceof ItemStack it) return it;
                }
            }
        } catch (Exception ignored) { }

        return null;
    }
}
