package local.mediarescan;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import android.content.Intent;
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

public class MainActivity extends AppCompatActivity {
    private static volatile boolean running, cancel;
    private static volatile String message = "准备就绪";
    private static volatile int total, done;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler();
    private TextView status, permission, folderLabel, summary;
    private Button details;
    private final List<String> selectedPaths = new ArrayList<>();
    private final List<Button> removeButtons = new ArrayList<>();
    private LinearLayout folderList;
    private LinearProgressIndicator progress;
    private Button scan, grant, stop, choose;
    private final Runnable refresh = new Runnable() {
        public void run() { render(); ui.postDelayed(this, 400); }
    };
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private int color(int attr) { return MaterialColors.getColor(this, attr, "MediaRescan"); }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(color(com.google.android.material.R.attr.colorOnSurface)); t.setPadding(0, dp(6), 0, dp(6));
        parent.addView(t); return t;
    }
    private Button button(LinearLayout parent, String title) {
        Button b = new MaterialButton(this); b.setText(title); b.setAllCaps(false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
        p.topMargin = dp(12); parent.addView(b, p); return b;
    }
    private MaterialButton textButton(String title) {
        MaterialButton button = (MaterialButton)getLayoutInflater().inflate(R.layout.button_text, null);
        button.setText(title);
        return button;
    }
    @Override public void onCreate(Bundle b) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        setContentView(root);
        boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        getWindow().getInsetsController().setSystemBarsAppearance(
            dark ? 0 : android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(dp(20) + bars.left, dp(12) + bars.top, dp(20) + bars.right, dp(12) + bars.bottom); return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        root.addView(header);
        TextView heading = text(header, "媒体库刷新", 28);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton help = textButton("帮助"); header.addView(help);
        help.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle("使用说明")
            .setMessage("添加同步目录，再点击重新扫描。\n\n扫描包含子目录，重叠范围只处理一次。自动跳过隐藏目录、.nomedia 和 Android 私有目录。移除目标不会删除文件。\n\n扫描时请保持页面打开；完成后重新进入微信或抖音的选图页。\n\n版本 " + appVersion())
            .setPositiveButton("知道了", null).show());
        text(root, "让同步的照片出现在选图列表里", 14)
            .setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        permission = text(root, "", 14);
        grant = button(root, "允许访问文件");
        grant.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        });
        loadFolders();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content);
        folderLabel = text(content, "", 14);
        folderLabel.setPadding(0, dp(20), 0, dp(12));
        folderList = new LinearLayout(this); folderList.setOrientation(LinearLayout.VERTICAL); content.addView(folderList);
        rebuildFolders();
        choose = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        choose.setText("＋ 添加目录");
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, dp(56)); addParams.topMargin = dp(8);
        content.addView(choose, addParams);
        choose.setOnClickListener(v -> showFolders(Environment.getExternalStorageDirectory()));
        MaterialCardView result = new MaterialCardView(this); result.setRadius(dp(24)); result.setCardElevation(0);
        result.setStrokeWidth(0); result.setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainer));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(16); root.addView(result, rp);
        LinearLayout resultBody = new LinearLayout(this); resultBody.setOrientation(LinearLayout.VERTICAL);
        resultBody.setPadding(dp(18), dp(12), dp(18), dp(12)); result.addView(resultBody);
        LinearLayout resultHeader = new LinearLayout(this); resultHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        resultBody.addView(resultHeader);
        status = text(resultHeader, "", 18); status.setTypeface(null, android.graphics.Typeface.BOLD);
        status.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        details = textButton("详情"); resultHeader.addView(details);
        details.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle("扫描详情")
            .setMessage(message).setPositiveButton("关闭", null).show());
        summary = text(resultBody, "", 14);
        summary.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        progress = new LinearProgressIndicator(this); progress.setTrackThickness(dp(4)); progress.setTrackCornerRadius(dp(2));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(8)); pp.topMargin = dp(8);
        resultBody.addView(progress, pp);
        scan = button(root, "重新扫描"); scan.setOnClickListener(v -> startScan());
        stop = button(root, "停止扫描"); stop.setOnClickListener(v -> { cancel = true; message = "正在停止…"; });
        if (!running) message = getPreferences(0).getString("last", "准备就绪");
    }
    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { return ""; }
    }
    @Override protected void onResume() { super.onResume(); ui.post(refresh); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(refresh); }
    private void render() {
        boolean allowed = Environment.isExternalStorageManager();
        permission.setText("先允许文件访问，再添加需要扫描的目录。");
        permission.setVisibility(allowed ? View.GONE : View.VISIBLE);
        grant.setVisibility(allowed ? View.GONE : View.VISIBLE);
        folderLabel.setText(selectedPaths.isEmpty() ? "还没有目标目录" : "目标目录 · " + selectedPaths.size());
        choose.setEnabled(allowed && !running);
        for (Button remove : removeButtons) remove.setEnabled(!running);
        scan.setEnabled(allowed && !running && !selectedPaths.isEmpty()); scan.setVisibility(running ? View.GONE : View.VISIBLE);
        stop.setVisibility(running ? View.VISIBLE : View.GONE); stop.setEnabled(!cancel);
        String snapshot = message;
        String title = running && !cancel ? (total == 0 ? "正在查找文件" : "正在扫描") : snapshot.split("\n", 2)[0];
        String caption = running ? (total == 0 ? "正在查找文件，请保持页面打开" : done + " / " + total + " 个文件") : "包含子目录 · 自动合并重叠范围";
        for (String line : snapshot.split("\n")) if (!running && line.startsWith("已处理 ")) { caption = line.substring(4).replace("项", "").trim() + " 个文件"; break; }
        if (!status.getText().toString().equals(title)) status.setText(title);
        if (!summary.getText().toString().equals(caption)) summary.setText(caption);
        details.setVisibility(snapshot.contains("\n") ? View.VISIBLE : View.GONE);
        boolean indeterminate = running && total == 0;
        if (progress.isIndeterminate() != indeterminate) {
            progress.setVisibility(View.INVISIBLE); progress.setIndeterminate(indeterminate);
        }
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        progress.setMax(Math.max(total, 1)); progress.setProgress(done);
        if (running) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void loadFolders() {
        String saved = getPreferences(0).getString("folders", null);
        if (saved == null) {
            String previous = getPreferences(0).getString("folder", null);
            if (previous != null) selectedPaths.add(previous);
            saveFolders();
        } else {
            try {
                org.json.JSONArray array = new org.json.JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    String path = array.getString(i);
                    if (!path.isEmpty() && !selectedPaths.contains(path)) selectedPaths.add(path);
                }
            } catch (org.json.JSONException e) {
                Toast.makeText(this, "目录列表读取失败，请重新添加目录", Toast.LENGTH_LONG).show();
            }
        }
    }
    private void saveFolders() {
        getPreferences(0).edit().putString("folders", new org.json.JSONArray(selectedPaths).toString())
            .remove("folder").apply();
    }
    private void rebuildFolders() {
        folderList.removeAllViews(); removeButtons.clear();
        for (String path : selectedPaths) {
            MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(20)); card.setCardElevation(0);
            card.setStrokeWidth(0); card.setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.bottomMargin = dp(8); folderList.addView(card, cp);
            LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(10), dp(4), dp(10)); card.addView(row);
            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            TextView label = text(labels, path.equals(root) ? "内部存储" : new File(path).getName(), 17);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView location = text(labels, path.equals(root) ? "整个共享存储" : path.substring(Math.min(root.length() + 1, path.length())), 12);
            location.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
            Button remove = textButton("移除");
            remove.setContentDescription("移除目录 " + path);
            remove.setOnClickListener(v -> {
                if (running) return;
                selectedPaths.remove(path); saveFolders(); rebuildFolders(); render();
            });
            removeButtons.add(remove); row.addView(remove, new LinearLayout.LayoutParams(dp(76), dp(52)));
        }
    }
    private void showFolders(File directory) {
        if (running || !Environment.isExternalStorageManager()) return;
        File storage = Environment.getExternalStorageDirectory();
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("添加目标目录")
            .setMessage(directory.getAbsolutePath() + "\n正在读取子目录…")
            .setNegativeButton("取消", null).create();
        dialog.show();
        worker.execute(() -> {
            List<File> dirs = new ArrayList<>();
            String error = null;
            try {
                ScanScope.validate(storage, directory);
                File[] children = directory.listFiles();
                if (children == null) throw new java.io.IOException("无法读取此目录，请检查文件访问权限");
                for (File child : children) {
                    if (!child.isDirectory()) continue;
                    try { ScanScope.validate(storage, child); dirs.add(child); }
                    catch (java.io.IOException ignored) { }
                }
                dirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            } catch (java.io.IOException e) { error = e.getMessage(); }
            final String failure = error;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing() || !dialog.isShowing()) return;
                dialog.dismiss();
                if (failure != null) {
                    new MaterialAlertDialogBuilder(this).setMessage(failure).setPositiveButton("确定", null).show();
                    return;
                }
                boolean isRoot = directory.equals(storage);
                List<String> names = new ArrayList<>();
                if (!isRoot) names.add("↑ 返回上一级");
                for (File dir : dirs) names.add(dir.getName() + "/");
                new MaterialAlertDialogBuilder(this).setTitle(directory.getAbsolutePath())
                    .setItems(names.toArray(new String[0]), (d, index) -> {
                        if (!isRoot && index == 0) showFolders(directory.getParentFile());
                        else showFolders(dirs.get(index - (isRoot ? 0 : 1)));
                    })
                    .setPositiveButton(isRoot ? "添加整个内部存储" : "添加此目录", (d, which) -> {
                        String path = directory.getAbsolutePath();
                        if (selectedPaths.contains(path)) {
                            Toast.makeText(this, "此目录已在列表中", Toast.LENGTH_SHORT).show();
                        } else {
                            selectedPaths.add(path); saveFolders(); rebuildFolders(); render();
                        }
                    }).setNegativeButton("取消", null).show();
            });
        });
    }
    private void startScan() {
        if (running || !Environment.isExternalStorageManager() || selectedPaths.isEmpty()) return;
        final List<String> targets = new ArrayList<>(selectedPaths);
        running = true; cancel = false; total = done = 0; message = "正在查找媒体文件…";
        getPreferences(0).edit().putString("last", "上次扫描中断，请重新扫描。").apply();
        final android.content.Context context = getApplicationContext();
        final android.content.SharedPreferences prefs = getPreferences(0);
        worker.execute(() -> {
            int ok = 0, failed = 0;
            int[] skipped = {0, 0};
            List<String> unavailable = new ArrayList<>();
            try {
                List<String> files = new ArrayList<>();
                File storage = Environment.getExternalStorageDirectory();
                List<File> validTargets = new ArrayList<>();
                for (String path : targets) {
                    if (cancel) break;
                    File target = new File(path);
                    try { ScanScope.validate(storage, target); validTargets.add(target); }
                    catch (java.io.IOException | SecurityException e) { unavailable.add(path + "：" + e.getMessage()); }
                }
                List<File> roots = ScanScope.minimalRoots(validTargets);
                for (File target : roots) {
                    if (cancel) break;
                    message = "正在查找媒体文件…\n" + target.getAbsolutePath();
                    collect(target, files, skipped, target.equals(storage));
                }
                total = files.size();
                for (String path : files) {
                    if (cancel) break;
                    CountDownLatch latch = new CountDownLatch(1);
                    final boolean[] indexed = {false};
                    try {
                        MediaScannerConnection.scanFile(context, new String[]{path}, null, (p, uri) -> {
                            indexed[0] = uri != null; latch.countDown();
                        });
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                        while (!latch.await(100, TimeUnit.MILLISECONDS)) {
                            if (cancel) break;
                            if (System.nanoTime() >= deadline) throw new IllegalStateException("系统扫描响应超时，请稍后重试");
                        }
                        if (cancel) break;
                        if (indexed[0]) ok++; else failed++;
                    } catch (SecurityException e) { failed++; }
                    done++;
                    message = "正在扫描 " + done + " / " + total + "\n已确认入库 " + ok + " 项";
                }
                message = (cancel ? "扫描已停止" : unavailable.isEmpty() ? "扫描完成" : "扫描结束（有目录未扫描）")
                        + "\n目标目录：" + targets.size() + " 个，合并重叠后：" + roots.size() + " 个"
                        + "\n已处理 " + done + " / " + total
                        + " 项\n系统确认入库：" + ok + " 项\n未确认入库：" + failed
                        + " 项\n跳过 .nomedia 目录：" + skipped[0]
                        + " 个\n无法读取的目录：" + skipped[1] + " 个";
            } catch (Exception e) {
                message = "扫描未完成（已处理 " + done + " 项）\n" + e.getMessage();
                android.util.Log.e("MediaRescan", "Scan failed", e);
            } finally {
                if (!unavailable.isEmpty()) message += "\n未扫描目录：\n" + String.join("\n", unavailable);
                message += "\n目标列表：\n" + String.join("\n", targets) + "\n\n" + java.text.DateFormat.getDateTimeInstance().format(new Date());
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
