package ict.minesunshineone.peek.util;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Messages {

    private final PeekPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();

    public Messages(PeekPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        FileConfiguration config = plugin.getConfig();
        String language = config.getString("language", "zh_CN");
        String path = String.format("messages.%s", language);

        var section = config.getConfigurationSection(path);
        if (section == null) {
            plugin.getLogger().severe(String.format("Could not find messages section for language: %s", language));
            return;
        }

        for (String key : section.getKeys(false)) {
            messages.put(key, config.getString(path + "." + key));
        }
    }

    public String get(String key, String... replacements) {
        String message = messages.getOrDefault(key, "Missing message: " + key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return message;
    }

    public void send(CommandSender sender, String key, String... replacements) {
        String message = get(key, replacements);
        if (message == null) {
            return;
        }

        if (sender instanceof Player) {
            ((Player) sender).sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
        } else {
            sender.sendMessage(message);
        }
    }

    public void sendError(CommandSender sender, String key, Throwable error) {
        send(sender, key);
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().warning(String.format("Error: %s - %s",
                    key, error.getMessage()));
        }
    }
}
