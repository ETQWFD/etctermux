package com.termux.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * etctermux 正版签名校验（防盗版）。
 *
 * 应用启动时校验自身签名是否与官方公布的一致：
 *  - 校验通过: 正常使用
 *  - 校验失败（被重新签名/盗版）: 弹窗提示盗版 → 清除应用数据 → 跳转官网 → 退出
 *
 * 官方签名 SHA-256（与官网 https://etc.tw.kg 公布一致）:
 * a08602c171dd9e1e6cba264e69c11284b55777922819d9f3750b9dbd8b1485bb
 */
public final class LicenseCheck {

    private static final String LOG_TAG = "LicenseCheck";
    private static final String OFFICIAL_SITE = "https://etc.tw.kg";
    private static final String VERIFY_URL = OFFICIAL_SITE + "/verify.json";
    private static final String EXPECTED_SHA256 =
        "a08602c171dd9e1e6cba264e69c11284b55777922819d9f3750b9dbd8b1485bb";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private LicenseCheck() {}

    /** 异步执行正版校验；不阻塞启动，校验失败时由回调处理。 */
    public static void verifyAsync(final Context context) {
        EXECUTOR.execute(() -> {
            final boolean ok = verifySelfSignature(context);
            if (!ok) {
                new Handler(Looper.getMainLooper()).post(() -> showPiracyDialog(context));
            }
        });
    }

    /** 校验自身签名是否等于官方公布值。 */
    private static boolean verifySelfSignature(Context context) {
        try {
            String localHash = getSelfSignatureSha256(context);
            if (localHash == null) return false;
            if (localHash.equalsIgnoreCase(EXPECTED_SHA256)) return true;

            // 官方签名以官网 verify.json 为准（获取失败时回退到内置值）
            String remoteHash = fetchRemoteSha256();
            if (remoteHash != null && !remoteHash.isEmpty()) {
                return localHash.equalsIgnoreCase(remoteHash.trim());
            }
            return false;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Signature verification error", e);
            return false;
        }
    }

    private static String getSelfSignatureSha256(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        String pkg = context.getPackageName();
        Signature[] sigs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
            sigs = pi.signingInfo.getApkContentsSigners();
        } else {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            sigs = pi.signatures;
        }
        if (sigs == null || sigs.length == 0) return null;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(sigs[0].toByteArray());
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String fetchRemoteSha256() {
        try {
            java.net.URL url = new java.net.URL(VERIFY_URL);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            java.io.InputStream in = conn.getInputStream();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            String body = reader.readLine();
            reader.close();
            conn.disconnect();
            if (body == null) return null;
            int idx = body.indexOf("sha256");
            if (idx < 0) return null;
            int start = body.indexOf('"', idx + 7);
            int end = body.indexOf('"', start + 1);
            if (start < 0 || end <= start) return null;
            return body.substring(start + 1, end);
        } catch (Exception e) {
            Log.w(LOG_TAG, "Cannot fetch remote signature: " + e.getMessage());
            return null;
        }
    }

    private static void showPiracyDialog(final Context context) {
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle("检测到非正版")
            .setMessage("当前版本签名与官方不一致，可能是被篡改/二次打包的盗版版本。\n\n" +
                "请下载官网正版：\n" + OFFICIAL_SITE + "\n\n" +
                "联系方式: 2416444244@qq.com\n\n" +
                "本版本即将被销毁并跳转官网。")
            .setCancelable(false)
            .setPositiveButton("前往官网下载正版", (d, w) -> {
                wipeAppData(context);
                openOfficialSite(context);
                exitApp(context);
            })
            .setNegativeButton("退出", (d, w) -> {
                wipeAppData(context);
                exitApp(context);
            })
            .create();
        dialog.show();
    }

    /** 清除应用数据（bootstrap、home 等），实现“销毁盗版版本”。 */
    private static void wipeAppData(Context context) {
        try {
            File filesDir = context.getFilesDir();
            if (filesDir != null && filesDir.exists()) {
                deleteRecursive(filesDir);
            }
            // 兼容 Termux 旧数据路径
            File termuxDir = new File("/data/data/" + context.getPackageName() + "/files");
            if (termuxDir.exists()) deleteRecursive(termuxDir);
            Log.w(LOG_TAG, "Pirated build data wiped");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Wipe failed", e);
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void openOfficialSite(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_SITE));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Cannot open official site", e);
        }
    }

    private static void exitApp(Context context) {
        try {
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).finishAffinity();
            }
        } catch (Exception ignored) {}
        System.exit(0);
    }
}
