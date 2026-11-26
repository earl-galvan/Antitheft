package io.github.earlgalvan.antitheft;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Stops copper golems from taking items out of locked blocks.
 * The event only exists on Paper 1.21.9+, so it is looked up at runtime and
 * nothing is registered on older servers.
 */
final class GolemGuard implements Listener {

    private static final String EVENT = "io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent";

    private GolemGuard() {
    }

    static boolean register(AntitheftPlugin plugin) {
        Class<? extends Event> type;
        MethodHandle getBlock, isAllowed, setAllowed;
        try {
            type = Class.forName(EVENT).asSubclass(Event.class);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getBlock = lookup.findVirtual(type, "getBlock", MethodType.methodType(Block.class));
            isAllowed = lookup.findVirtual(type, "isAllowed", MethodType.methodType(boolean.class));
            setAllowed = lookup.findVirtual(type, "setAllowed", MethodType.methodType(void.class, boolean.class));
        } catch (ReflectiveOperationException e) {
            return false; // no copper golems on this server version
        }
        Locks locks = plugin.locks();
        plugin.getServer().getPluginManager().registerEvent(type, new GolemGuard(), EventPriority.HIGH, (listener, event) -> {
            if (!type.isInstance(event)) return;
            try {
                if (!(boolean) isAllowed.invoke(event)) return;
                if (locks.read((Block) getBlock.invoke(event)) == null) return;
                // A golem carrying an item is depositing, which is allowed; an empty-handed one would take.
                if (holdsItem(((EntityEvent) event).getEntity())) return;
                setAllowed.invoke(event, false);
            } catch (Throwable t) {
                throw new org.bukkit.event.EventException(t);
            }
        }, plugin, false);
        return true;
    }

    private static boolean holdsItem(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        EntityEquipment eq = living.getEquipment();
        if (eq == null) return false;
        ItemStack hand = eq.getItemInMainHand();
        return !hand.getType().isAir();
    }
}
