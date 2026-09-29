package local.mediarescan;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static volatile boolean running, cancel;
    private static volatile String message = "准备就绪";
    private static volatile int total, done;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler();
    private TextView status, permission;
    private ProgressBar progress;
    private Button scan, grant, stop;
    private final Runnable refresh = new Runnable() {
        public void run() { render(); ui.postDelayed(this, 400); }
    };
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(Color.rgb(25, 48, 47)); t.setPadding(0, dp(12), 0, dp(12));
        parent.addView(t); return t;
    }
    private Button button(LinearLayout parent, String title) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
        p.topMargin = dp(12); parent.addView(b, p); return b;
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1);
        root.setPadding(dp(24), dp(30), dp(24), dp(24)); root.setBackgroundColor(0xFFF3F8F5);
        scroll.addView(root); setContentView(scroll);
        getWindow().getInsetsController().setSystemBarsAppearance(
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        text(root, "媒体库刷新", 30);
        text(root, "同步已完成，照片却没出现？\n让系统重新发现手机里的媒体文件。", 16);
        permission = text(root, "", 14);
        grant = button(root, "允许访问文件");
        grant.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        });
        scan = button(root, "立即重新扫描"); scan.setOnClickListener(v -> startScan());
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(12)); pp.topMargin = dp(24);
        root.addView(progress, pp); status = text(root, "", 16);
        stop = button(root, "停止扫描"); stop.setOnClickListener(v -> { cancel = true; message = "正在停止…"; });
        text(root, "扫描范围：内部共享存储中的照片、视频和音频。\n\n自动跳过隐藏目录、含 .nomedia 的目录及 Android 应用私有目录。扫描过程中请保持此页面打开。\n\n完成后，重新进入微信或抖音的照片选择页面。", 14);
        if (!running) message = getPreferences(0).getString("last", "准备就绪");
    }
    @Override protected void onResume() { super.onResume(); ui.post(refresh); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(refresh); }
    private void render() {
        boolean allowed = Environment.isExternalStorageManager();
        permission.setText(allowed ? "✓ 文件访问权限已开启" : "首次使用，请允许访问文件以查找尚未入库的照片。");
        grant.setVisibility(allowed ? View.GONE : View.VISIBLE);
        scan.setEnabled(allowed && !running); stop.setVisibility(running ? View.VISIBLE : View.GONE);
        status.setText(message); progress.setIndeterminate(running && total == 0);
        progress.setMax(Math.max(total, 1)); progress.setProgress(done);
        if (running) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void startScan() {
        if (running || !Environment.isExternalStorageManager()) return;
        running = true; cancel = false; total = done = 0; message = "正在查找媒体文件…";
        getPreferences(0).edit().putString("last", "上次扫描中断，请重新扫描。").apply();
        final android.content.Context context = getApplicationContext();
        final android.content.SharedPreferences prefs = getPreferences(0);
        worker.execute(() -> {
            int ok = 0, failed = 0;
            int[] skipped = {0, 0};
            try {
                List<String> files = new ArrayList<>();
                collect(Environment.getExternalStorageDirectory(), files, skipped, true);
                total = files.size();
                for (String path : files) {
                    if (cancel) break;
                    CountDownLatch latch = new CountDownLatch(1);
                    final boolean[] indexed = {false};
                    try {
                        MediaScannerConnection.scanFile(context, new String[]{path}, null, (p, uri) -> {
                            indexed[0] = uri != null; latch.countDown();
                        });
                        if (!latch.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("系统扫描响应超时，请稍后重试");
                        }
                        if (indexed[0]) ok++; else failed++;
                    } catch (SecurityException e) { failed++; }
                    done++;
                    message = "正在扫描 " + done + " / " + total + "\n已确认入库 " + ok + " 项";
                }
                message = (cancel ? "扫描已停止" : "扫描完成") + "\n已处理 " + done + " / " + total
                        + " 项\n系统确认入库：" + ok + " 项\n未确认入库：" + failed
                        + " 项\n跳过 .nomedia 目录：" + skipped[0]
                        + " 个\n无法读取的目录：" + skipped[1] + " 个";
            } catch (Exception e) {
                message = "扫描未完成（已处理 " + done + " 项）\n" + e.getMessage();
                android.util.Log.e("MediaRescan", "Scan failed", e);
            } finally {
                message += "\n\n" + java.text.DateFormat.getDateTimeInstance().format(new Date());
                prefs.edit().putString("last", message).apply();
                android.util.Log.i("MediaRescan", message); running = false;
            }
        }); render();
    }
    private static void collect(File dir, List<String> paths, int[] skipped, boolean root) throws java.io.IOException {
        if (cancel) return;
        if (new File(dir, ".nomedia").exists()) { skipped[0]++; return; }
        File[] children = dir.listFiles();
        if (children == null) { skipped[1]++; return; }
        for (File file : children) {
            if (cancel) return;
            String name = file.getName();
            if (name.startsWith(".")) continue;
            if (file.isDirectory()) {
                if (root && name.equals("Android")) continue;
                if (!file.getCanonicalPath().equals(file.getAbsolutePath())) continue;
                collect(file, paths, skipped, false);
            } else if (file.isFile() && isMedia(name)) paths.add(file.getAbsolutePath());
        }
    }
    private static boolean isMedia(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null && (mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/"))
                || Arrays.asList("heic", "heif", "avif", "dng", "mkv", "opus").contains(ext);
    }
}
