package io.github.earlgalvan.antitheft;

import io.github.earlgalvan.antitheft.TrustStore.Kind;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.earlgalvan.antitheft.Messages.ph;

/** /lock, /unlock, /lockinfo, /trust, /untrust, /trustlist, /antitheft. */
public final class Commands implements TabExecutor {

    private static final int REACH = 5;
    private static final String OWNERSHIP = "ownership";

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
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (args.length == 0) {
            lockTarget(p);
        } else if (args.length == 1 && sub.equals("help")) {
            help(p);
        } else if (args.length == 1 && sub.equals("admin-unlock")) {
            adminUnlock(p);
        } else if (args.length == 3 && sub.equals("give") && args[1].equalsIgnoreCase(OWNERSHIP)) {
            editCoOwner(p, args[2], true);
        } else if (args.length == 3 && sub.equals("remove") && args[1].equalsIgnoreCase(OWNERSHIP)) {
            editCoOwner(p, args[2], false);
        } else if (args.length == 2 && (sub.equals("add") || sub.equals("remove"))) {
            editAccess(p, args[1], sub.equals("add"));
        } else if (sub.equals("give")) {
            msg.send(p, "usage-give");
        } else {
            msg.send(p, "usage-lock");
        }
    }

    private void help(Player p) {
        msg.sendPlain(p, "help");
        if (p.hasPermission("antitheft.adminunlock") || p.hasPermission("antitheft.reload")) msg.sendPlain(p, "help-admin");
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
        // Re-locking your own block brings back the co-owners and access list it had before /unlock.
        Lock previous = locks.remembered(b, p.getUniqueId());
        locks.write(b, previous != null ? previous : new Lock(p.getUniqueId()));
        msg.send(p, previous != null && (!previous.access().isEmpty() || !previous.coOwners().isEmpty())
                ? "locked-restored" : "locked");
    }

    /** /lock add|remove: owner and co-owners. */
    private void editAccess(Player p, String targetName, boolean add) {
        Block b = target(p);
        if (b == null) return;
        Lock lock = managedLock(p, b);
        if (lock == null) return;
        OfflinePlayer other = resolve(p, targetName);
        if (other == null) return;
        UUID id = other.getUniqueId();
        String shown = AntitheftPlugin.nameOf(id);
        if (id.equals(lock.owner()) || id.equals(p.getUniqueId())) {
            msg.send(p, "cannot-target-self");
        } else if (add == lock.access().contains(id)) {
            msg.send(p, add ? "access-already" : "access-missing", ph("player", shown));
        } else {
            locks.write(b, lock.withAccess(id, add));
            msg.send(p, add ? "access-added" : "access-removed", ph("player", shown));
        }
    }

    /** /lock give|remove ownership: only the real owner chooses co-owners. */
    private void editCoOwner(Player p, String targetName, boolean add) {
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
        } else if (add == lock.coOwners().contains(id)) {
            msg.send(p, add ? "coowner-already" : "coowner-missing", ph("player", shown));
        } else {
            Lock next = lock.withCoOwner(id, add);
            if (add) next = next.withAccess(id, false); // a co-owner doesn't need a separate access entry
            locks.write(b, next);
            msg.send(p, add ? "coowner-added" : "coowner-removed", ph("player", shown));
        }
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
        locks.write(b, null); // removes everything, including what /unlock would remember
        msg.send(p, "admin-unlocked", ph("owner", AntitheftPlugin.nameOf(lock.owner())));
    }

    // ---- /unlock, /lockinfo ----

    private void unlock(Player p) {
        Block b = target(p);
        if (b == null) return;
        Lock lock = managedLock(p, b);
        if (lock == null) return;
        locks.unlockRemembering(b, lock);
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
        // Only people who manage the lock, and admins, see who else has a say or access.
        if (locks.canManage(p.getUniqueId(), lock) || p.hasPermission(Locks.BYPASS)) {
            msg.send(p, "info-coowners", ph("list", names(lock.coOwners())));
            msg.send(p, "info-access", ph("list", names(lock.access())));
        }
    }

    // ---- /trust, /untrust, /trustlist ----

    /** /trust [ownership] <player> and /untrust [ownership] <player>. */
    private void trust(Player p, String label, String[] args, boolean add) {
        Kind kind;
        String targetName;
        if (args.length == 1) {
            kind = Kind.TRUST;
            targetName = args[0];
        } else if (args.length == 2 && args[0].equalsIgnoreCase(OWNERSHIP)) {
            kind = Kind.OWNER;
            targetName = args[1];
        } else {
            msg.send(p, "usage-trust", ph("command", label));
            return;
        }
        OfflinePlayer other = resolve(p, targetName);
        if (other == null) return;
        UUID id = other.getUniqueId();
        String shown = AntitheftPlugin.nameOf(id);
        boolean owner = kind == Kind.OWNER;
        if (id.equals(p.getUniqueId())) {
            msg.send(p, "cannot-target-self");
        } else if (trust.set(kind, p.getUniqueId(), id, add)) {
            msg.send(p, owner ? (add ? "trusted-owner" : "untrusted-owner") : (add ? "trusted" : "untrusted"), ph("player", shown));
        } else {
            msg.send(p, owner ? (add ? "trust-owner-already" : "trust-owner-missing") : (add ? "trust-already" : "trust-missing"),
                    ph("player", shown));
        }
    }

    private void trustList(Player p) {
        var trusted = trust.granted(Kind.TRUST, p.getUniqueId());
        var owners = trust.granted(Kind.OWNER, p.getUniqueId());
        if (trusted.isEmpty() && owners.isEmpty()) {
            msg.send(p, "trustlist-empty");
            return;
        }
        msg.send(p, "trustlist", ph("list", names(trusted)));
        msg.send(p, "trustlist-owners", ph("list", names(owners)));
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

    /** The block's lock if the player is its owner, or null after telling them why not. */
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

    /** The block's lock if the player is its owner or a co-owner, or null after telling them why not. */
    private Lock managedLock(Player p, Block b) {
        Lock lock = locks.read(b);
        if (lock == null) {
            msg.send(p, "not-locked");
            return null;
        }
        if (!locks.canManage(p.getUniqueId(), lock)) {
            msg.send(p, "not-manager");
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
                List<String> subs = new ArrayList<>(List.of("help", "add", "remove", "give"));
                if (sender.hasPermission("antitheft.adminunlock")) subs.add("admin-unlock");
                return filter(subs, args[0]);
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2 && sub.equals("give")) return filter(List.of(OWNERSHIP), args[1]);
            if (args.length == 2 && sub.equals("remove")) return withOnline(OWNERSHIP, args[1]);
            if (args.length == 2 && sub.equals("add")) return null; // online names
            if (args.length == 3 && (sub.equals("give") || sub.equals("remove")) && args[1].equalsIgnoreCase(OWNERSHIP)) return null;
            return List.of();
        }
        if (name.equals("trust") || name.equals("untrust")) {
            if (args.length == 1) return withOnline(OWNERSHIP, args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase(OWNERSHIP)) return null;
        }
        return List.of();
    }

    /** "ownership" plus matching online player names. */
    private static List<String> withOnline(String word, String prefix) {
        List<String> out = new ArrayList<>(filter(List.of(word), prefix));
        String p = prefix.toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(p)) out.add(online.getName());
        }
        return out;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(p)).toList();
    }
}
