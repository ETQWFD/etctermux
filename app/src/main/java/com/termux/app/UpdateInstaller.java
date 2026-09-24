package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * etctermux: 检查更新——检测到新版本时弹出窗口，下载 APK 后调用安卓自带安装程序安装。
 */
public class UpdateInstaller {

    private static final String TAG = "EtcUpdate";
    private static final String RELEASES_URL = "https://api.github.com/repos/etqwfd/etctermux/releases/latest";
    private static final String CURRENT_VERSION = "2.0.0";
    private static final String PREFS = "etctermux_update";
    private static final String KEY_LAST_CHECK = "last_check_ms";
    private static final long CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000; // 每 6 小时检查一次

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 应用启动时静默检测（有频率限制）；返回 true 表示已发起检查。 */
    public static void checkOnLaunch(Context context) {
        long last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK, 0);
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return;
        check(context, false);
    }

    /** 菜单/命令触发：立即检测并弹窗。 */
    public static void checkNow(Context context) {
        check(context, true);
    }

    private static void check(final Context context, final boolean force) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
        EXECUTOR.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent", "etctermux");
                int code = conn.getResponseCode();
                if (code != 200) { log("check failed: http " + code); return; }
                String json = new String(readAll(conn.getInputStream()), "UTF-8");
                JSONObject root = new JSONObject(json);
                final String tag = root.optString("tag_name", "").replace("v", "");
                final String body = root.optString("body", "");
                // 版本比较（简单数字比较，取第一段数字）
                int newVer = parseVersion(tag);
                int curVer = parseVersion(CURRENT_VERSION);
                if (newVer <= curVer) { log("no update: " + tag); return; }

                // 选择匹配 ABI 的 APK 资产
                JSONArray assets = root.optJSONArray("assets");
                final String abi = pickAbi();
                String downloadUrl = null;
                String assetName = null;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        String name = a != null ? a.optString("name", "") : "";
                        if (name.contains(abi) && name.endsWith(".apk")) {
                            downloadUrl = a.optString("browser_download_url", null);
                            assetName = name;
                            break;
                        }
                        if (name.contains("universal") && name.endsWith(".apk")) {
                            downloadUrl = a.optString("browser_download_url", null);
                            assetName = name;
                        }
                    }
                }
                final String dlUrl = downloadUrl;
                final String dlName = assetName;

                MAIN.post(() -> {
                    AlertDialog.Builder b = new AlertDialog.Builder(context)
                        .setTitle("发现新版本 v" + tag)
                        .setMessage("当前版本: v" + CURRENT_VERSION + "\n最新版本: v" + tag +
                            (body != null && !body.isEmpty() ? "\n\n更新说明:\n" + body : "") +
                            "\n\n下载安装包后将调用系统安装程序安装。")
                        .setPositiveButton("立即更新", (d, w) -> {
                            if (dlUrl == null) {
                                new AlertDialog.Builder(context).setTitle("下载失败")
                                    .setMessage("未找到匹配本机(" + abi + ")的安装包，请到官网 https://etc.tw.kg 下载")
                                    .setPositiveButton("打开官网", (d2, w2) -> openUrl(context, "https://etc.tw.kg"))
                                    .show();
                                return;
                            }
                            downloadAndInstall(context, dlUrl, dlName, tag);
                        })
                        .setNegativeButton("以后再说", null);
                    if (!force) b.setCancelable(true);
                    b.show();
                });
            } catch (Exception e) {
                log("check exception: " + e.getMessage());
            }
        });
    }

    private static void downloadAndInstall(final Context context, final String url, final String name, final String tag) {
        final ProgressDialog pd = new ProgressDialog(context);
        pd.setTitle("正在下载 v" + tag);
        pd.setMessage("请稍候…");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();
        EXECUTOR.execute(() -> {
            final File apk = new File(context.getCacheDir(), "etctermux-update.apk");
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(120000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "etctermux");
                long total = conn.getContentLengthLong();
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(apk);
                byte[] buf = new byte[8192];
                long done = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        final int pct = (int) (done * 100 / total);
                        MAIN.post(() -> pd.setProgress(pct));
                    }
                }
                out.flush();
                out.close();
                in.close();
                MAIN.post(() -> {
                    pd.dismiss();
                    installApk(context, apk);
                });
            } catch (Exception e) {
                log("download failed: " + e.getMessage());
                MAIN.post(() -> {
                    pd.dismiss();
                    new AlertDialog.Builder(context).setTitle("下载失败")
                        .setMessage(e.getMessage() + "\n请稍后重试或到官网 https://etc.tw.kg 下载")
                        .setPositiveButton("确定", null).show();
                });
            }
        });
    }

    /** 调用安卓自带安装程序。 */
    private static void installApk(Context context, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(context, "com.termux.fileProvider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            log("install intent failed: " + e.getMessage());
            new AlertDialog.Builder(context).setTitle("安装失败")
                .setMessage("无法调用系统安装程序: " + e.getMessage())
                .setPositiveButton("确定", null).show();
        }
    }

    private static String pickAbi() {
        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if (abi.contains("arm64")) return "arm64-v8a";
                if (abi.contains("x86_64")) return "x86_64";
                if (abi.contains("armeabi")) return "armeabi-v7a";
                if (abi.contains("x86")) return "x86";
            }
        }
        return "universal";
    }

    private static int parseVersion(String v) {
        try {
            String s = v.replaceAll("[^0-9]", "");
            if (s.isEmpty()) return 0;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static void openUrl(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            log("open url failed: " + e.getMessage());
        }
    }

    private static void log(String msg) {
        Log.i(TAG, msg);
    }
}
