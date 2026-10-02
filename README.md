# 媒体库刷新

自用 Android 小工具，适用于 Android 11 及以上。打开应用，点击“添加目标目录”，进入目标文件夹后点“添加此目录”；重复操作可建立多个目录的目标列表。同步照片后，点击“重新扫描列表中的目录”即可批量扫描。首次使用需在系统设置允许“所有文件访问权限”。无需 Root、Shizuku、网络或电脑连接。

只遍历列表中的目录及其子目录，自动保存添加顺序。每行的“移除”只修改列表，不删除文件。同一个目录不能重复添加；列表同时包含父目录和子目录时，扫描会自动合并范围，避免重复遍历。扫描期间禁止修改列表。升级自 1.1 时，原先选择的目录自动成为列表的第一项；空列表禁用扫描按钮，删除所有目录后重启也不会恢复旧目录。

目录选择器支持返回上一级；在内部存储根目录点击“添加整个内部存储”可手动选择全盘扫描。含 `.nomedia`、隐藏目录和 Android 私有目录不列入可选目录；扫描前再次校验目录是否仍存在及上级目录是否被排除。无法使用的目标会单独显示路径和原因，其他有效目标继续扫描。

扫描期间保持页面打开；应用会保持屏幕常亮。支持停止扫描，保存上次结果。切换横竖屏不会重复启动扫描。系统终止进程后，下次打开会提示上次扫描中断。

## 原理与范围

遍历内部共享存储中目标列表内的照片、视频和音频，通过 Android `MediaScannerConnection.scanFile` 提交给系统。结果中的“系统确认入库”表示扫描回调返回媒体 URI，包含原本已入库的文件，并不等于新增数量。扫描结果会记录本次目标列表、合并重叠后的目录数量，以及跳过的无效目标。

自动跳过隐藏文件/目录、带 `.nomedia` 的目录、目录符号链接，以及共享存储顶层的 `Android` 目录。不会移除 `.nomedia`，也不会修改照片文件。无法读取的目录和未确认入库的文件会显示计数。单文件扫描等待超过 30 秒会结束并提示重试。

此应用使用普通应用可调用的逐文件扫描接口，不等同于 ADB 的特权 `scan_volume`：不负责清理所有失效索引，也不扫描应用私有目录或外置 SD 卡。它无法修复微信/抖音自身的照片访问权限限制或不支持的格式。

## 构建

运行 `powershell -ExecutionPolicy Bypass -File .\build.ps1`。脚本使用本机 Android SDK 35 和 JDK 17，无 Gradle 或第三方依赖。若环境变化，修改脚本中的 SDK 和 JDK 路径。

产物：`MediaRescan.apk`。`build/media-rescan.keystore` 为个人安装使用的本地签名密钥（默认口令 android），请保留，以便后续覆盖升级；不适用于公开发行。

## 验证

目录边界、重复目录、父子目录两种添加顺序、同名前缀兄弟目录、根目录覆盖和空列表测试：

```powershell
javac -encoding UTF-8 -d build/tests src/local/mediarescan/ScanScope.java tests/local/mediarescan/ScanScopeTest.java
java -cp build/tests local.mediarescan.ScanScopeTest
```

1.2 已在当前连接手机验证：1.1 的 `DCIM/Nikon` 选择迁移到目标列表；加入 `syncthing` 后，批量扫描结果为 2100/2100 个文件均获得系统入库确认。强制停止并重新打开后，两个目标目录的顺序与扫描结果仍然保留。目录范围与重叠去重的 JVM 测试通过。

## API 文档

- https://developer.android.com/reference/android/media/MediaScannerConnection
- https://developer.android.com/training/data-storage/manage-all-files
