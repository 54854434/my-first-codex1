# NodeAtlas

NodeAtlas 是一个面向 Android 的节点发现、风险标记和 VPN 导入工具，包含：

- 用户自行添加的 HTTPS 订阅源；
- `tg://proxy`、`tg://socks`、`ss://`、`socks5://`、`http(s)://`、`vmess://`、`vless://`、`trojan://` 解析；
- 本地去重、搜索、来源标记、节点状态和 Android 原生 TCP 端口检测；
- Telegram 专用代理链接打开/复制；
- 通用代理的 sing-box 配置导出，以及对内置 VPN 客户端的本地导入；
- “自动发现”公开 HTTPS 文本目录；公开节点会标记为不明来源并按协议、配置完整性和地址形态计算风险等级。

## 明确边界

本项目只抓取 HTTPS 文本源，不会无差别爬取任意网站，也不会声称 TCP 可达就是“高速/安全”。自动发现的公开源不代表节点拥有者、合法性、隐私或速度；高风险节点默认只展示风险信息，不会静默自动连接。Telegram MTProto 代理是 Telegram 专用代理，不等同于全局 VPN。

`codex/node-atlas-vpn` 分支的 VPN 版 APK 会在同一个安装包内组合 NodeAtlas 界面与官方 sing-box Android 客户端/内核。导入节点后仍需在内置 VPN 控制台确认并启动，应用不会因为“自动发现”而后台连接陌生节点。

## 本地网页预览

直接打开 `app/src/main/assets/index.html` 即可。浏览器由于安全策略不能稳定执行原生 TCP 测试，网页会明确提示在 APK 中进行检测。

## 构建 APK

```bash
gradle --no-daemon assembleDebug
```

GitHub Actions 会在 `codex/node-atlas` 分支构建轻量聚合版；`codex/node-atlas-vpn` 分支会固定上游提交、编译 libbox AAR，并上传 `NodeAtlas-VPN-debug.apk`、网页和来源说明。

## 许可

NodeAtlas 自有代码采用 MIT 许可证。VPN 构建会包含 SagerNet sing-box-for-android 与 sing-box 的 GPL-3.0-or-later 组件，交付包内附来源说明及上游仓库链接；NodeAtlas 不声称与上游应用官方关联。
