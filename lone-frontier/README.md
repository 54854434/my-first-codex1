# 孤军：无限战线

现代军事风格的安卓离线增量防守游戏。从一名自动射击的步兵开始，守住无限波次的敌军。全部玩法代码、界面、战场画面与合成音效均为原创，无外部美术资源、运行时依赖、网络请求、广告或内购。

## 安装与游玩

安装 `LoneFrontier-v1.0.0.apk`，支持 Android 8.0 及以上。系统 Android System WebView 建议保持更新。打开后点击“部署第一名步兵”。部队自动开火，你通过购买升级和使用支援技能推进。

- 军备：伤害、射速、防线耐久、补给收益。
- 部队：步兵、机枪、狙击手、迫击炮、坦克、无人机。
- 每 5 波出现首领，敌人含快攻步兵、重甲、装甲车及坦克。
- 点击战场集火，长按升级连续购买；支持 2 倍速。
- 第 5 波开放空袭，第 8 波开放自动升级，第 10 波通关后可撤离重编。
- 失守后保留军备和补给，回退 2 波；撤离会重置本轮成长并奖励可购买永久科技的勋章。
- 每 5 秒及切换后台时保存。离线收益最多累积 8 小时，后台不进行战斗。
- 暂停菜单可复制完整存档并恢复。卸载前可用此功能备份。

## 构建

只需 JDK 17、Node.js 18+、Android SDK Platform 35 与 Build Tools 35.0.0、zip。无需 Gradle，也无需下载第三方游戏引擎。

```bash
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
export ANDROID_HOME=/your/android-sdk
bash scripts/build.sh
```

产物为 `dist/LoneFrontier-v1.0.0.apk`。首次构建会创建 `.signing/frontier.jks` 个人签名，别名 `frontier`，密码 `android`。保留此文件可以为后续版本使用同一签名。可以通过 `FRONTIER_KEYSTORE` 指定其他密钥路径。

源码备份压缩包内附此交付 APK 对应的 `.signing/frontier.jks`，方便后续更新覆盖安装；GitHub 不存储签名密钥，云端重新构建的包会使用新签名。

GitHub Actions 工作流见 `.github/workflows/lone-frontier.yml`。在当前仓库中游戏位于 `lone-frontier/` 子目录，工作流位于仓库根目录；工作流会构建 APK、在 Android 11 模拟器安装并操作应用、保存截图与日志。

## 代码结构与验证

- `app/src/main/assets/engine.js`：可独立运行的确定性战斗、成长与存档系统。
- `app/src/main/assets/game.js`：触屏界面、Canvas 原创绘图、音效与生命周期。
- `app/src/main/java/.../MainActivity.java`：本地 WebView、应用私有存档与剪贴板桥接。
- `tests/engine.test.cjs`：12 项核心逻辑测试，包含一小时战斗模拟。
- `scripts/android-smoke.py`：安装、部署、购买、暂停、备份和后台恢复的 Android 自动化测试。

运行核心测试：`node --test tests/engine.test.cjs`。

应用不申请联网、位置、相机、麦克风或存储权限。存档保存在应用私有目录。包名 `com.lonefrontier.game`，minSdk 26，targetSdk 35。程序和所有原创资源采用 MIT 许可。
