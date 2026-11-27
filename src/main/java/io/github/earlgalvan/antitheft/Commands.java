package io.github.earlgalvan.antitheft;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.earlgalvan.antitheft.Messages.ph;

/** /lock, /unlock, /lockinfo, /trust, /untrust, /trustlist, /antitheft. */
public final class Commands implements TabExecutor {

    private static final int REACH = 5;

    private final AntitheftPlugin plugin;
    private final Locks locks;
    private final Messages msg;
    private final TrustStore trust;

    public Commands(AntitheftPlugin plugin) {
        this.plugin = plugin;
        this.locks = plugin.locks();
        this.msg = plugin.messages();
        this.trust = plugin.trust();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("antitheft")) {
            if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
                msg.send(sender, "usage-antitheft");
            } else {
                plugin.reloadAll();
                msg.send(sender, "reloaded");
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            msg.send(sender, "players-only");
            return true;
        }
        switch (name) {
            case "lock" -> lock(player, args);
            case "unlock" -> unlock(player);
            case "lockinfo" -> info(player);
            case "trust" -> trust(player, label, args, true);
            case "untrust" -> trust(player, label, args, false);
            case "trustlist" -> trustList(player);
            default -> { }
        }
        return true;
    }

    // ---- /lock ----

    private void lock(Player p, String[] args) {
        if (args.length == 0) {
            lockTarget(p);
        } else if (args.length == 1 && args[0].equalsIgnoreCase("admin-unlock")) {
            adminUnlock(p);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            editAccess(p, args[1], args[0].equalsIgnoreCase("add"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            giveOwnership(p, args[1]);
        } else {
            msg.send(p, "usage-lock");
        }
    }

    private void lockTarget(Player p) {
        Block b = target(p);
        if (b == null) return;
        Lock lock = locks.read(b);
        if (lock != null) {
            msg.send(p, lock.owner().equals(p.getUniqueId()) ? "already-locked-self" : "already-locked-other",
                    ph("owner", AntitheftPlugin.nameOf(lock.owner())));
            return;
        }
        locks.write(b, new Lock(p.getUniqueId(), List.of()));
        msg.send(p, "locked");
    }

    private void editAccess(Player p, String targetName, boolean add) {
        Block b = target(p);
        if (b == null) return;
        Lock lock = ownedLock(p, b);
        if (lock == null) return;
        OfflinePlayer other = resolve(p, targetName);
        if (other == null) return;
        UUID id = other.getUniqueId();
        String shown = AntitheftPlugin.nameOf(id);
        if (id.equals(lock.owner())) {
            msg.send(p, "cannot-target-self");
        } else if (add == lock.access().contains(id)) {
            msg.send(p, add ? "access-already" : "access-missing", ph("player", shown));
        } else {
            locks.write(b, lock.withAccess(id, add));
            msg.send(p, add ? "access-added" : "access-removed", ph("player", shown));
        }
    }

    /** Hands the lock to another player. The new owner leaves the access list; the old owner keeps no access. */
    private void giveOwnership(Player p, String targetName) {
        Block b = target(p);
        if (b == null) return;
        Lock lock = ownedLock(p, b);
        if (lock == null) return;
        OfflinePlayer other = resolve(p, targetName);
        if (other == null) return;
        UUID id = other.getUniqueId();
        if (id.equals(lock.owner())) {
            msg.send(p, "cannot-target-self");
            return;
        }
        locks.write(b, new Lock(id, lock.withAccess(id, false).access()));
        msg.send(p, "ownership-given", ph("player", AntitheftPlugin.nameOf(id)));
    }

    private void adminUnlock(Player p) {
        if (!p.hasPermission("antitheft.adminunlock")) {
            msg.send(p, "no-permission");
            return;
        }
        Block b = target(p);
        if (b == null) return;
        Lock lock = locks.read(b);
        if (lock == null) {
            msg.send(p, "not-locked");
            return;
        }
        locks.write(b, null);
        msg.send(p, "admin-unlocked", ph("owner", AntitheftPlugin.nameOf(lock.owner())));
    }

    // ---- /unlock, /lockinfo ----

    private void unlock(Player p) {
        Block b = target(p);
        if (b == null) return;
        if (ownedLock(p, b) == null) return;
        locks.write(b, null);
        msg.send(p, "unlocked");
    }

    private void info(Player p) {
        Block b = target(p);
        if (b == null) return;
        Lock lock = locks.read(b);
        if (lock == null) {
            msg.send(p, "not-locked");
            return;
        }
        msg.send(p, "info-owner", ph("owner", AntitheftPlugin.nameOf(lock.owner())));
        // Only the owner and admins see who else has access.
        if (p.getUniqueId().equals(lock.owner()) || p.hasPermission(Locks.BYPASS)) {
            msg.send(p, "info-access", ph("list", names(lock.access())));
        }
    }

    // ---- /trust, /untrust, /trustlist ----

    private void trust(Player p, String label, String[] args, boolean add) {
        if (args.length != 1) {
            msg.send(p, "usage-trust", ph("command", label));
            return;
        }
        OfflinePlayer other = resolve(p, args[0]);
        if (other == null) return;
        UUID id = other.getUniqueId();
        String shown = AntitheftPlugin.nameOf(id);
        if (id.equals(p.getUniqueId())) {
            msg.send(p, "cannot-target-self");
        } else if (trust.setTrust(p.getUniqueId(), id, add)) {
            msg.send(p, add ? "trusted" : "untrusted", ph("player", shown));
        } else {
            msg.send(p, add ? "trust-already" : "trust-missing", ph("player", shown));
        }
    }

    private void trustList(Player p) {
        var list = trust.trustedBy(p.getUniqueId());
        if (list.isEmpty()) msg.send(p, "trustlist-empty");
        else msg.send(p, "trustlist", ph("list", names(list)));
    }

    // ---- helpers ----

    /** The lockable block the player looks at, or null after telling them why not. */
    private Block target(Player p) {
        Block b = p.getTargetBlockExact(REACH);
        if (b == null || b.getType().isAir()) {
            msg.send(p, "no-target");
            return null;
        }
        if (!locks.isLockable(b.getType())) {
            msg.send(p, "not-lockable");
            return null;
        }
        return b;
    }

    /** The block's lock if the player owns it, or null after telling them why not. */
    private Lock ownedLock(Player p, Block b) {
        Lock lock = locks.read(b);
        if (lock == null) {
            msg.send(p, "not-locked");
            return null;
        }
        if (!lock.owner().equals(p.getUniqueId())) {
            msg.send(p, "not-owner");
            return null;
        }
        return lock;
    }

    /** Online player, or one who has joined before. Never looks names up online. */
    private OfflinePlayer resolve(Player p, String name) {
        OfflinePlayer other = Bukkit.getPlayerExact(name);
        if (other == null) other = Bukkit.getOfflinePlayerIfCached(name);
        if (other == null) msg.send(p, "unknown-player", ph("player", name));
        return other;
    }

    private String names(Collection<UUID> ids) {
        if (ids.isEmpty()) return msg.raw("info-none");
        return ids.stream().map(AntitheftPlugin::nameOf).sorted().collect(Collectors.joining(", "));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("antitheft")) return args.length == 1 ? filter(List.of("reload"), args[0]) : List.of();
        if (name.equals("lock")) {
            if (args.length == 1) {
                List<String> subs = sender.hasPermission("antitheft.adminunlock")
                        ? List.of("add", "remove", "give", "admin-unlock") : List.of("add", "remove", "give");
                return filter(subs, args[0]);
            }
            if (args.length == 2 && !args[0].equalsIgnoreCase("admin-unlock")) return null; // online names
            return List.of();
        }
        if (name.equals("trust") || name.equals("untrust")) return args.length == 1 ? null : List.of();
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(p)).toList();
    }
}
