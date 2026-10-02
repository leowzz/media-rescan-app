# 媒体库刷新 · Media Rescan

[![Build tagged release](https://github.com/leowzz/media-rescan-app/actions/workflows/release.yml/badge.svg)](https://github.com/leowzz/media-rescan-app/actions/workflows/release.yml)

一个简洁的 Material 3 Android 工具：Syncthing 同步来的照片在文件管理器里能看到，却没有出现在微信或抖音的选图列表时，手动重新扫描指定目录。

## 使用

1. 从 [Releases](https://github.com/leowzz/media-rescan-app/releases) 下载并安装 APK（Android 11 及以上）。
2. 首次打开，允许“所有文件访问权限”。
3. 点击“添加目录”，逐个添加同步目录。
4. 点击“重新扫描”。完成后重新进入其他应用的选图页面。

列表自动保存，可随时移除目录；移除不会删除文件。扫描包含子目录，并合并重叠范围。扫描期间请保持应用页面打开。主页只显示进度和简要结果，完整统计和错误可在“详情”中查看。

支持系统深浅色主题、Android 12 及以上动态配色。无需 Root、Shizuku 或网络权限。

## 扫描范围

通过 Android `MediaScannerConnection.scanFile` 更新 MediaStore。确认入库数量包含已存在的记录，不等于新增数量。自动跳过隐藏目录、`.nomedia`、符号链接目录和 `Android` 私有目录；失效目标单独报告，其他有效目录继续扫描。

仅支持内部共享存储。不扫描外置 SD 卡，也不执行 ADB 特权全盘扫描或清理所有失效索引。应用自身的媒体访问权限和不受支持的格式问题需要另行处理。

## 本机开发

使用 JDK 17、Android SDK 35，项目自带 Gradle Wrapper。配置 `ANDROID_HOME` 或在未跟踪的 `local.properties` 中设置 `sdk.dir`。

```sh
./gradlew testDebugUnitTest lintRelease assembleDebug
```

Windows 使用 `gradlew.bat`。Debug APK 位于 `app/build/outputs/apk/debug/`；Debug 签名不同于发行版，不用于覆盖发行版。

构建签名发行版需要以下环境变量：

- `APP_VERSION`：例如 `v1.3.0`；未设置时使用当前开发版本。
- `SIGNING_STORE_FILE`：签名密钥的绝对路径。
- `SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`：签名配置。

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease
```

签名 APK 位于 `app/build/outputs/apk/release/app-release.apk`。未配置签名时本机生成 unsigned APK；GitHub 发行流程要求有效签名，缺少密钥会失败。`build.ps1 -Version v1.3.0` 为原开发环境保留了本地签名快捷入口。

## Tag 发行

推送 `vMAJOR.MINOR.PATCH` 格式的 Tag，GitHub Actions 自动运行测试、Lint、R8/资源压缩、发行版签名及签名/版本校验，随后创建 GitHub Release 并上传 APK 和 SHA-256 校验文件。构建报告和混淆映射同时保存为 Actions artifacts。

```sh
git tag v1.3.1
git push origin v1.3.1
```

Tag 是发行版本的唯一来源：`v1.3.1` 对应 `versionName=1.3.1`，`versionCode=10301`。版本码公式为 `major * 10000 + minor * 100 + patch`，每段允许 0–99，发行时递增版本。失败后可修复代码并推送新的版本 Tag；不改写已发布的 Tag。

仓库需要以下 Actions Secrets：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 原发行密钥文件的 Base64 |
| `ANDROID_STORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | 签名别名 |
| `ANDROID_KEY_PASSWORD` | 私钥密码 |

密钥不进入 Git 或公开产物。保持相同签名和应用 ID，即可覆盖升级并保留目标列表；R8 配置也保留了偏好文件依赖的 Activity 类名。

## 验证

JUnit 覆盖目录边界、缺失目录、隐藏/私有目录、上级 `.nomedia`、重复项、两种父子添加顺序、兄弟目录和空列表。Android Lint 在本机和 Tag 构建中执行。

## API 文档

- [MediaScannerConnection](https://developer.android.com/reference/android/media/MediaScannerConnection)
- [所有文件访问权限](https://developer.android.com/training/data-storage/manage-all-files)
- [Material Components for Android](https://github.com/material-components/material-components-android)
