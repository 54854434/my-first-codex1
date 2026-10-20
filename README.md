# 极光免费 VPN · Android 开发版

面向安卓手机的免费公共节点客户端：自动获取公开线路、检测可达性、选择线路并连接。不需要注册账号、填写 API Key、购买套餐或自行部署服务端。

**状态：开发中，不能视为已验证的商用品。** 本仓库提供应用界面和节点发现代码；通过固定版本的 OpenVPN for Android 源码构建为同一个 APK，无须另装连接引擎。APK 编译结果见 Actions；真机连接验收仍需进行。

## 功能

- 深蓝渐变首页、大号连接按钮、实时内核连接状态和流量计数。
- 内置 VPN Gate 公共 CSV 地址，HTTPS 获取，不需要用户配置 API。
- 最多选取 40 个源站标称带宽较高的 TCP 节点，4 路并发进行有超时的 TCP 可达性检测。
- 按本机 TCP 连接耗时排序；自动优选最多尝试前 3 个可达节点，每次约 35 秒。用户授权等待不计时。
- 国家/IP 搜索、收藏标记、来源/检测时间/公共节点风险说明。
- 原生 OpenVPN 全局隧道、Android 系统授权、内核前台通知与断开连接。
- 连接后手动执行约 1 MiB 的 HTTPS 下载测试，检查当前网络确实为 VPN 网络；显示该次下载平均 Mbps。
- 无广告、支付、账号系统或自建后台。不会伪造国家、延迟、剩余流量或“已连接”状态。

## 安装与使用

1. 打开本仓库 **Actions → Android APK**，进入对应开发分支最新成功的构建。
2. 下载 `aurora-free-vpn-debug`，解压并安装其中的通用 APK，兼容 ARM、ARM64、x86、x86_64。
3. 首次打开，阅读并接受免费节点说明，应用自动获取与检测线路。
4. 点击“自动优选连接”，确认 Android VPN 授权；也可以在线路页选定节点后点击首页圆形按钮。
5. 内核报告连接完成后显示“已连接”。点击“检测当前隧道下载速度”可测本次实际下载速度。

调试 APK 仅供测试。不同构建机器的调试签名可能不同，跨构建更新可能需要先卸载；正式发布前应配置长期发布签名。首次下载安装仍需 Android 的安装授权和 VPN 授权，应用不能跳过系统提示。

## 免费与速度的边界

VPN Gate 由志愿者提供线路，免费且无需注册，但节点随时可能离线或拥堵。应用仅使用公开自愿提供的 VPN 线路，不收集付费订阅密钥。

| 显示项 | 含义 |
|---|---|
| 标称 Mbps | VPN Gate 提供的服务器测速数据，**不是你的实测带宽** |
| TCP ms | 此手机到节点端口的连接耗时，**不等于隧道可用** |
| 已连接 | OpenVPN 内核报告隧道连接完成，**不保证所有网站可达** |
| 本次下载 Mbps | 当前 VPN 网络内向 Cloudflare 下载至多 1 MiB 的平均速度，包含建立 HTTPS 连接耗时 |

线路页持续标记“公共节点 / 日志风险”。VPN Gate 及节点运营者可能保留日志，不承诺匿名或隐私。HTTPS 和 VPN 证书校验保持启用，不会为连通而忽略证书错误。原始公开配置经白名单检查，不执行脚本、插件或外部文件引用。只接受带内联 CA 的 TCP/tun 配置。

公共源：<https://www.vpngate.net/api/iphone/>。源站被阻断、网络断开或源格式变化时，应用会提示获取失败；本版没有保证可用的镜像，也不提供离线硬编码节点。

## 开发与可复现构建

需要 Git、Python 3、JDK 17、Android SDK 36、NDK `29.0.14206865`、CMake、SWIG。

```bash
git clone --recursive https://github.com/54854434/my-first-codex1.git
cd my-first-codex1
git checkout codex/aurora-vpn-android
git submodule update --init --recursive
bash scripts/test.sh
python3 scripts/prepare.py
bash upstream/gradlew -p upstream :main:assembleUiOvpn2Debug
```

内核固定到 OpenVPN for Android v0.7.64，提交 `73ce3c99dde39207990d66c21be8201228cd1f09`，及其递归子模块。`scripts/prepare.py` 检查固定提交后，将 `overlay/` 复制到内核的 UI 源集，修改应用 ID 和启动入口；保留内核 VPN 服务及状态监听机制。包名为 `com.aurora.freevpn`，最低 Android 8.0。升级内核时应明确修改固定提交并重新验证，不自动跟随上游主分支。

`config/lint-baseline.xml` 记录固定内核中原有的 36 项 `ImpliedQuantity` 多语言数量词资源问题，路径均为未修改的上游 `src/ui/res/values-*/plurals.xml`。本项目新增源码不在基线内；没有关闭 lint 的错误阻断。测试会核对基线的数量、检查类型和文件范围。APK 生成、上传与 lint 分步执行，检查报告保留在 Actions。

## 已知限制与验收

- 当前自动发现只有 VPN Gate，一个公共来源；只纳入可预检的 OpenVPN TCP 线路，不含 WireGuard、VLESS、Trojan 或 UDP。
- 收藏只做标记；节点列表仅在当前应用进程中保存。来源更新会替换列表。
- 自动换线仅在当前 Activity 存活时运行；关闭界面后已建立的隧道由内核后台服务维持。本版没有完整的后台自动换线调度。
- 公共源中的城市信息不完整，因此只展示真实国家信息，不编造城市或地图位置。
- 隧道断开后 Android 可能恢复直连。需要断线阻断时，在系统 VPN 设置中开启“始终开启 VPN”及“阻止不使用 VPN 的连接”，并在设备上验证。
- 测速地址为 `https://speed.cloudflare.com/__down?bytes=1048576`，第三方服务可失效或限流。
- 正式商用前仍需 Android 8/12/14/16 真机连接、DNS/IPv6 路由与泄漏测试、Wi-Fi/移动网络切换、后台存活、撤销授权、证书失败、节点全失效等验收。没有完成这些验证前，不宣称达到商业 VPN 的可靠性。

## 许可证与来源

本项目新增代码按 GPL-2.0 发布，集成时遵守 OpenVPN for Android 的 GPL v2 及其附加许可条款。具体文本见 `LICENSE` 及 `upstream/doc/LICENSE.txt`；原生 OpenVPN、OpenSSL 等依赖各自的许可证保留在递归源码中。发布二进制时应同时提供本仓库、精确内核提交及依赖源码的获取方式和许可证。

- 内核与源码：<https://github.com/schwabe/ics-openvpn/tree/v0.7.64>
- VPN Gate 项目说明：<https://www.vpngate.net/en/about_overview.aspx>
- VPN Gate 日志政策：<https://www.vpngate.net/en/about_abuse.aspx>

本项目不是 VPN Gate 或 OpenVPN 官方应用。
