package local.mediarescan;

import java.io.File;
import java.io.IOException;

/** Validate persisted selections as well as picker entries, including ancestor exclusions. */
final class ScanScope {
    static void validate(File storage, File target) throws IOException {
        File root = storage.getCanonicalFile();
        File selected = target.getCanonicalFile();
        if (!selected.toPath().startsWith(root.toPath()))
            throw new IOException("请选择内部共享存储中的目录");
        if (!target.getAbsoluteFile().equals(selected))
            throw new IOException("不支持扫描符号链接目录");
        if (!selected.isDirectory()) throw new IOException("所选目录已不存在，请重新选择");
        for (File current = selected; current != null; current = current.getParentFile()) {
            if (new File(current, ".nomedia").exists())
                throw new IOException("此目录或其上级包含 .nomedia，系统会忽略其中的媒体文件");
            if (current.equals(root)) break;
            if (current.getName().startsWith(".")
                || (current.getParentFile().equals(root) && current.getName().equals("Android")))
                throw new IOException("不支持扫描隐藏目录或 Android 应用私有目录");
        }
    }
}
