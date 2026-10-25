# 小米 MiTV-MFTR0（codename freeguy）

- Android 14（SDK 34），HyperOS TV，固件 OS3.0.101.0.UFTMATV，MediaTek MT9653，32 位 ARM（armeabi-v7a），4 GB 内存。
- 1920×1080，320dpi（960×540dp）。GPU 很弱，见 ARCHITECTURE.md 的性能约束。
- 原桌面：`com.mitv.tvhome/.MainActivityUserMode`（PatchWall）。备用桌面：`com.xiaomi.mitv.settings/.entry.FallbackHome`（priority -1000）。
- 安装的 flavor：`standard`。普通的 `adb install` 就能装。

## HOME 锁定

- `cmd role add-role-holder android.app.role.HOME app.orchard.tv` 和 `cmd package set-home-activity` 都返回成功，但 `resolve-activity` 仍然返回 `com.mitv.tvhome`。
- 原因：`com.android.server.pm.MitvSettings.isThirdpartyHomeAllowed(pkg)` → `isAppInWhitelistWithFlag(pkg, 4)`。白名单来自 `/data/system/mitv_features.xml` 和 `persist.appcontrol_*` 属性，只有系统进程能写。
- **不要停用 `com.mitv.tvhome`**：停用后 HOME 解析到 FallbackHome，而 FallbackHome 也不认第三方 HOME，会一直黑屏并刷日志「User unlocked but no home」。实测过，之后已立即 `pm enable` 还原。
- 解决办法：桌面守护（无障碍）。实体遥控器的 HOME 键会被 `onKeyEvent` 在系统处理前拦下，不会闪出原桌面。

## 待机会强制停止第三方 App

- 待机（遥控器关机，实际是待机；`/proc/uptime` 不会归零）时，`MitvActivityManagerService.killAllThirdApp` → `killAllThirdAppSkipFgPkg` 对所有非系统 App 执行 `forceStopPackage`。
- 豁免条件：系统应用、`MitvConfigParser.isAppInWhiteList(0x8000)`（云端规则）；以及 STR 模式（`persist.sys.system_str == 1`，这台为空）下的 `strKillWhiteList`（来自 `Settings.System str_killbackgroud_pkg_white_list`，只在开机时读取）。在这台电视上，都没有可用的正规途径。
- 后果：Android 对被强制停止的 App 会移除它的无障碍服务 → 唤醒后守护失效。
- 解决办法：`RevivalListener`（通知监听）。系统会在绑定断开后主动重绑，日志里可以看到 `notification listener binding died` 然后 `binding:`，约 0.5 秒。复活后 `GuardSetup` 用 `WRITE_SECURE_SETTINGS` 重新写入守护服务。已实测：待机再唤醒后直接是 Aerial。

## ADB 会被关闭

- 唤醒后 `global adb_enabled` 会被系统改成 0（`dumpsys settings` 的历史里可以看到）。
- 开了 `keep_adb`（`KEEP_ADB=1 tools/install.sh`，或用 `ConfigReceiver` 的广播）后，守护服务监听这个设置，立刻写回 1。实测唤醒后 3 秒内 ADB 可连。

## 屏保

- 不用 Android 标准的 Dream（`screensaver_enabled=0`）。由小米的 PowerManagerService 在空闲 `system screen_saver_timeout`（480000 = 8 分钟）后发出 `com.duokan.intent.action.show.screensaver`，交给 `com.xiaomi.mitv.hyper.screensaver` 显示。这个广播受保护，shell 发不出去。
- 解决办法：Aerial 在桌面空闲 5 分钟时启动自己的屏保，同时加 `FLAG_KEEP_SCREEN_ON`，小米的屏保就不会再触发。停在别的 App 里空闲时，小米屏保仍可能出现。

## 其他

- 背光：`Settings.Global picture_backlight`（0–100），改了以后 `tvmenusettingsservice` 会立即应用。需要 `WRITE_SECURE_SETTINGS`。
- 音量：普通的 `adjustStreamVolume` 就能生效。
- 信号源：HW5/6/7 = HDMI 1/2/3（HDMI 2 是 eARC），HW8 是不存在的第 4 个口（已隐藏）；HW2 = AV，HW1 = ATV，HW0 = DTMB。切换用 `com.xiaomi.mitv.tvplayer` 的 Intent（参考 tv-vibehack 仓库的设备笔记）。CEC 的 OSD 名（如「Apple TV」）只有系统权限能读，第三方 App 拿到的是 `Playback_1`。
- 设置：`com.xiaomi.mitv.settings/.entry.MainActivity`；Wi-Fi：`.wifi.MitvWifiActivity`。
- 截图：`adb shell screencap -p /data/local/tmp/x.png` 再 `adb pull`（直接 `exec-out` 在部分小米机型上会坏）。视频画面能截到。
- 还有一个 `pi_config` 黑名单（广电合规），里面有很多第三方桌面、文件管理器的包名，Aerial 不在其中。

## 没有标准桌面入口的系统 App

小米桌面会显示几个系统 App，但它们没有声明 `CATEGORY_LAUNCHER` / `LEANBACK_LAUNCHER`，一般的查询找不到。`AppCatalog` 里单独处理了这几个（1.7 起）：

| App | 入口 | 怎么找到 |
|---|---|---|
| 应用中心 `com.xiaomi.mitv.appstore` | `.MainActivity` | 分类 `android.intent.category.APPSTORE_LAUNCHER`（按分类查询，不写死） |
| 高清播放器 `com.xiaomi.mitv.mediaexplorer` | `.NewScraperMainEntryActivity` | 写死在 `UNLISTED_ENTRIES`；另一个 MAIN 入口 `.xq.XQMainEntryActivity` 打开是空白页 |
| 米家 `com.xiaomi.smarthome.tv` | `.ui.MainActivity` | 写死在 `UNLISTED_ENTRIES` |

其余有 MAIN 但没有桌面分类的（`mitv.mivideoplayer`、`mitv.upgrade`、`xm.webcontent`、`mi_connect_service` 等）是播放器、升级、弹窗之类，不是给用户直接打开的。
