package io.github.earlgalvan.antitheft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A lock as stored in a block entity's PersistentDataContainer: the owner, the co-owners
 * (owner powers on this lock), and the access list (may open it).
 */
public record Lock(UUID owner, List<UUID> coOwners, List<UUID> access) {

    public Lock {
        coOwners = List.copyOf(coOwners);
        access = List.copyOf(access);
    }

    public Lock(UUID owner, List<UUID> access) {
        this(owner, List.of(), access);
    }

    public Lock(UUID owner) {
        this(owner, List.of(), List.of());
    }

    public Lock withAccess(UUID player, boolean add) {
        return new Lock(owner, coOwners, edit(access, player, add));
    }

    public Lock withCoOwner(UUID player, boolean add) {
        return new Lock(owner, edit(coOwners, player, add), access);
    }

    private static List<UUID> edit(List<UUID> list, UUID player, boolean add) {
        List<UUID> next = new ArrayList<>(list);
        next.remove(player);
        if (add) next.add(player);
        return next;
    }
}
