package io.github.earlgalvan.antitheft;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

import static io.github.earlgalvan.antitheft.Messages.ph;

public final class ProtectionListener implements Listener {

    private final AntitheftPlugin plugin;
    private final Locks locks;
    private final Messages messages;
    private final TrustStore trust;

    public ProtectionListener(AntitheftPlugin plugin) {
        this.plugin = plugin;
        this.locks = plugin.locks();
        this.messages = plugin.messages();
        this.trust = plugin.trust();
    }

    // ---- opening ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.useInteractedBlock() == Event.Result.DENY) return;
        Lock lock = locks.read(e.getClickedBlock());
        if (lock == null || locks.canAccess(e.getPlayer(), lock)) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        if (e.getHand() == EquipmentSlot.HAND) {
            messages.send(e.getPlayer(), "denied-open", ph("owner", AntitheftPlugin.nameOf(lock.owner())));
        }
    }

    // ---- breaking ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Lock lock = locks.read(e.getBlock());
        if (lock == null) return;
        Player p = e.getPlayer();
        if (p.getUniqueId().equals(lock.owner()) || p.hasPermission(Locks.BYPASS)) return;
        e.setCancelled(true);
        messages.send(p, "denied-break", ph("owner", AntitheftPlugin.nameOf(lock.owner())));
    }

    /** A broken shulker box must not carry its lock into the dropped item. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShulkerBroken(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (Tag.SHULKER_BOXES.isTagged(b.getType()) && locks.read(b) != null) locks.writeOne(b, null);
    }

    /** Fire would destroy flammable lockable blocks (lecterns, bookshelves, shelves) and their items. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (locks.read(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> locks.read(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> locks.read(b) != null);
    }

    // ---- placing ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        Player p = e.getPlayer();
        if (b.getType() == Material.HOPPER) recordPlacer(b, p);
        if (!locks.isLockable(b.getType())) return;

        Block partner = locks.partner(b);
        Lock partnerLock = partner == null ? null : locks.read(partner);
        if (partnerLock != null) {
            if (!locks.canAccess(p, partnerLock)) {
                e.setCancelled(true);
                messages.send(p, "denied-connect", ph("owner", AntitheftPlugin.nameOf(partnerLock.owner())));
                return;
            }
            locks.writeOne(b, partnerLock); // new half joins the existing lock
            return;
        }
        if (plugin.autoLock() && p.hasPermission("antitheft.lock")) {
            locks.write(b, new Lock(p.getUniqueId(), List.of())); // block entity exists when this event fires
            messages.send(p, "auto-locked");
        }
    }

    /** Remembers who placed a hopper, so a player's own hoppers work with their locked blocks without /lock. */
    private void recordPlacer(Block hopper, Player p) {
        if (!(hopper.getState() instanceof TileState tile)) return; // block entity exists when this event fires
        tile.getPersistentDataContainer().set(locks.placerKey(), PersistentDataType.STRING, p.getUniqueId().toString());
        tile.update(false, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent e) {
        if (e.getEntity() instanceof HopperMinecart cart && e.getPlayer() != null) {
            cart.getPersistentDataContainer().set(locks.placerKey(), PersistentDataType.STRING,
                    e.getPlayer().getUniqueId().toString());
        }
    }

    // ---- hoppers (hot path) ----

    /**
     * Only pulls out of a locked block are checked; pushing items into a locked block is always allowed.
     * A pull is allowed when the hopper's owner (its lock owner, otherwise whoever placed it) has access.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        Inventory dest = e.getDestination();
        if (!dest.equals(e.getInitiator())) return; // a push, not a pull
        Lock lock = lockOf(e.getSource().getHolder(false));
        if (lock == null) return;
        UUID puller = pullerOf(dest.getHolder(false));
        if (puller == null || !locks.canAccess(puller, lock)) e.setCancelled(true);
    }

    private Lock lockOf(InventoryHolder holder) {
        if (holder instanceof DoubleChest dc) holder = dc.getLeftSide(false);
        if (holder instanceof TileState tile && locks.isLockable(tile.getType())) return locks.read(tile);
        return null;
    }

    /** Whose hopper this is: the owner of its lock, otherwise the player who placed it. */
    private UUID pullerOf(InventoryHolder holder) {
        if (holder instanceof TileState tile) {
            Lock l = locks.read(tile);
            return l != null ? l.owner() : locks.placer(tile);
        }
        if (holder instanceof HopperMinecart cart) return locks.placer(cart);
        return null;
    }

    // ---- trust cache ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) trust.loadBlocking(e.getUniqueId());
    }

    /** Fallback for logins where the pre-login load did not run (e.g. another plugin's login flow). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        trust.loadIfMissing(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        trust.unload(e.getPlayer().getUniqueId());
    }
}
