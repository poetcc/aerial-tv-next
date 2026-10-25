# Aerial Next 维护交接

当前仓库：<https://github.com/poetcc/aerial-tv-next>。来源和开发基线见 [UPSTREAM](../UPSTREAM.md)。主应用 `app/` 与可选历史兼容助手 `sonybridge/` 分开构建；源码版本分别为 code26、code6。

本地构建与38项单元测试已通过。在小米 Android 11 验证过四平台记录、搜索导航、输入法上键收起、HOME、屏保焦点和已授权的ADB自动恢复。新版待机/重启稳定性、完整中文候选输入及其他品牌仍待更多测试。设备目录中的原版机型结论仅供参考。

## 构建和本地资料

使用 JDK17、Android SDK36、Gradle Wrapper：

```sh
./gradlew testStandardReleaseUnitTest assembleStandardRelease assembleGuardRelease :sonybridge:assembleRelease
BUILD=1 KEEP_ADB=1 TV=<电视IP> tools/install.sh
```

真实电视地址、授权原值、日志、签名及本机测试证据放 `local/`，不提交。签名配置用 `local/signing.properties`；没有时回落到开发签名。应用和助手必须同签名。

## 必须遵守

1. 不卸载、停用系统应用；尤其不要停用小米系统桌面。
2. 更改无障碍服务列表时保留所有其他服务；延时重绑后重新读取列表。
3. 不删除或重置用户照片、壁纸、记录和其他应用文件；只清理本次创建且记下名称的临时数据。
4. 不上传 `local/`、`research/`、`.wrangler/`、凭据、固件、第三方APK或电视截图。
5. 真机使用 release 构建并执行 `cmd package compile -m speed -f app.orchard.tv`。按键操作前确认当前画面和焦点；长间隔测试先检查空闲屏保。
6. 修改系统设置前记录原值，并确保可恢复；重启前取得电视使用者同意。
7. 不将“编译通过”写成“功能已验证”。分别报告自动测试、真机检查及尚未确认的边界。
8. 保持Android5.0兼容，不升级到要求minSdk23的新Compose版本；新系统API按版本判断。
9. 用zsh时写`${VAR}:...`，避免冒号被解释成路径修饰符。

## 发布

首次公开为源码，尚无正式APK Release。脚本的默认下载仓库已经改为本仓库；发布前先保存固定签名、提升版本号、运行相关构建与测试，并完成对应真机验证。

可选网页位于 `site/`，现有截图来自上游。`wrangler.jsonc` 不再绑定上游域名；不要向上游的Cloudflare账户或域名部署。所有新发布和反馈以本仓库为准。
