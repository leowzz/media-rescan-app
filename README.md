# 媒体库刷新

自用 Android 小工具，适用于 Android 11 及以上。同步照片后，打开应用，点击“立即重新扫描”。首次使用需在系统设置允许“所有文件访问权限”。无需 Root、Shizuku、网络或电脑连接。

扫描期间保持页面打开；应用会保持屏幕常亮。支持停止扫描，保存上次结果。切换横竖屏不会重复启动扫描。系统终止进程后，下次打开会提示上次扫描中断。

## 原理与范围

遍历内部共享存储中的照片、视频和音频，通过 Android `MediaScannerConnection.scanFile` 提交给系统。结果中的“系统确认入库”表示扫描回调返回媒体 URI，包含原本已入库的文件，并不等于新增数量。

自动跳过隐藏文件/目录、带 `.nomedia` 的目录、目录符号链接，以及共享存储顶层的 `Android` 目录。不会移除 `.nomedia`，也不会修改照片文件。无法读取的目录和未确认入库的文件会显示计数。单文件扫描等待超过 30 秒会结束并提示重试。

此应用使用普通应用可调用的逐文件扫描接口，不等同于 ADB 的特权 `scan_volume`：不负责清理所有失效索引，也不扫描应用私有目录或外置 SD 卡。它无法修复微信/抖音自身的照片访问权限限制或不支持的格式。

## 构建

运行 `powershell -ExecutionPolicy Bypass -File .\build.ps1`。脚本使用本机 Android SDK 35 和 JDK 17，无 Gradle 或第三方依赖。若环境变化，修改脚本中的 SDK 和 JDK 路径。

产物：`MediaRescan.apk`。`build/media-rescan.keystore` 为个人安装使用的本地签名密钥（默认口令 android），请保留，以便后续覆盖升级；不适用于公开发行。

## 参考

- https://developer.android.com/reference/android/media/MediaScannerConnection
- https://developer.android.com/training/data-storage/manage-all-files
