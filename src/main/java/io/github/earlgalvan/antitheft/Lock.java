package io.github.earlgalvan.antitheft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owner and per-lock access list, as stored in a block entity's PersistentDataContainer. */
public record Lock(UUID owner, List<UUID> access) {

    public Lock {
        access = List.copyOf(access);
    }

    public Lock withAccess(UUID player, boolean add) {
        List<UUID> next = new ArrayList<>(access);
        if (add) next.add(player); else next.remove(player);
        return new Lock(owner, next);
    }
}
