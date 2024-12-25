package ict.minesunshineone.peek;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 消息管理类 负责从配置文件加载和管理插件的所有消息
 */
public class Messages {

    private final Map<String, String> messages = new HashMap<>();  // 存储所有消息
    private final PeekPlugin plugin;

    public Messages(PeekPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    /**
     * 从配置文件加载消息 支持多语言，默认使用中文
     */
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

    /**
     * 获取指定键的消息
     *
     * @param key 消息的键
     * @return 对应的消息，如果不存在则返回错误提示
     */
    public String get(String key) {
        return messages.getOrDefault(key, "Missing message: " + key);
    }

    /**
     * 获取带有占位符替换的消息
     *
     * @param key 消息的键
     * @param replacements 替换参数，格式为: 占位符,值,占位符,值...
     * @return 替换后的消息
     */
    public String get(String key, String... replacements) {
        String message = get(key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return message;
    }
}
