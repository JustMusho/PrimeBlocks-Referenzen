package de.plotropolis.crates.crates;

import org.bukkit.inventory.ItemStack;

public final class CrateItem {
    private int slot;
    private Rarity rarity;
    private ItemStack item;
    private String command; // optional; if set => execute instead of giving item

    public CrateItem(int slot, Rarity rarity, ItemStack item, String command) {
        this.slot = slot;
        this.rarity = rarity;
        this.item = item;
        this.command = command;
    }

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }

    public Rarity getRarity() { return rarity; }
    public void setRarity(Rarity rarity) { this.rarity = rarity; }

    public ItemStack getItem() { return item; }
    public void setItem(ItemStack item) { this.item = item; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public boolean isCommandReward() { return command != null && !command.isBlank(); }
}
