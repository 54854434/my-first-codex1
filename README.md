# NodeAtlas

NodeAtlas 是一个面向 Android 的节点来源管理器和可达性检查工具，包含：

- 用户自行添加的 HTTPS 订阅源；
- `tg://proxy`、`tg://socks`、`ss://`、`socks5://`、`http(s)://`、`vmess://`、`vless://`、`trojan://` 解析；
- 本地去重、搜索、来源标记、节点状态和 Android 原生 TCP 端口检测；
- Telegram 专用代理链接打开/复制；
- 通用代理的 sing-box 配置导出，以及对 sing-box 远程订阅导入链接的交接。

## 明确边界

本项目不会无差别爬取任意网站、传播来源不明的节点，或声称 TCP 可达就是“高速/安全”。只添加你有权使用的订阅或链接。Telegram MTProto 代理是 Telegram 专用代理，不等同于全局 VPN。

当前 APK 是“聚合/检测/导出”客户端。它不内置任何免费节点，也不把未经验证的节点当作 VPN 内核运行；要建立系统级 TUN 通道，可把导出的配置交给已安装的 sing-box 等兼容客户端。这样不会把不可审计的内核或陌生节点静默塞进用户设备。

## 本地网页预览

直接打开 `app/src/main/assets/index.html` 即可。浏览器由于安全策略不能稳定执行原生 TCP 测试，网页会明确提示在 APK 中进行检测。

## 构建 APK

```bash
gradle --no-daemon assembleDebug
```

GitHub Actions 会在 `codex/node-atlas` 分支推送后构建并上传 `NodeAtlas-debug.apk` 与 `NodeAtlas.html`。

## 许可

本工程代码采用 MIT 许可证。它只使用 Android SDK，不捆绑 sing-box 或其他第三方 VPN 内核。
