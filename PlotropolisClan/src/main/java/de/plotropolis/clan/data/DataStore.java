package de.plotropolis.clan.data;

import de.plotropolis.clan.model.Clan;

import java.util.*;

public interface DataStore extends AutoCloseable {

    void load();

    void save();

    default void markDirty() {  }

    default boolean isMySql() { return false; }

    Map<String, Clan> clansByTag();
    Map<UUID, String> playerClanTag();

    default Clan getClanByTag(String tag) {
        if (tag == null) return null;
        return clansByTag().get(tag.toLowerCase(Locale.ROOT));
    }


    default Clan getClanOf(UUID player) {
        if (player == null) return null;
        String tag = playerClanTag().get(player);
        if (tag == null) return null;
        return clansByTag().get(tag);
    }

    @Override
    default void close() {
    }
}
