# Aerial Next

一个面向 Android TV 的开源桌面：应用网格、壁纸与航拍屏保，加上继续观看、观看记录和聚合搜索。

**本项目是 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) 的独立维护分支，由 [poetcc](https://github.com/poetcc) 继续开发。** 基于原项目的 `067e4b9`（Aerial 1.9），保留原作者署名、Git 历史和 MIT 许可证。原版下载、官网及维护者不代表本分支；来源与差异见 [UPSTREAM.md](UPSTREAM.md)。

[源码与反馈](https://github.com/poetcc/aerial-tv-next) · [构建检查](https://github.com/poetcc/aerial-tv-next/actions) · [贡献指南](CONTRIBUTING.md) · [MIT](LICENSE)

## 我们增加了什么

- **继续观看**：桌面显示三条紧凑记录，观看记录列表保留最近 100 个节目或集数，可返回对应平台继续观看。
- **四平台兼容接收器**：在标准 MediaSession 之外，实验兼容部分爱奇艺、腾讯、优酷、芒果电视版提供的节目记录。标题、进度和历史保存在本机。
- **聚合搜索**：遥控器字母数字键盘、片名首字母搜索、系统中文输入、平台筛选与分页。空白页面不展示推荐。
- **中立的本地排序**：片名匹配优先，同等匹配度的平台轮流显示，起始平台随关键词轮换。该规则只影响已返回的结果，不能改变外部服务的收录和分页范围。
- **玻璃界面**：记录卡片和局部磨砂面板，放大反馈替代应用磁贴外的白色选框。
- **遥控交互与守护改进**：HOME 退出搜索，输入完成后按上收起输入法并选结果；补充守护重绑、唤醒复查及已授权的 ADB 自动恢复。

原版的应用管理、控制中心、壁纸、手机配置和航拍屏保继续保留。Android 包名仍为 `app.orchard.tv`，便于沿用同签名的安装与设置；这不代表本分支与原版的签名相同。

## 当前验证范围

本分支在小米 Android 11 电视上验证过四平台真实观看记录及点击返回节目；搜索结果、方向键、HOME 返回、输入法上键收起、屏保焦点和 ADB 开关自动恢复也完成了真机检查。

- 本地标准版、守护版和接收助手可构建，现有 **38 项单元测试**覆盖记录解析与合并、应用过滤、搜索路由、方向键图、排序及守护窗口判断。
- 用户曾报告待机／重启后偶尔回到系统桌面。恢复流程已有改进，**尚未完成新版待机、重启的稳定性验证**，不能保证所有固件都能持续接管。
- 完整中文候选字选择、不同品牌及流媒体后续版本仍需更多测试。上游的 TCL 等机型笔记仅作为参考，不能视为本分支的测试结果。

## 从源码构建

首次公开以源码为主，**尚未发布本分支的正式 APK Release**。不要把原版 Aerial 的 APK 当作本分支下载。

需要 JDK 17、Android SDK 36 和可访问 Gradle／Google Maven 的网络：

```sh
git clone https://github.com/poetcc/aerial-tv-next.git
cd aerial-tv-next
export ANDROID_HOME=/path/to/android-sdk
./gradlew testStandardReleaseUnitTest assembleStandardRelease assembleGuardRelease :sonybridge:assembleRelease
```

| 产物 | 用途 |
| --- | --- |
| `app/build/outputs/apk/standard/release/app-standard-release.apk` | 声明 HOME 的标准版 |
| `app/build/outputs/apk/guard/release/app-guard-release.apk` | 不声明 HOME、依靠守护接管的版本 |
| `sonybridge/build/outputs/apk/release/sonybridge-release.apk` | 可选的历史兼容接收助手 |

没有 `local/signing.properties` 时构建使用本机 debug key，适合开发和首次安装；持续更新需要维护者自行保管一套固定签名。桌面和助手必须同签名。仓库不包含签名密钥，**不要为解决签名冲突直接卸载已有桌面**，以免丢失设置和历史。

## 安装与恢复

在电视设置中开启 ADB 调试，电脑和电视连接同一网络，在电视确认调试授权。没有 adb 时可以从 [Google 官方 platform-tools](https://developer.android.com/tools/releases/platform-tools) 安装。

本地构建后安装标准版：

```sh
BUILD=1 KEEP_ADB=1 TV=<电视IP> tools/install.sh
# 拒绝声明 HOME 的固件可选择守护版：
BUILD=1 KEEP_ADB=1 TV=<电视IP> tools/install.sh guard
```

需要继续观看兼容助手时，另行安装**同一次构建、同一签名**的助手：

```sh
adb -s <电视IP>:5555 install -r sonybridge/build/outputs/apk/release/sonybridge-release.apk
```

`KEEP_ADB=1` 是可选项：系统关闭调试开关后，Aerial 在恢复运行和拥有原授权时尝试重新开启；系统强停、断电或网络断开期间仍可能短暂失联。它不是固件级的永久锁定。

安装工具不卸载、停用系统应用。它可能申请默认桌面、悬浮窗、修改安全设置、无障碍守护及通知监听等权限，供 HOME 接管和恢复使用。开启继续观看还会使用媒体会话信息。不同厂商的授权和安装限制见 [devices](docs/devices/)。

撤销桌面接管和相关工具授权，保留应用：

```sh
TV=<电视IP> tools/restore.sh
```

不要在未获电视使用者同意时重启电视。发布 APK 后安装脚本将从本仓库下载；目前请使用源码构建方式。

## 流媒体兼容边界

- 观看记录依赖对应电视版上报，不会读取其他应用的私有数据库，也不能恢复此前未上报的历史。
- 爱奇艺、腾讯、优酷、芒果的兼容版本按包名、最低版本和公开签名指纹识别。兼容助手标识、协议及冲突说明见 [UPSTREAM.md](UPSTREAM.md#兼容模块与第三方资源)。
- 仓库不附带电视固件、商业应用 APK、账户凭据、平台签名或缓存配置密钥。请自行从可信渠道取得并授权使用兼容应用。
- 搜索使用外部公共节目目录；其接口未承诺对本项目稳定开放。搜索只提供节目元数据与跳转，播放、广告、地区限制和会员资格由对应平台决定。
- 图片、节目名称、第三方应用、商标和外部视频不因本仓库采用 MIT 而改变其归属。

## 后续维护

开发和反馈集中在 [poetcc/aerial-tv-next](https://github.com/poetcc/aerial-tv-next)，逐步完善待机恢复、多机型适配、输入体验和协议兼容。之后选择性同步上游改动，并在提交中保留来源。

原版功能介绍与截图保存在 [上游 README 快照](docs/UPSTREAM_README.md)，当前结构和操作约定见 [HANDOFF](docs/HANDOFF.md)、[ARCHITECTURE](docs/ARCHITECTURE.md)。本地电视地址、照片、日志、研究产物和签名资料均不上传。

## 致谢与许可证

感谢 **pengchujin** 创建 Aerial，并感谢上游贡献者。基础桌面、屏保和设备适配由原项目发展而来；本分支新增部分同样以 [MIT License](LICENSE) 开源。字体保留 [SIL Open Font License](FONT-LICENSE-OFL.txt)。

本项目为社区派生项目，与原维护者、Sony、当贝、Apple、电视厂商及各流媒体平台均无官方隶属或合作关系。
