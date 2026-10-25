> 上游历史资料：以下内容来自 pengchujin/aerial-tv 的 067e4b9，描述原版 Aerial，不能作为 Aerial Next 的下载或验证说明。当前项目请阅读根目录 README。

<div align="center">

<img src="docs/brand/icon.png" width="96" alt="Aerial 图标">

# Aerial

**给国产电视的一块干净桌面。**

参照 tvOS 设计的 Android TV 桌面：没有广告位，没有推荐流，只有你的 App、你选的壁纸，和 Apple TV 式的航拍屏保。
为小米、TCL 等国内电视而做，开机自动接管，不怕被系统清理。

<a href="https://aerial.qust.me"><img alt="官网" src="https://img.shields.io/badge/%E5%AE%98%E7%BD%91-1f6feb?style=for-the-badge&logo=safari&logoColor=white"></a>
<a href="https://github.com/pengchujin/aerial-tv/releases/latest"><img alt="下载 APK" src="https://img.shields.io/badge/%E4%B8%8B%E8%BD%BD%20APK-34a853?style=for-the-badge&logo=android&logoColor=white"></a>
<a href="#用-agent-安装推荐"><img alt="让 Agent 帮你装" src="https://img.shields.io/badge/%E8%AE%A9%20Agent%20%E5%B8%AE%E4%BD%A0%E8%A3%85-d97757?style=for-the-badge&logo=claude&logoColor=white"></a>
<a href="#第一步打开电视的-adb-调试"><img alt="开启 ADB" src="https://img.shields.io/badge/%E5%BC%80%E5%90%AF%20ADB-555555?style=for-the-badge&logo=gnubash&logoColor=white"></a>

<a href="https://github.com/pengchujin/aerial-tv/releases/latest"><img alt="版本" src="https://img.shields.io/github/v/release/pengchujin/aerial-tv?style=flat-square&label=%E7%89%88%E6%9C%AC&color=2997ff"></a>
<a href="LICENSE"><img alt="许可证 MIT" src="https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-MIT-555555?style=flat-square"></a>
<img alt="Android TV 5.0+" src="https://img.shields.io/badge/Android%20TV-5.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white">

![Aerial 首页](site/img/home.jpg)

| 电视自带的桌面 | 换上 Aerial 之后 |
|:---:|:---:|
| <img src="site/img/stock-xiaomi.jpg" alt="电视自带的桌面：满屏剧集推荐、会员和限免角标"> | <img src="site/img/home.jpg" alt="Aerial 首页"> |

</div>

## 功能

### 首页

<p align="center">
  <img src="site/img/focus.jpg" width="49%" alt="聚焦在 Netflix 上">
  <img src="site/img/grid.jpg" width="49%" alt="往下滚动后的应用网格">
</p>

- **大号细体时钟**：显示在壁纸上，壁纸的底部和左侧会自动压暗，保证文字和 App 都看得清。往下滚动时，时钟淡出，状态栏出现小时钟。
- **应用网格**：每行 5 个。聚焦时磁贴浮起、出现柔光，名字从下方浮现；按到边缘会轻轻倾斜回弹。
- **焦点样式**：默认「高对比度」，选中的 App 外面会加一圈白色描边，离得远也看得清（对应 tvOS「辅助功能 → 增强对比度 → 焦点样式」）。想要和 Apple TV 出厂一样只浮起不描边，可以在「控制中心 → Aerial 设置」或手机扫码页里改成「标准」。
- **图标处理**：有 TV 横幅的直接用横幅；自适应图标拆成前后两层，有视差效果；老式图标放在用它自身模糊做成的毛玻璃底板上。
- **全部 App 都在**：标准的 TV 入口和手机入口都会收录，小米的「应用中心」「高清播放器」「米家」这类没有标准入口的系统 App 也会显示出来。
- **返回键**：和 tvOS 一样，在网格里按返回会回到第一行，在第一行按返回会立即开始屏保。

### 长按菜单

![长按菜单](site/img/menu.jpg)

长按 OK 键：**移动**（tvOS 式抖动，其他磁贴自动让位）、**从主屏幕隐藏**、**应用信息**、**卸载**。

### 控制中心

![控制中心](site/img/control-center.jpg)

按遥控器的「菜单 ≡」键打开，或在第一行按上键选中右上角。
- **快捷入口**：信号源（HDMI / ATV / DTMB / AV，显示 CEC 设备）、网络、设置、睡眠。网络和设置会打开电视厂商自己的界面。
- **调节**：音量和亮度滑块，用左右键调节。
- **其他**：壁纸与屏保、隐藏的 App、Aerial 设置（焦点样式）、桌面守护状态。

### 航拍屏保

<p align="center">
  <img src="site/img/screensaver-aerial.jpg" width="49%" alt="航拍屏保：俯瞰优胜美地">
  <img src="site/img/screensaver-clock.jpg" width="49%" alt="时钟屏保">
</p>

- **航拍**：播放 Apple TV 的 114 段航拍影片，涵盖风景、城市、太空和水下。影片从 Apple 服务器实时播放，镜头移动时会显示中文地名。左右键可以切换上一段 / 下一段，其他按键退出。
- **照片**：每日精选和你的照片缓慢推移，显示拍摄地点。
- **时钟**：大号时钟配合当前壁纸，每分钟换一个位置，防止烧屏。

使用 Android 标准屏保的电视（比如 TCL），Aerial 会成为系统屏保，在任何 App 里空闲都会启动。小米用的是自家的屏保机制，所以 Aerial 在桌面空闲 5 分钟时启动自己的屏保，早于小米的广告屏保。

### 壁纸与手机设置

<p align="center">
  <img src="site/img/wallpaper.jpg" width="74%" alt="壁纸与屏保面板">
  <img src="site/img/phone.jpg" width="20%" alt="手机上的设置页">
</p>

- **每日精选**：必应每日图片（国内直连），保留最近 8 张，也可以选「每日自动更新」。
- **内置**：8 组在电视上实时生成的柔光渐变，不含任何素材文件。
- **我的照片**：用手机扫码上传，传完立刻生效。
- **用手机设置**：扫描面板右侧的二维码，会打开一个 iOS 风格的网页，可以上传照片、挑选壁纸、切换屏保。这个页面只在壁纸面板打开时开放，关闭后再保留 5 分钟；地址里带随机令牌，同一网络里的其他设备猜不到。

## 安装

全程不需要 root，**不卸载、不停用任何系统应用**，可以随时一条命令还原。

### 第一步：打开电视的 ADB 调试

电脑和电视要连在同一个网络里。先在电视上找到 IP 地址（一般在「设置 → 网络」或「关于」里），再打开 ADB：

| 电视 | 怎么打开 |
|---|---|
| **小米 / Redmi**（PatchWall、HyperOS） | 设置 → 账号与安全 → **ADB 调试** 打开；同一页的 **安装未知来源的应用** 也打开 |
| **TCL / 雷鸟** | 设置 → 关于 → **本机信息**，进入后在遥控器上依次按 **上、下、左、右**（每次间隔不超过 1 秒），页面底部会出现隐藏的「更多」分组，选中 **ADB** 按确认打开 |
| **原生 Android TV / Google TV**、海信等 | 设置 → 设备偏好设置 → 关于 → 连续按 **版本号**（Build）7 次，打开开发者选项；再到 开发者选项 → **USB 调试**（有的叫「网络调试」/「ADB 调试」）打开 |

电脑第一次连接时，电视上会弹出「允许 USB 调试吗？」，选 **允许**（勾上「始终允许」）。

### 用 Agent 安装（推荐）

把下面这段复制给 Claude Code、Codex、Cursor 之类能在你电脑上运行命令的 Agent，把 `<电视IP>` 换成你的电视 IP：

```text
请帮我把开源电视桌面 Aerial（https://github.com/pengchujin/aerial-tv）安装到我的电视上，并让它成为开机自动启动的桌面。
我的电视 IP 是：<电视IP>

请按这个顺序做：
1. 读一下仓库 README 的「安装」部分和 docs/devices/ 下的机型笔记。
2. 确认电脑上有 adb、curl、python3（没有 adb 也可以，安装脚本会自动下载 Google 的 platform-tools）。
3. 运行 adb connect <电视IP>:5555。如果连不上，提醒我按 README 在电视上打开 ADB 调试；如果显示 unauthorized，提醒我在电视上点「允许」。
4. 运行一键安装：
   curl -fsSL https://raw.githubusercontent.com/pengchujin/aerial-tv/main/tools/get.sh | KEEP_ADB=1 TV=<电视IP> bash
   如果是 TCL，电视右上角会弹出「多屏互动：正在发起应用推送」，提醒我在电视上选「同意」。
5. 装完后用 adb shell dumpsys window | grep mCurrentFocus 确认前台是 app.orchard.tv；征得我同意后，再 adb reboot 一次，确认开机后直接进入 Aerial。
6. 不要卸载或停用任何系统应用，不要删除电视上不是这次安装创建的文件。遇到失败时，把命令输出原样给我看，再参考 docs/devices/ 排查。
```

### 一行命令安装（macOS / Linux）

```bash
curl -fsSL https://raw.githubusercontent.com/pengchujin/aerial-tv/main/tools/get.sh | KEEP_ADB=1 TV=192.168.1.20 bash
```

脚本会做这些事：
1. 下载最新版 APK 并安装。
2. 授予只有 ADB 能给的权限（用途见下表）。
3. 预编译 App，让它在电视上更流畅。
4. 接管桌面和屏保。
5. 放行开机自启。

`KEEP_ADB=1` 表示电视每次关掉 ADB 调试后，Aerial 都会立刻重新打开（小米待机唤醒后会关）。不需要可以去掉。

- **拒绝安装桌面类 App 的固件**：在命令最后加 `-s guard`，改装不声明 HOME 的版本，由桌面守护接管。
- **TCL**：TCL 拒绝一切 ADB 安装，脚本会改用 TCL 自带的「多屏互动」把 APK 推给电视，你只需要在电视上选一次「同意」。没有多屏互动时，会退回用当贝市场的「安装包管理」。

还原成原来的桌面：

```bash
curl -fsSL https://raw.githubusercontent.com/pengchujin/aerial-tv/main/tools/get.sh | TV=192.168.1.20 bash -s restore
```

### 手动安装

到 [Releases](https://github.com/pengchujin/aerial-tv/releases/latest) 下载 `aerial-standard.apk`（拒绝安装桌面类 App 的固件用 `aerial-guard.apk`），然后：

```bash
adb connect 192.168.1.20:5555
adb install -r aerial-standard.apk
adb shell pm grant app.orchard.tv android.permission.WRITE_SECURE_SETTINGS
adb shell cmd notification allow_listener app.orchard.tv/app.orchard.tv.guard.RevivalListener
adb shell appops set app.orchard.tv SYSTEM_ALERT_WINDOW allow
adb shell am start -n app.orchard.tv/.MainActivity
```

Aerial 启动后会自己打开桌面守护。完整的步骤（屏保、亮度、TCL 自启动）见 [tools/install.sh](tools/install.sh)。

## 它怎么接管桌面，又怎么不被杀掉

国内电视的固件大多只认自家桌面，还会在待机时清理第三方 App。Aerial 按「先温和、后强硬」的顺序处理：

1. **先正常申请**：申请 HOME 角色、设为默认桌面。原生 Android TV 和 Google TV 到这一步就够了。
2. **桌面守护**：固件仍然坚持用自家桌面时（小米、TCL 都是这样），由一个无障碍服务接管。它在系统之前接住遥控器的 HOME 键，所以不会闪出官方桌面；官方桌面一出现在前台，就把 Aerial 拉回来。这个服务不读取任何屏幕内容。
3. **被杀后复活**：小米待机时会强制停止所有第三方 App，守护服务也会随之被关。Aerial 注册了一个通知监听服务（不读取任何通知），系统在 App 被杀后会主动重新绑定它，Aerial 借此在约 0.5 秒内复活，并用 `WRITE_SECURE_SETTINGS` 把守护服务重新打开。
4. **开机自启**：开机时，通知监听被系统绑定，Aerial 随之启动并接管桌面。TCL 还有一层「自启动管理」，默认禁止第三方 App 开机启动，安装脚本会用 `appops` 放行（`APP_AUTO_START`、`APP_ASSOC_START`）。
5. **ADB 常开**（可选）：小米唤醒后会关闭 ADB 调试，开启 `KEEP_ADB` 后 Aerial 会立刻把它打开，方便以后升级。

以上每一条都在小米 MiTV-MFTR0 和 TCL MT5879 上实测过，包括断电重启。

### ADB 授予的权限及用途

| 权限 | 用途 |
|---|---|
| `WRITE_SECURE_SETTINGS` | 守护服务被系统关闭后重新打开；保持 Aerial 为系统屏保；`KEEP_ADB` 重新打开 ADB；写入小米的背光值 |
| 通知监听 | 被强制停止或开机后由系统重新拉起，不读取任何通知 |
| 悬浮窗（`SYSTEM_ALERT_WINDOW`） | 开机后从后台打开 Aerial 所需的系统豁免 |
| 修改系统设置 | 用标准接口调节亮度（小米和 TCL 之外的电视） |
| 定位 | 仅用于读取 Wi-Fi 名称 |
| TCL `APP_AUTO_START` / `APP_ASSOC_START` | 允许开机自启（只有 TCL 固件有这两项） |

## 已验证的电视

| 机型 | 系统 | 状态 | 笔记 |
|---|---|---|---|
| 小米 MiTV-MFTR0 | Android 14 / HyperOS TV | 日常使用中 | [docs/devices/xiaomi-mitv-mftr0.md](docs/devices/xiaomi-mitv-mftr0.md) |
| TCL MT5879 国行 | Android 11 | 日常使用中 | [docs/devices/tcl-mt5879-cn.md](docs/devices/tcl-mt5879-cn.md) |

其他品牌（海信、创维、索尼等）按同样的思路应该可以工作，但还没在真机上验证。欢迎[提交 Issue](https://github.com/pengchujin/aerial-tv/issues) 或 PR 补充你的机型。

## 已知限制

- **网络**：航拍影片和每日精选都需要联网。航拍按 1080p H.264 实时播放，码率约 15–20 Mbps。
- **HDMI 设备名**：真实名称（如「Apple TV」）只有系统权限才读得到，所以统一显示为「已连接」。
- **TCL 音量条**：TCL 调音量时会同时弹出它自己的音量条，这是让音量真正生效的必要条件。
- **小米屏保**：在小米上，如果停在别的 App 里空闲，仍然会出现小米自己的屏保。
- **网页安装**：浏览器不能直接连接电视的网络 ADB（没有原生 TCP），电视的 USB 口也不能作为设备被电脑连接，所以没有提供「打开网页就能装」的方式。最接近的是上面的 Agent 提示词和一行命令。

## 开发

需要 JDK 17、Android SDK 36。

```bash
./gradlew assembleStandardRelease assembleGuardRelease
BUILD=1 KEEP_ADB=1 TV=<电视IP> tools/install.sh      # 用本地编译的 APK 安装
```

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)：代码结构与关键数据流
- [docs/HANDOFF.md](docs/HANDOFF.md)：现状、调试技巧、必须遵守的规则
- [docs/devices/](docs/devices/)：各机型的实测笔记，包括 TCL 多屏互动的推送协议
- [AGENTS.md](AGENTS.md)：给 AI 代理的开发说明
- [site/](site/)：官网（纯静态，可以直接部署到 Cloudflare Pages）

设计上的几点取舍：
- **画布**：统一按 960×540dp 的画布设计并等比缩放，不同屏幕密度的电视看起来完全一样。
- **性能**：电视 GPU 很弱，每帧最多一次全屏绘制，空闲时不跑任何动画。
- **动效**：焦点动画用弹簧，可以中途打断。
- **字体**：Inter 可变字体（OFL 许可）。

航拍目录 `app/src/main/assets/aerials.json` 由 Apple 公开的 `resources-16.tar` 生成，只包含影片地址和地名，不含任何影片。

## 许可

[MIT](LICENSE)。Inter 字体使用 [SIL Open Font License](FONT-LICENSE-OFL.txt)。

Aerial 是独立的开源项目，与 Apple、小米、TCL 均无关联。航拍影片版权归 Apple 所有，由 Apple 的服务器直接播放；每日精选图片来自必应，版权归原作者所有。
