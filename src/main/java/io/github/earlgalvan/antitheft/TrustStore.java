package io.github.earlgalvan.antitheft;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global trust lists. Two kinds: {@link Kind#TRUST} (may open all of an owner's locks) and
 * {@link Kind#OWNER} (co-owner of all of an owner's locks). Both cover locks made later too.
 * One file per player holds both directions of both kinds, so a check never needs the (possibly
 * offline) owner's file. All disk I/O runs on one background thread; the cache holds online players only.
 */
public final class TrustStore {

    /** The two global relations, with the YAML keys of their two directions. */
    public enum Kind {
        TRUST("trusts", "trusted-by", 0),
        OWNER("co-owners", "co-owner-of", 2);

        final String grantedKey, receivedKey;
        final int index; // grants at index, receipts at index + 1

        Kind(String grantedKey, String receivedKey, int index) {
            this.grantedKey = grantedKey;
            this.receivedKey = receivedKey;
            this.index = index;
        }
    }

    /** sets[k.index] = players this one granted k to; sets[k.index + 1] = players who granted k to this one. */
    private record Entry(Set<UUID>[] sets) {
        @SuppressWarnings("unchecked")
        static Entry empty() {
            Set<UUID>[] s = new Set[4];
            for (int i = 0; i < 4; i++) s[i] = ConcurrentHashMap.newKeySet();
            return new Entry(s);
        }

        Set<UUID> granted(Kind k) {
            return sets[k.index];
        }

        Set<UUID> received(Kind k) {
            return sets[k.index + 1];
        }

        boolean isEmpty() {
            for (Set<UUID> s : sets) if (!s.isEmpty()) return false;
            return true;
        }
    }

    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Antitheft-IO");
        t.setDaemon(true);
        return t;
    });
    private final File dir;
    private final Logger log;

    public TrustStore(File dir, Logger log) {
        this.dir = dir;
        this.log = log;
    }

    /** Call from an async thread (pre-login). Waits for pending writes, then caches the player's lists. */
    public void loadBlocking(UUID player) {
        try {
            io.submit(() -> cache.put(player, readEntry(player))).get();
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not load trust list for " + player, e);
        }
    }

    public void loadAsync(UUID player) {
        io.execute(() -> cache.put(player, readEntry(player)));
    }

    public void loadIfMissing(UUID player) {
        if (!cache.containsKey(player)) loadAsync(player);
    }

    /** Runs a task on the plugin's I/O thread. */
    public void runIo(Runnable task) {
        io.execute(task);
    }

    public void unload(UUID player) {
        cache.remove(player);
    }

    /** May {@code player} open all of {@code owner}'s locks? */
    public boolean isTrusted(UUID owner, UUID player) {
        return has(Kind.TRUST, owner, player);
    }

    /** Is {@code player} a co-owner of all of {@code owner}'s locks? */
    public boolean isCoOwner(UUID owner, UUID player) {
        return has(Kind.OWNER, owner, player);
    }

    private boolean has(Kind k, UUID owner, UUID player) {
        // An online owner's own list is always current; use it when we have it.
        Entry o = cache.get(owner);
        if (o != null) return o.granted(k).contains(player);
        Entry p = cache.get(player);
        return p != null && p.received(k).contains(owner);
    }

    /** Players {@code owner} granted this kind to. The owner must be online. */
    public Set<UUID> granted(Kind k, UUID owner) {
        Entry e = cache.get(owner);
        return e == null ? Set.of() : Set.copyOf(e.granted(k));
    }

    /** Adds or removes a grant. Returns false if nothing changed. The owner must be online. */
    public boolean set(Kind k, UUID owner, UUID target, boolean add) {
        Entry o = cache.get(owner);
        if (o == null) return false;
        // The owner's cached list is changed only here, on the caller's thread.
        if (!(add ? o.granted(k).add(target) : o.granted(k).remove(target))) return false;
        io.execute(() -> {
            edit(owner, k, true, target, add, false);
            // The target's cached list is changed only on the I/O thread, in order with its login load.
            edit(target, k, false, owner, add, true);
        });
        return true;
    }

    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) log.warning("Trust saves did not finish in 10 s.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- I/O thread only ----

    private void edit(UUID id, Kind k, boolean grantedSide, UUID value, boolean add, boolean updateCache) {
        Entry onDisk = readEntry(id);
        apply(grantedSide ? onDisk.granted(k) : onDisk.received(k), value, add);
        writeEntry(id, onDisk);
        if (updateCache) {
            Entry cached = cache.get(id);
            if (cached != null) apply(grantedSide ? cached.granted(k) : cached.received(k), value, add);
        }
    }

    private static void apply(Set<UUID> set, UUID value, boolean add) {
        if (add) set.add(value); else set.remove(value);
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    private Entry readEntry(UUID id) {
        Entry e = Entry.empty();
        File f = file(id);
        if (!f.exists()) return e;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        for (Kind k : Kind.values()) {
            for (String s : yml.getStringList(k.grantedKey)) e.granted(k).add(UUID.fromString(s));
            for (String s : yml.getStringList(k.receivedKey)) e.received(k).add(UUID.fromString(s));
        }
        return e;
    }

    private void writeEntry(UUID id, Entry e) {
        File f = file(id);
        if (e.isEmpty()) {
            if (f.exists() && !f.delete()) log.warning("Could not delete " + f);
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        for (Kind k : Kind.values()) {
            if (!e.granted(k).isEmpty()) yml.set(k.grantedKey, toStrings(e.granted(k)));
            if (!e.received(k).isEmpty()) yml.set(k.receivedKey, toStrings(e.received(k)));
        }
        try {
            dir.mkdirs();
            yml.save(f);
        } catch (IOException ex) {
            log.log(Level.WARNING, "Could not save " + f, ex);
        }
    }

    private static List<String> toStrings(Set<UUID> set) {
        return set.stream().map(UUID::toString).sorted().toList();
    }
}
