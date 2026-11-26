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
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global trust lists. One file per player holds who they trust and who trusts them,
 * so an access check never needs the (possibly offline) owner's file.
 * All disk I/O runs on one background thread; the cache holds online players only.
 */
public final class TrustStore {

    private record Entry(Set<UUID> trusts, Set<UUID> trustedBy) {
        static Entry empty() {
            return new Entry(ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet());
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

    public boolean isTrusted(UUID owner, UUID player) {
        // An online owner's own list is always current; use it when we have it.
        Entry o = cache.get(owner);
        if (o != null) return o.trusts().contains(player);
        Entry p = cache.get(player);
        return p != null && p.trustedBy().contains(owner);
    }

    public Set<UUID> trustedBy(UUID owner) {
        Entry e = cache.get(owner);
        return e == null ? Set.of() : Set.copyOf(e.trusts());
    }

    /** Adds or removes trust. Returns false if nothing changed. The owner must be online. */
    public boolean setTrust(UUID owner, UUID target, boolean add) {
        Entry o = cache.get(owner);
        if (o == null) return false;
        // The owner's cached list is changed only here, on the main thread.
        if (!(add ? o.trusts().add(target) : o.trusts().remove(target))) return false;
        io.execute(() -> {
            edit(owner, Entry::trusts, target, add, false);
            // The target's cached list is changed only on the I/O thread, in order with its login load.
            edit(target, Entry::trustedBy, owner, add, true);
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

    private void edit(UUID id, Function<Entry, Set<UUID>> field, UUID value, boolean add, boolean updateCache) {
        Entry onDisk = readEntry(id);
        apply(field.apply(onDisk), value, add);
        writeEntry(id, onDisk);
        if (updateCache) {
            Entry cached = cache.get(id);
            if (cached != null) apply(field.apply(cached), value, add);
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
        for (String s : yml.getStringList("trusts")) e.trusts().add(UUID.fromString(s));
        for (String s : yml.getStringList("trusted-by")) e.trustedBy().add(UUID.fromString(s));
        return e;
    }

    private void writeEntry(UUID id, Entry e) {
        File f = file(id);
        if (e.trusts().isEmpty() && e.trustedBy().isEmpty()) {
            if (f.exists() && !f.delete()) log.warning("Could not delete " + f);
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("trusts", toStrings(e.trusts()));
        yml.set("trusted-by", toStrings(e.trustedBy()));
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
