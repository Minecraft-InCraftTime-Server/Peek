# Peek Plugin

一个基于魔法主题的 Minecraft 观察者插件，让玩家能以有趣的方式观察其他玩家。

## ✨ 特性

- 🎮 使用魔法主题的交互体验
- 👥 实时观察其他玩家视角
- ⚡ 完整支持 Folia 服务端
- 📊 详细的观察统计系统
- ⏱️ 可配置的观察时长和冷却时间
- 🔒 完善的权限管理系统
- 🌈 生动有趣的提示信息
- 💫 断线重连自动恢复状态
- 📈 PlaceholderAPI 变量支持

## 📦 安装

1. 下载最新版本的 Peek.jar
2. 放入服务器的 plugins 文件夹
3. 重启服务器
4. （可选）安装 PlaceholderAPI 以使用统计变量

## 🎮 命令

- `/peek <玩家名>` 开始观察指定玩家
- `/peek exit` 停止观察
- `/peek stats` 查看观察统计
- `/peek help` 查看帮助信息

## ⚙️ 配置

主要配置项：
- `max-peek-duration` 最大观察时长（秒）
- `check-target` 是否检查目标权限
- `debug` 是否开启调试模式
- `performance.async-save` 是否异步保存数据

## 🎵 声音效果

- `start-peek` 开始被观察时的声音
- `end-peek` 结束被观察时的声音
- `cooldown` 冷却中的声音

## 🔑 权限节点

- `peek.use` 使用观察插件
- `peek.admin` 管理观察插件
- `peek.check-target` 检查目标权限
- `peek.debug` 开启调试模式
- `peek.nocooldown` 绕过冷却时间
- `peek.target` 允许被其他玩家观察

## 📊 统计变量

PlaceholderAPI 变量：
- `%peek_peek_count%` 观察次数
- `%peek_peeked_count%` 被观察次数
- `%peek_peek_duration%` 总观察时长（分钟）

## 📝 注意事项

1. 请确保服务器使用 Paper/Folia 1.21.4 或更高版本
2. 插件需要 Java 21 或更高版本运行环境
3. 首次运行会自动生成配置文件
4. 建议在正式使用前先测试配置是否正确
5. 如遇问题，请查看服务器日志获取详细信息
6. 使用 `/peek exit` 而不是切换游戏模式来退出观察
7. 观察者数量限制默认为 5 人，可在配置中调整
8. 统计数据每 10 分钟自动保存一次
9. 权限节点的设置会影响插件的使用范围
10. 支持断线重连自动恢复观察状态
11. 统计变量需要安装 PlaceholderAPI 才能使用

## 🛡️ 安全特性

- 服务器关闭时自动结束所有观察状态
- 目标玩家下线时自动结束观察
- 游戏模式改变时自动退出观察
- 支持最大观察者数量限制
- 可配置目标玩家权限检查
- 断线重连自动恢复原始状态

## 📊 统计系统

统计功能记录：
- 观察次数
- 被观察次数
- 总观察时长（精确到小数点后一位）
- 支持异步保存
- 支持 PlaceholderAPI 变量

## 🔧 技术特性

- 使用 Adventure API 处理文本和颜色
- 支持异步操作和区域调度
- 完整的事件处理系统
- 优化的性能表现
- 断线重连状态恢复系统
- PlaceholderAPI 扩展支持

## 📝 开发计划

- [x] 添加断线重连状态恢复
- [x] 添加 PlaceholderAPI 支持
- [ ] 支持多语言系统
- [ ] 扩展统计功能
- [ ] 添加更多自定义选项

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 MIT 许可证。

## 🌟 特别感谢

感谢所有为本项目做出贡献的开发者！

## 🔗 相关链接

- [问题反馈](https://github.com/yourusername/peek/issues)
