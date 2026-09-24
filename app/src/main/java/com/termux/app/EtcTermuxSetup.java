package com.termux.app;

import android.content.Context;
import android.system.Os;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * etctermux: 在 bootstrap 安装完成后写入自定义配置。
 *
 * 功能: Kali 风格 banner(motd) / shell 配置 / 镜像切换 / 工具菜单 / 更新检测 / sudo 模拟。
 * 仅使用 java.io 与 libcore Os，兼容 minSdk 21。
 */
public final class EtcTermuxSetup {

    private static final String LOG_TAG = "EtcTermuxSetup";
    private static final String ASSET_DIR = "etctermux";
    private static final String[] SCRIPTS = {"kali", "mirror", "etcupdate", "setup.sh", "sudo", "distro", "backup", "sniff"};
    private static final String[] STATIC_FILES = {"banner.txt", "bashrc", "version"};

    private EtcTermuxSetup() {}

    public static void apply(Context context) {
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        File etcDir = new File(prefix, "etc/etctermux");
        if (!etcDir.exists() && !etcDir.mkdirs()) {
            Log.w(LOG_TAG, "Cannot create " + etcDir);
            return;
        }

        try {
            // 1. 复制脚本与静态文件
            for (String name : SCRIPTS) copyAsset(context, new File(etcDir, name), name);
            for (String name : STATIC_FILES) copyAsset(context, new File(etcDir, name), name);

            // 2. 赋予脚本可执行权限
            for (String name : SCRIPTS) {
                File f = new File(etcDir, name);
                if (f.exists()) f.setExecutable(true, false);
            }

            // 3. 覆盖 motd 为自定义 banner
            copyAsset(context, new File(prefix, "etc/motd"), "banner.txt");

            // 4. 确保 bash.bashrc 存在并追加 etctermux 配置引用
            File bashrc = new File(prefix, "etc/bash.bashrc");
            String line = "\n# etctermux customization\n[ -r \"$PREFIX/etc/etctermux/bashrc\" ] && . \"$PREFIX/etc/etctermux/bashrc\"\n";
            if (!bashrc.exists()) {
                writeString(bashrc, line);
            } else {
                String content = readFile(bashrc);
                if (content == null || !content.contains("etctermux/bashrc")) {
                    appendString(bashrc, line);
                }
            }

            // 5. 建立 $PREFIX/bin 软链接
            linkBin(prefix, "kali");
            linkBin(prefix, "mirror");
            linkBin(prefix, "etcupdate");
            linkBin(prefix, "sudo");
            linkBin(prefix, "distro");
            linkBin(prefix, "backup");
            linkBin(prefix, "sniff");

            // 6. 写入烘烤引导版本标记（TermuxInstaller 据此判断是否需重建容器）
            writeString(new File(etcDir, "baked_version"), "1.3.0\n");

            Log.i(LOG_TAG, "etctermux customization applied to " + etcDir);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to apply etctermux customization", e);
        }
    }

    private static void copyAsset(Context context, File dest, String name) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        try (InputStream in = context.getAssets().open(ASSET_DIR + "/" + name);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private static void linkBin(String prefix, String name) {
        File target = new File(prefix, "bin/" + name);
        File source = new File(prefix, "etc/etctermux/" + name);
        try {
            if (!target.exists() && source.exists()) {
                Os.symlink(source.getAbsolutePath(), target.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Cannot link " + name + ": " + e.getMessage());
        }
    }

    private static String readFile(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n <= 0) break;
                off += n;
            }
            return new String(data, 0, off, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeString(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void appendString(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
