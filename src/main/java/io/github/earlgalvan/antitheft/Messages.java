package io.github.earlgalvan.antitheft;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Messages from messages.yml, in MiniMessage format. */
public final class Messages {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private volatile YamlConfiguration yml = new YamlConfiguration();

    /** Loads the server's file; keys it lacks (e.g. added in a newer version) come from the bundled defaults. */
    public void load(File file, InputStream bundled) {
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        if (bundled != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
        yml = loaded;
    }

    public String raw(String key) {
        String text = yml.getString(key); // one-argument form falls back to the defaults
        return text != null ? text : key;
    }

    public void send(CommandSender to, String key, TagResolver... placeholders) {
        String text = raw(key);
        if (text.isEmpty()) return;
        String prefix = yml.getString("prefix");
        to.sendMessage(mm.deserialize((prefix != null ? prefix : "") + text, TagResolver.resolver(placeholders)));
    }

    /** Sends a message without the prefix (used for multi-line text such as /lock help). */
    public void sendPlain(CommandSender to, String key) {
        String text = raw(key);
        if (!text.isEmpty()) to.sendMessage(mm.deserialize(text));
    }

    public static TagResolver ph(String name, String value) {
        return Placeholder.unparsed(name, value);
    }
}
