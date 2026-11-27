package io.github.earlgalvan.antitheft;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reads and writes locks on block entities. Lock data lives only in the block's PDC. */
public final class Locks {

    public static final String BYPASS = "antitheft.bypass";

    /** 1.0.0 stored access as a PDC string list, which servers before 1.20.4 cannot read. */
    private static final PersistentDataType<?, List<String>> LEGACY_LIST = legacyListType();

    private final NamespacedKey ownerKey;
    private final NamespacedKey membersKey; // access list, comma-separated UUIDs (1.1.0+)
    private final NamespacedKey legacyAccessKey; // access list as written by 1.0.0
    private final NamespacedKey placerKey;
    private final NamespacedKey coOwnersKey; // co-owners, comma-separated UUIDs (1.3.0+)
    private final NamespacedKey previousOwnerKey; // set by /unlock so the same owner's re-lock restores access (1.3.0+)
    private final TrustStore trust;
    private volatile Set<Material> lockable = EnumSet.noneOf(Material.class);

    public Locks(Plugin plugin, TrustStore trust) {
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.membersKey = new NamespacedKey(plugin, "members");
        this.legacyAccessKey = new NamespacedKey(plugin, "access");
        this.placerKey = new NamespacedKey(plugin, "placer");
        this.coOwnersKey = new NamespacedKey(plugin, "coowners");
        this.previousOwnerKey = new NamespacedKey(plugin, "previous-owner");
        this.trust = trust;
    }

    public void setLockable(Set<Material> materials) {
        this.lockable = materials.isEmpty() ? EnumSet.noneOf(Material.class) : EnumSet.copyOf(materials);
    }

    public boolean isLockable(Material material) {
        return lockable.contains(material);
    }

    /** Who placed a hopper (block PDC) or a hopper minecart (entity PDC). */
    public NamespacedKey placerKey() {
        return placerKey;
    }

    /** The player who placed this hopper or hopper minecart, or null (placed before 1.2.0, or not by a player). */
    public UUID placer(PersistentDataHolder holder) {
        String s = holder.getPersistentDataContainer().get(placerKey, PersistentDataType.STRING);
        return s == null ? null : UUID.fromString(s);
    }

    // ---- reading ----

    /** Lock on this block or, for a double chest, on its other half. Null if unlocked or not lockable. */
    public Lock read(Block block) {
        if (!isLockable(block.getType())) return null;
        Lock lock = readOne(block);
        if (lock != null) return lock;
        Block partner = partner(block);
        return partner == null ? null : readOne(partner);
    }

    private Lock readOne(Block block) {
        BlockState state = block.getState(false); // no snapshot copy, read only
        return state instanceof TileState tile ? read(tile) : null;
    }

    public Lock read(PersistentDataHolder holder) {
        PersistentDataContainer pdc = holder.getPersistentDataContainer();
        String owner = pdc.get(ownerKey, PersistentDataType.STRING);
        return owner == null ? null : new Lock(UUID.fromString(owner), coOwners(pdc), access(pdc));
    }

    /**
     * For /lock on an unlocked block: the co-owners and access list this player had on it before
     * they /unlock-ed it, or null if they didn't (then the new lock starts empty).
     */
    public Lock remembered(Block block, UUID locker) {
        for (Block b : new Block[]{block, partner(block)}) {
            if (b == null || !(b.getState(false) instanceof TileState tile)) continue;
            PersistentDataContainer pdc = tile.getPersistentDataContainer();
            if (locker.toString().equals(pdc.get(previousOwnerKey, PersistentDataType.STRING))) {
                return new Lock(locker, coOwners(pdc), access(pdc));
            }
        }
        return null;
    }

    private List<UUID> access(PersistentDataContainer pdc) {
        String members = pdc.get(membersKey, PersistentDataType.STRING);
        if (members != null) return uuids(members);
        if (LEGACY_LIST != null && pdc.has(legacyAccessKey, LEGACY_LIST)) {
            List<UUID> out = new ArrayList<>();
            for (String s : pdc.get(legacyAccessKey, LEGACY_LIST)) out.add(UUID.fromString(s));
            return out;
        }
        return List.of();
    }

    private List<UUID> coOwners(PersistentDataContainer pdc) {
        String s = pdc.get(coOwnersKey, PersistentDataType.STRING);
        return s == null ? List.of() : uuids(s);
    }

    private static List<UUID> uuids(String csv) {
        if (csv.isEmpty()) return List.of();
        List<UUID> out = new ArrayList<>();
        for (String s : Arrays.asList(csv.split(","))) out.add(UUID.fromString(s));
        return out;
    }

    // ---- writing ----

    /** Writes the lock (or removes all lock data when {@code lock} is null) on the block and its double-chest partner. */
    public void write(Block block, Lock lock) {
        writeOne(block, lock);
        Block partner = partner(block);
        if (partner != null) writeOne(partner, lock);
    }

    /** Writes only this block, never its partner. */
    public void writeOne(Block block, Lock lock) {
        if (!(block.getState() instanceof TileState tile)) return;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        pdc.remove(legacyAccessKey); // rewritten in the new format below
        pdc.remove(previousOwnerKey);
        if (lock == null) {
            pdc.remove(ownerKey);
            pdc.remove(membersKey);
            pdc.remove(coOwnersKey);
        } else {
            pdc.set(ownerKey, PersistentDataType.STRING, lock.owner().toString());
            setList(pdc, membersKey, lock.access());
            setList(pdc, coOwnersKey, lock.coOwners());
        }
        tile.update(false, false);
    }

    /**
     * /unlock: removes the lock but remembers its co-owners and access list on the block, so when the
     * same owner locks it again they come back. A different player locking it starts fresh.
     */
    public void unlockRemembering(Block block, Lock lock) {
        for (Block b : new Block[]{block, partner(block)}) {
            if (b == null || !(b.getState() instanceof TileState tile)) continue;
            PersistentDataContainer pdc = tile.getPersistentDataContainer();
            pdc.remove(legacyAccessKey);
            pdc.remove(ownerKey);
            pdc.set(previousOwnerKey, PersistentDataType.STRING, lock.owner().toString());
            setList(pdc, membersKey, lock.access());
            setList(pdc, coOwnersKey, lock.coOwners());
            tile.update(false, false);
        }
    }

    private static void setList(PersistentDataContainer pdc, NamespacedKey key, List<UUID> ids) {
        if (ids.isEmpty()) pdc.remove(key);
        else pdc.set(key, PersistentDataType.STRING, String.join(",", ids.stream().map(UUID::toString).toList()));
    }

    @SuppressWarnings("unchecked")
    private static PersistentDataType<?, List<String>> legacyListType() {
        try {
            var field = PersistentDataType.class.getField("LIST"); // Paper 1.20.4+
            return (PersistentDataType<?, List<String>>) field.getType().getMethod("strings").invoke(field.get(null));
        } catch (ReflectiveOperationException e) {
            return null; // older server: no 1.0.0 data can exist here
        }
    }

    // ---- access ----

    /** Owner powers on this lock: the owner, its co-owners, and the owner's global co-owners. */
    public boolean canManage(UUID player, Lock lock) {
        return player.equals(lock.owner())
                || lock.coOwners().contains(player)
                || trust.isCoOwner(lock.owner(), player);
    }

    /** May open it: anyone who can manage it, its access list, and players the owner trusts. */
    public boolean canAccess(UUID player, Lock lock) {
        return canManage(player, lock)
                || lock.access().contains(player)
                || trust.isTrusted(lock.owner(), player);
    }

    public boolean canAccess(Player player, Lock lock) {
        return player.hasPermission(BYPASS) || canAccess(player.getUniqueId(), lock);
    }

    // ---- double chests ----

    /** The other half of a double chest (also copper and trapped chests), or null. */
    public Block partner(Block block) {
        if (!(block.getBlockData() instanceof Chest chest) || chest.getType() == Chest.Type.SINGLE) return null;
        BlockFace facing = chest.getFacing();
        // Same rule as vanilla ChestBlock.getConnectedDirection.
        BlockFace dir = chest.getType() == Chest.Type.LEFT ? clockwise(facing) : counterClockwise(facing);
        Block other = block.getRelative(dir);
        return other.getBlockData() instanceof Chest ? other : null;
    }

    private static BlockFace clockwise(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private static BlockFace counterClockwise(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }
}
