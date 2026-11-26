package io.github.earlgalvan.antitheft;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/** Messages from messages.yml, in MiniMessage format. */
public final class Messages {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private volatile YamlConfiguration yml = new YamlConfiguration();

    public void load(File file) {
        yml = YamlConfiguration.loadConfiguration(file);
    }

    public String raw(String key) {
        return yml.getString(key, key);
    }

    public void send(CommandSender to, String key, TagResolver... placeholders) {
        String text = yml.getString(key, key);
        if (text.isEmpty()) return;
        to.sendMessage(mm.deserialize(yml.getString("prefix", "") + text, TagResolver.resolver(placeholders)));
    }

    public static TagResolver ph(String name, String value) {
        return Placeholder.unparsed(name, value);
    }
}
