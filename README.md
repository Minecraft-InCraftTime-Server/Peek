# Peek - 贴贴魔法插件 ✨

一个充满魔法的 Minecraft 插件，让玩家可以通过魔法之眼查看其他玩家的视角。

## 🌟 魔法特性

- 🎯 观察者模式贴贴
- 🔮 私人模式与请求系统
- ⏳ 魔法冷却时间
- 📊 详尽的施法记录
- 🌍 跨维度传送
- 🎵 自定义魔法音效
- 💫 状态保存与恢复
- 📈 PlaceholderAPI 变量支持

## 📌 注意事项

1. 需要 Paper/Folia 服务端
2. 支持 Minecraft 1.20+
3. 建议安装 PlaceholderAPI
4. 私人模式需要玩家在线
5. 跨维度传送可能受服务器设置影响
6. 统计数据会定期自动保存
7. 冷却时间可在配置中调整
8. 支持自定义所有消息文本
9. 支持自定义所有音效
10. 统计变量需要安装 PlaceholderAPI 才能使用

## 🎮 快速开始

1. 下载最新版本的Peek(插件)
2. 放入你的魔法工坊(服务器)的 plugins 文件夹
3. 重启服务器，让魔法生效
4. (可选) 安装 PlaceholderAPI 以使用进阶魔法变量

## 📜 魔法咒语(命令)

- `/peek <玩家名>` - 施展贴贴魔法
- `/peek exit` - 解除贴贴魔法
- `/peek stats` - 查看魔法记录
- `/peek privacy` - 切换私人魔法护盾
- `/peek accept` - 接受贴贴请求
- `/peek deny` - 拒绝贴贴请求

## 🔑 魔法权限

- `peek.use` - 允许使用基础贴贴魔法
- `peek.bypass` - 允许绕过私人魔法护盾
- `peek.nocooldown` - 允许无视魔法冷却
- `peek.stats` - 允许查看魔法记录

## ⚙️ 魔法配置

### 基础设置
```yaml
# 调试模式
debug: false

# 限制设置
limits:
  max-peek-distance: 50.0  # 最大魔法距离
  cooldown:
    enabled: true         # 是否启用魔法冷却
    duration: 90        # 魔法冷却时间(秒)

# 隐私设置
privacy:
  request-timeout: 30      # 请求超时时间(秒)
  cooldown:
    enabled: true         # 是否启用请求冷却
    duration: 90        # 冷却时间(秒)

# 声音设置
sounds:
  start-peek: ENTITY_ENDERMAN_TELEPORT  # 开始观察音效
  end-peek: ENTITY_ENDERMAN_TELEPORT    # 结束观察音效
```

## 📊 PlaceholderAPI 变量

- `%peek_peek_count%` - 玩家使用贴贴功能的次数
- `%peek_peeked_count%` - 玩家被贴贴的次数
- `%peek_total_duration%` - 玩家总贴贴时长（分钟）
- `%peek_is_peeking%` - 玩家当前是否在贴贴别人
- `%peek_is_private%` - 玩家是否开启了私人模式

## 🎯 特性说明

1. **观察者模式**
   - 自动切换为观察者模式
   - 自动跟随目标玩家
   - 超出距离自动结束

2. **私人模式**
   - 开启后其他玩家需要发送请求
   - 请求超时自动取消
   - 可配置请求冷却时间

3. **状态保存**
   - 自动保存玩家状态
   - 意外退出后自动恢复
   - 支持跨服务器重启

4. **距离限制**
   - 可配置最大观察距离
   - 超出距离自动结束
   - 跨维度自动传送

## 🎯 技术特性

1. **Folia 支持**
   - 完整支持 Folia 多线程系统
   - 优化的区域调度器使用
   - 安全的跨线程操作

2. **状态管理**
   - 自动保存玩家状态
   - 安全的状态恢复机制
   - 跨服务器重启保护

3. **性能优化**
   - 异步数据处理
   - 内存优化管理
   - 自动清理系统

## 🛠️ 开发要求

- Java 21+
- Paper/Folia 1.20+
- PlaceholderAPI (可选)

## 📦 构建工具

- Maven
- Adventure API
- Folia API
- PlaceholderAPI

## 🔄 更新日志

### v2.2
- ✨ 优化了 Folia 支持
- 🔒 改进了状态保存机制
- 🎯 优化了距离检查逻辑
- 🎵 新增了更多音效选项

## 🐛 问题反馈

如果您在使用过程中遇到任何问题，欢迎通过以下方式反馈：

1. 在 [GitHub Issues](https://github.com/MineSunshineOne/Peek/issues) 提交问题

## 📄 开源协议

本项目采用 [MIT](LICENSE) 协议开源。

## 🙏 鸣谢

- [Paper](https://papermc.io/) - 高性能 Minecraft 服务端
- [Folia](https://github.com/PaperMC/Folia) - 多线程优化服务端
- [MiniMessage](https://docs.adventure.kyori.net/minimessage.html) - 文本格式化库
