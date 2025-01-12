package ict.minesunshineone.peek.util;

import java.io.File;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;

public class Messages {

    private final PeekPlugin plugin;
    private YamlConfiguration messages;

    public Messages(PeekPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        String language = plugin.getConfig().getString("language", "zh_CN");
        File langFile = new File(plugin.getDataFolder(), "lang/" + language + ".yml");

        // 如果语言文件不存在，保存默认的语言文件
        if (!langFile.exists()) {
            langFile.getParentFile().mkdirs();
            plugin.saveResource("lang/" + language + ".yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(langFile);
        if (!messages.contains("messages")) {
            plugin.getLogger().severe("Could not find messages section for language: " + language);
            // 加载默认消息作为备份
            messages = new YamlConfiguration();
            messages.createSection("messages");
        }
    }

    public void send(Player player, String key, String... placeholders) {
        String message = messages.getString("messages." + key);
        if (message == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return;
        }

        // 替换占位符
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                messages.getString("messages.prefix", "") + message));
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        String message = messages.getString("messages." + key);
        if (message == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return;
        }

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                messages.getString("messages.prefix", "") + message));
    }

    public String get(String key) {
        return messages.getString("messages." + key);
    }
}
