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
    private final TrustStore trust;
    private volatile Set<Material> lockable = EnumSet.noneOf(Material.class);

    public Locks(Plugin plugin, TrustStore trust) {
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.membersKey = new NamespacedKey(plugin, "members");
        this.legacyAccessKey = new NamespacedKey(plugin, "access");
        this.placerKey = new NamespacedKey(plugin, "placer");
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
        if (owner == null) return null;
        String members = pdc.get(membersKey, PersistentDataType.STRING);
        List<String> raw;
        if (members != null) {
            raw = members.isEmpty() ? List.of() : Arrays.asList(members.split(","));
        } else if (LEGACY_LIST != null && pdc.has(legacyAccessKey, LEGACY_LIST)) {
            raw = pdc.get(legacyAccessKey, LEGACY_LIST);
        } else {
            raw = List.of();
        }
        List<UUID> access = new ArrayList<>(raw.size());
        for (String s : raw) access.add(UUID.fromString(s));
        return new Lock(UUID.fromString(owner), access);
    }

    // ---- writing ----

    /** Writes the lock (or removes it when {@code lock} is null) on the block and its double-chest partner. */
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
        if (lock == null) {
            pdc.remove(ownerKey);
            pdc.remove(membersKey);
        } else {
            pdc.set(ownerKey, PersistentDataType.STRING, lock.owner().toString());
            if (lock.access().isEmpty()) {
                pdc.remove(membersKey);
            } else {
                pdc.set(membersKey, PersistentDataType.STRING,
                        String.join(",", lock.access().stream().map(UUID::toString).toList()));
            }
        }
        tile.update(false, false);
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

    public boolean canAccess(UUID player, Lock lock) {
        return player.equals(lock.owner())
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
