package ict.minesunshineone.peek.util;

import java.io.File;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

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
            plugin.getLogger().severe(String.format("无法找到语言 %s 的消息配置", language));
            // 加载默认消息作为备份
            messages = new YamlConfiguration();
            messages.createSection("messages");
        }
    }

    public void send(Player player, String key, String... placeholders) {
        String message = messages.getString("messages." + key);
        if (message == null) {
            plugin.getLogger().warning(String.format("找不到消息键：%s", key));
            return;
        }

        // 替换占位符
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }

        // 添加前缀并处理颜色代码
        String prefix = messages.getString("messages.prefix", "");
        String fullMessage = prefix + message;

        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(fullMessage));
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        String message = messages.getString("messages." + key);
        if (message == null) {
            plugin.getLogger().warning(String.format("找不到消息键：%s", key));
            return;
        }

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }

        // 添加前缀并处理颜色代码
        String prefix = messages.getString("messages.prefix", "");
        String fullMessage = prefix + message;

        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(fullMessage));
    }

    public String get(String key) {
        return messages.getString("messages." + key);
    }

    public void sendActionBar(Player player, String key, String... placeholders) {
        String message = messages.getString("messages." + key);
        if (message == null) {
            return;
        }

        // 替换占位符
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }

        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    /**
     * 重新加载消息文件
     * 用于调试或在运行时更新消息
     */
    public void reloadMessages() {
        loadMessages();
        plugin.getLogger().info("消息文件已重新加载");
    }
}
