# 开源方案核查与采用范围

核查日期：2026-10-21。区分“客户端”“公开节点来源”和“订阅管理工具”；客户端开源不代表免费提供出口服务器。以下链接均为维护者仓库/官方文档。

| 项目 | 可借鉴部分 | 极光当前状态 |
|---|---|---|
| [OpenVPN for Android](https://github.com/schwabe/ics-openvpn) | Android VpnService、原生 OpenVPN 内核、授权和服务生命周期 | 已集成固定源码及许可证；不是自行编造 VPN 引擎 |
| [Bitmask Android](https://github.com/leapcode/bitmask_android) | 官方 LEAP 服务商接入、单击连接和网关选择 | 已阅读 README，确认提供一键免费 VPN 的目标；适合后续研究正式服务商适配，尚未接入其认证和网关接口 |
| [sing-box](https://github.com/SagerNet/sing-box) | URLTest 的真实 URL 请求、并发上限、超时、测试历史、失败删除与切换容差 | 已审阅 `protocol/group/urltest.go`；采用以实际联网证据选路、限定并发及失败清除本机速度优先级的设计。没有复制其 Go 实现，也没有声称当前 APK 已集成该内核 |
| [Sub-Store](https://github.com/sub-store-org/Sub-Store) | 聚合订阅、转换和独立分发 | 已阅读说明；采用“外部整理与客户端消费分离”的结构。该项目自身不提供免费高速出口，当前 OpenVPN CSV 无需引入整套代理订阅转换服务 |
| [AutoOVPN](https://github.com/9xN/auto-ovpn) | 按时间更新的公开配置归档 | 新增服务器端采集通道，检查归档时间并经本项目同一配置白名单；仍按 VPN Gate 池计数，按 IP/端口去重，不冒充独立运营商 |

参考代码：[URLTest](https://github.com/SagerNet/sing-box/blob/testing/protocol/group/urltest.go)、[官方说明](https://sing-box.sagernet.org/configuration/outbound/urltest/)。URLTest 的短请求衡量响应耗时，下载采样另行测量，二者不混称带宽。

许可证核查：Bitmask 标示 GPL-3.0；Sub-Store 标示 AGPL-3.0；sing-box LICENSE 包含 GPLv3-or-later 文本及名称关联限制。若后续直接复用代码或集成内核，需保留其完整许可、源代码与归属要求。本轮对这些候选仅进行架构参考，不把 AGPL 代码复制进现有客户端。已有 OpenVPN 集成的许可与固定提交见仓库 README/COPYING。

边界：本轮外置列表测的是 GitHub 云端网络，不能替代手机当前移动网络/Wi-Fi 的判断；公开节点的数量、运营者数量和采集渠道数量分别计数。尚未完成多协议内核、LEAP 服务商实际接入或多地区探测，因此不宣称这些能力已可用。
