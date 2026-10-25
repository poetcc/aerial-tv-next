# 参与 Aerial Next

在本仓库提交 Issue 或 Pull Request；通用上游问题可以同时参考 pengchujin/aerial-tv，但请说明使用的是 Aerial Next。

## 开发

使用 JDK 17、Android SDK 36。桌面按 960×540dp 画布设计。保持 minSdk 21 和现有兼容依赖；不要为了新接口直接提升最低系统版本。

```sh
./gradlew testStandardReleaseUnitTest assembleStandardRelease assembleGuardRelease :sonybridge:assembleRelease
```

按改动范围补充有效测试。界面和系统行为需要真机验证；使用 release 构建，记录机型、Android 版本和具体结果，未验证的行为明确标注。遥控控件使用 Pressable；界面文字同时维护英文和中文资源。

## 提交与反馈

- 不提交 `local/`、`research/`、`.wrangler/`、签名密钥、APK、固件、个人照片、原始观看记录、电视 IP 或账户令牌。
- 系统行为按功能检测实现；不要停用、卸载系统桌面，不覆盖其他无障碍服务。
- 记录协议或搜索路由的来源及兼容范围，使用匿名化或合成测试数据，不提交平台凭据。
- 同步上游改动时注明原仓库及提交。提交到本项目的贡献按 MIT 许可证分发，并保留必要的第三方声明。

发布构建需要维护者在本地或安全的发布环境保存固定签名。CI 不包含发布私钥，也不把开发签名产物当成正式更新包。
