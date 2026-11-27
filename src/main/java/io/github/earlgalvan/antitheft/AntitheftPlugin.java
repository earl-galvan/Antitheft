package io.github.earlgalvan.antitheft;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AntitheftPlugin extends JavaPlugin {

    private final Messages messages = new Messages();
    private TrustStore trust;
    private Locks locks;
    private volatile boolean autoLock;

    @Override
    public void onEnable() {
        if (!isPaper()) {
            getLogger().severe("Antitheft needs Paper 1.18.2 or newer, or a Paper fork such as Purpur or Folia. "
                    + "Spigot and CraftBukkit are not supported. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);

        trust = new TrustStore(new File(getDataFolder(), "trust"), getLogger());
        locks = new Locks(this, trust);
        reloadAll();

        // Players already online (e.g. after a plugin reload).
        for (Player p : Bukkit.getOnlinePlayers()) trust.loadAsync(p.getUniqueId());

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        if (GolemGuard.register(this)) getLogger().info("Copper golem protection enabled.");
        Commands commands = new Commands(this);
        for (String name : new String[]{"lock", "unlock", "lockinfo", "trust", "untrust", "trustlist", "antitheft"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd == null) throw new IllegalStateException("Command missing from plugin.yml: " + name);
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        warnIfHopperEventsDisabled();
    }

    @Override
    public void onDisable() {
        if (trust != null) trust.shutdown();
    }

    public void reloadAll() {
        reloadConfig();
        try (var bundled = getResource("messages.yml")) {
            messages.load(new File(getDataFolder(), "messages.yml"), bundled);
        } catch (java.io.IOException e) {
            getLogger().warning("Could not read the bundled messages.yml: " + e.getMessage());
        }
        autoLock = getConfig().getBoolean("auto-lock-on-place", false);

        Set<Material> lockable = EnumSet.noneOf(Material.class);
        List<String> skipped = new ArrayList<>();
        for (String name : getConfig().getStringList("lockable-blocks")) {
            Material m = Material.matchMaterial(name);
            if (m == null || !m.isBlock()) skipped.add(name); else lockable.add(m);
        }
        locks.setLockable(lockable);
        getLogger().info(lockable.size() + " lockable block types loaded.");
        if (!skipped.isEmpty()) {
            getLogger().info("Not on this Minecraft version, skipped: " + String.join(", ", skipped));
        }
    }

    /** MiniMessage and the non-snapshot block state API only exist on Paper (1.18.2+) and its forks. */
    private static boolean isPaper() {
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Paper's "hopper.disable-move-event" option stops InventoryMoveItemEvent from firing,
     * which would let hoppers drain locked blocks. Read the config files off the main thread and warn.
     */
    private void warnIfHopperEventsDisabled() {
        File root = getServer().getWorldContainer();
        List<File> files = new ArrayList<>();
        files.add(new File(root, "config/paper-world-defaults.yml")); // 1.19+
        for (World w : Bukkit.getWorlds()) files.add(new File(w.getWorldFolder(), "paper-world.yml"));
        File legacy = new File(root, "paper.yml"); // 1.18
        trust.runIo(() -> { // Bukkit scheduler is not available on Folia
            boolean off = false;
            for (File f : files) {
                if (f.isFile() && YamlConfiguration.loadConfiguration(f).getBoolean("hopper.disable-move-event")) off = true;
            }
            if (legacy.isFile()) {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(legacy);
                var worlds = yml.getConfigurationSection("world-settings");
                if (worlds != null) {
                    for (String w : worlds.getKeys(false)) {
                        if (worlds.getBoolean(w + ".hopper.disable-move-event")) off = true;
                    }
                }
            }
            if (off) {
                getLogger().warning("hopper.disable-move-event is true in your Paper config. "
                        + "Hoppers can then drain locked blocks. Set it to false to keep them protected.");
            }
        });
    }

    public boolean autoLock() {
        return autoLock;
    }

    public Locks locks() {
        return locks;
    }

    public Messages messages() {
        return messages;
    }

    public TrustStore trust() {
        return trust;
    }

    static String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        if (name != null) return name;
        var profile = Bukkit.createProfile(id);
        // Server name cache only; never a web lookup.
        if (profile.completeFromCache() && profile.getName() != null) return profile.getName();
        return id.toString();
    }
}
