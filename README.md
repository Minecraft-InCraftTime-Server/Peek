# Peek - 贴贴插件

一个有趣的 Minecraft 插件，让玩家可以以观察者模式查看其他玩家的视角。

## ✨ 特性

- 🎮 使用观察者模式查看其他玩家
- 🔒 私人模式和请求系统
- ⏱️ 可配置的冷却时间
- 📊 详细的统计信息
- 🌍 跨维度自动传送
- 🎵 可自定义的音效提示
- 🔄 自动状态保存和恢复
- 📈 PlaceholderAPI 变量支持

## 🚀 快速开始

1. 下载最新版本的插件
2. 放入服务器的 plugins 文件夹
3. 重启服务器
4. （可选）安装 PlaceholderAPI 以使用统计变量

## 📝 命令

- `/peek <玩家名>` - 观察指定玩家
- `/peek exit` - 退出观察模式
- `/peek stats` - 查看统计信息
- `/peek privacy` - 切换私人模式
- `/peek accept` - 接受观察请求
- `/peek deny` - 拒绝观察请求

## 🔑 权限

- `peek.use` - 允许使用基础贴贴功能
- `peek.bypass` - 允许绕过私人模式
- `peek.nocooldown` - 允许绕过冷却时间
- `peek.stats` - 允许查看统计信息

## ⚙️ 配置

### config.yml
```yaml
# 调试模式
debug: false

# 限制设置
limits:
  max-peek-distance: 50.0  # 最大观察距离
  cooldown:
    enabled: true         # 是否启用观察冷却
    duration: 90        # 观察冷却时间(秒)

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

## 🔄 更新日志

### v2.0
- ✨ 优化了跨维度传送逻辑
- 🔒 改进了状态保存机制
- 🎯 优化了距离检查逻辑
- 🔧 修复了已知问题

## 🛠️ 开发计划

- [x] 基础贴贴功能
- [x] 私人模式系统
- [x] 统计系统
- [x] 状态保存
- [x] PlaceholderAPI 扩展支持
- [x] 跨维度支持
- [x] 距离限制
- [x] 音效系统

## 📄 开源协议

本项目采用 MIT 协议开源。
