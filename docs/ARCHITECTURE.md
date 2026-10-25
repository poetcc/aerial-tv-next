# Aerial Next 架构

本文基础来自原版 Aerial。本分支新增 `media/` 的播放记录和来源合并、`search/` 的目录路由／中立排序／方向键图，以及 `ui/home/` 的记录磨砂面板和搜索页。`sonybridge/` 是同签名的可选兼容接收器。上游基线及权利说明见 [UPSTREAM](../UPSTREAM.md)。

本分支应用磁贴使用放大与柔光，不再画外部白色焦点框；历史或搜索浮层、屏保显示时禁用底层焦点。HOME 统一清除 ViewModel 的浮层状态，原生输入框在完成输入后可用上键收起输入法并选结果。旧文中关于原版白边的描述仅供设计沿革参考。

Android TV 主应用（`app/`）与可选历史接收器（`sonybridge/`），Kotlin + Jetpack Compose（没有用 androidx.tv 和 Material，全部自绘）。包名 `app.orchard.tv`（产品名是 Aerial，包名沿用旧名 Orchard：改包名会让电视把它当成新 App，丢掉户主数据和全部授权），minSdk 21（Android 5.0），targetSdk 34，compileSdk 36。Compose 固定在 1.9.x（BOM 2025.11.01）：1.10 起要求 Android 6.0。比 Android 5.0 新的接口都要按 `Build.VERSION.SDK_INT` 判断，`./gradlew lintStandardRelease` 的 NewApi 检查能找出漏掉的地方。

## 目录一览

```
app/src/main/java/app/orchard/tv/
├── MainActivity.kt          HOME Activity：窗口设置、960×540 画布密度、广播/网络监听、空闲计时与桌面内屏保、启动 App
├── HomeViewModel.kt         首页状态：App 列表、浮层（Overlay）、系统状态（网络/音量/亮度/信号源/桌面状态）、移动模式
├── apps/
│   ├── Apps.kt              AppCatalog：枚举 LEANBACK_LAUNCHER + LAUNCHER，按包去重，缓存磁贴图（按 lastUpdateTime）
│   ├── TileArt.kt           磁贴图生成：横幅 / 自适应图标双层 / 老式图标毛玻璃底板；主色；每 App 的 64×36 环境光背景
│   └── LayoutStore.kt       用户排序与隐藏（SharedPreferences "layout"），首次运行的默认排序
├── ui/
│   ├── theme/Theme.kt       Palette（tvOS 深色系统色）、Motion（缓动曲线）、Inter 字体（opsz 14/32）、Type
│   ├── theme/Glyphs.kt      自绘 SF Symbols 风格图标（Canvas 线条）
│   ├── components/Pressable.kt  遥控器可按控件：按下缩放、只在「本控件上开始的按压」抬起时触发
│   ├── home/HomeScreen.kt   首页布局：网格几何、滚动、焦点管理、各浮层挂载、返回键
│   ├── home/AppTile.kt      单个磁贴：聚焦弹簧、阴影、柔光、视差、边缘回弹、长按、移动抖动
│   ├── home/HomeChrome.kt   背景（AmbientBackground）、大时钟、右上角状态胶囊、时间格式
│   ├── home/Overlays.kt     长按菜单、隐藏的 App 面板、MenuRow、MiniTile
│   ├── home/Screensaver.kt  屏保 UI：航拍（VideoView）/ 照片（Ken Burns）/ 时钟
│   ├── control/ControlCenter.kt  控制中心：按钮、音量/亮度滑块、信号源子页
│   └── wallpaper/WallpaperSheet.kt  壁纸与屏保面板 + 手机扫码卡片
├── wallpaper/
│   ├── Wallpapers.kt        壁纸单例：来源（每日/内置/照片/跟随App）、选择、首页背景烘焙、缩略图、上传
│   ├── Gradients.kt         8 组内置渐变的参数与渲染
│   ├── Images.kt            解码裁剪、首页压暗烘焙、抖动、box blur
│   ├── Aerials.kt           读取 assets/aerials.json（Apple 航拍目录）
│   └── PhoneServer.kt       NanoHTTPD 手机配置页 + 二维码生成
├── settings/
│   └── AerialSettings.kt    Aerial 自己的偏好（SharedPreferences "settings"）：焦点样式 FocusStyle，默认高对比度
├── system/
│   ├── SystemState.kt       SystemProbe：网络、音量、桌面状态、设置入口、信号源（TvInputManager + 小米路由 + 机型修正）
│   └── Brightness.kt        亮度：小米 picture_backlight / 标准 screen_brightness / TCL 走面板
├── guard/
│   ├── HomeGuardService.kt  无障碍「桌面守护」：拦 HOME 键、原桌面出现就切回、亮屏检查、睡眠、ADB 保持
│   ├── GuardSetup.kt        用 WRITE_SECURE_SETTINGS 重新开启守护 / 保持 ADB / 「已启用未绑定」时强制重绑
│   ├── RevivalListener.kt   通知监听服务：被强杀后由系统重绑，借此复活并重开守护
│   ├── BootReceiver.kt      真正重启后的兜底
│   └── ConfigReceiver.kt    ADB 专用配置入口（keep_adb），要求发送方持有 WRITE_SECURE_SETTINGS
└── dream/AerialDream.kt    系统级屏保（DreamService + Compose）

app/src/main/assets/aerials.json   Apple 航拍目录（114 条：地址 + 中英文地名 + 时间点说明）
app/src/main/assets/web/index.html 手机配置页（单文件，iOS 风格，深浅色）
app/src/standard/AndroidManifest.xml  只给 standard 版本加 HOME intent-filter
tools/install.sh / restore.sh       部署与还原
```

## 两个构建版本（flavor）

| flavor | HOME 声明 | 用途 |
|---|---|---|
| `standard` | 有（`src/standard/AndroidManifest.xml` 合并进去） | 能装桌面类 APK 的电视（小米、原生 Android TV 等） |
| `guard` | 无 | 拒绝安装第三方桌面的固件（TCL 的 `LauncherStrategy`），只靠桌面守护接管 |

两者代码完全相同，只差 manifest。

## 首页

- **画布**：`MainActivity` 用 `LocalDensity` 把内容缩放到 960×540dp 的设计画布（`density = 屏幕宽 / 960`）。不同厂商的 1080p 屏幕 dpi 不同（小米 320，TCL 240），这样排版完全一致。Compose 里所有 dp/sp 都按这个画布理解。
- **网格**（`HomeScreen.GridMetrics`）：5 列，16:9 磁贴，边距 56dp、间距 28dp，第一行在屏幕高 55.5% 处。磁贴用绝对 `offset` 定位，没有用 Row，所以移动 App 时组件不会重建、焦点不会丢，位置变化由 `animateIntOffsetAsState` 做重排动画。
- **滚动**：不用 `verticalScroll`（它会自动 bringIntoView），而是一个 `Animatable` 加内容 `offset`，目标由聚焦行决定（`scrollFor`），用弹簧动画以便连按时平滑改变目标。
- **焦点**：每个磁贴一个 `FocusRequester`（按包名存在 map 里）。方向键由磁贴自己处理：`moveFocus` 失败时触发边缘回弹。浮层打开时磁贴 `focusable(enabled=false)`，焦点不会逃出浮层；浮层关闭后恢复到原磁贴。HOME 键重置通过 `homeResetPending` 加「窗口重新获得焦点」两个条件触发（在别的 App 在前台时请求焦点会丢）。
- **返回键**：网格里 → 回第一行；第一行 → 启动屏保（tvOS 行为）。
- **焦点样式**：`AppTile` 聚焦时放大 1.15 倍，加阴影、顶部高光和加粗的名字。`FocusStyle.HighContrast`（默认）时，另在磁贴外 5dp 处画 3dp 白色描边（外加一圈淡黑边，保证在亮壁纸上也看得见）。描边画在一个不裁剪的独立图层上，和磁贴共用放大、倾斜、推移、抖动这些变换。起因：国产电视都是方向键遥控器，没有 Siri 遥控器那种跟着手指的倾斜，只靠放大的话用户找不到焦点。
- **背景**：`Wallpapers.backdrop`（选中壁纸，已烘焙压暗），否则（「跟随 App」模式）用聚焦 App 的 `TileArt.backdrop`。整屏只画一张图。

### 性能约束（重要）

电视 GPU 很弱，这里的瓶颈是全屏填充率。第一版在小米上帧耗时中位数 65ms，改完后约 18ms。
- 首页每帧只允许一次全屏绘制：壁纸和压暗层在 CPU 上预先合成；窗口背景设为 null。
- 空闲时不能有任何持续动画（测量结果是 0 帧）。
- 模糊（`BlurEffect`）只在控制中心 / 隐藏 App 面板打开时用。
- 加任何全屏效果前，先测：`adb shell dumpsys gfxinfo app.orchard.tv framestats`。
- 一定要装 release 版，并执行 `cmd package compile -m speed -f app.orchard.tv`。debug 版的 Compose 在电视上明显卡顿。

## 磁贴图（TileArt）

优先级：TV 横幅 → 自适应图标（背景层铺满，前景层单独保存，倾斜时做视差；前景超出安全区的当老式图标处理）→ 老式图标（放在用图标自身放大模糊做成的毛玻璃底板上，四角不透明的方形图标裁成圆角）。同时算出主色和 64×36 的环境光背景。所有位图都在 `Dispatchers.Default` 上生成，按包名和 `lastUpdateTime` 缓存。

## 壁纸与屏保

- `Wallpapers` 是 Compose 状态单例，只在主线程写。来源：
  - `daily:<date>`：必应 HPImageArchive，`cn.bing.com`，最近 8 张，存在 `files/daily/`；
  - `daily:latest`：一直跟随最新一张；
  - `grad:<key>`：内置渐变；
  - `photo:<id>`：手机上传的照片，存在 `files/photos/`，id 是毫秒时间戳的 36 进制；
  - `auto`：跟随 App。
- 首页背景是 1280×720 的图，压暗层已经烘焙进去（`Images.shadeForHome`）。
- 屏保有两条路径：
  1. **桌面内**：`MainActivity` 空闲计时 5 分钟，或在第一行按返回、点「立即开始」→ `vm.screensaverVisible`，同时加 `FLAG_KEEP_SCREEN_ON`，用来压住厂商屏保（小米靠这条）。
  2. **系统级**：`AerialDream`，由 `install.sh` 在使用标准屏保（`screensaver_enabled=1`）的电视上写入 `screensaver_components`（TCL 靠这条）。
- 航拍：`assets/aerials.json` 只含地址和地名，影片实时从 `sylvan.apple.com` 播放（1080p H.264）。连续 3 次播放失败就改用照片屏保。

## 手机配置（PhoneServer）

NanoHTTPD 监听 8790 端口，路径前缀是 8 位随机令牌（每次打开面板都重新生成，二维码里带着）。只在壁纸面板打开时运行，关闭后再保留 5 分钟。接口：`GET /` 页面、`GET api/state`、`GET thumb/<id>`、`POST api/wallpaper|screensaver|delete`（表单）、`POST api/upload`（multipart，可多张）。页面在 `assets/web/index.html`。

## 桌面接管（guard/）

层次和原因见 `docs/devices/`。数据流：

```
安装：install.sh 授权（WRITE_SECURE_SETTINGS、通知监听、SAW、WRITE_SETTINGS）
  → MainActivity.onStart → GuardSetup.ensureEnabled()（把守护写进 enabled_accessibility_services）
  → 5 秒后 rebindIfStalled()（已启用但没运行 → 先移除、1.5 秒后加回，触发重绑）
运行：HomeGuardService
  - onKeyEvent(HOME) → 不是默认桌面时吞掉按键，打开 Aerial
  - TYPE_WINDOW_STATE_CHANGED → 原桌面的 HOME Activity 到前台 → 打开 Aerial
  - SCREEN_ON → 1.2 秒后如果最后的窗口是原桌面（或者未知）→ 打开 Aerial
  - adb_enabled 的 ContentObserver → keep_adb 时写回 1
被杀（小米待机 killAllThirdApp）：系统移除无障碍授权
  → 通知监听绑定断开 → 系统约 0.5 秒后重绑 RevivalListener（进程复活）
  → GuardSetup.ensureEnabled() → 守护重新启用 → onServiceConnected 判断为「刚被复活」→ 打开 Aerial
```

`allowStockLauncher`：用户从网格里主动打开原桌面时，1 小时内不拦截。

## 系统能力（system/）

- **信号源**：`TvInputManager` 列出直通输入和调谐器。装了 `com.xiaomi.mitv.tvplayer` 的走小米的 Intent（HDMI 的 `input = HW 编号 + 18`，AV=2，ATV/DTMB 各自的 Activity），否则走 `TvContract` 的通用 URI。CEC 子输入的名字用于显示设备名，`Playback_1` 这类通用名会被过滤。`DeviceProfile` 存放按机型的修正（MFTR0 隐藏不存在的 HW8，并重命名调谐器）。
- **音量**：`AudioManager` 的 STREAM_MUSIC；TCL 必须加 `FLAG_SHOW_UI`（否则不会写入功放）。
- **亮度**：见 `Brightness.kt` 的注释（小米 / 标准 / TCL 面板三种）。
- **设置入口**：优先厂商自己的设置（小米 `com.xiaomi.mitv.settings`）。

## 资源与第三方

- 字体：Inter 可变字体（OFL，许可证在 `FONT-LICENSE-OFL.txt`）。
- 依赖：Compose BOM 2026.02.01、activity-compose、lifecycle-viewmodel-compose、core-ktx、NanoHTTPD 2.3.1、ZXing core 3.5.3。
- 航拍目录由 Apple 的 `resources-16.tar` 生成（生成方法见 HANDOFF.md）。
