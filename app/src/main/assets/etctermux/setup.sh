#!/data/data/com.termux/files/usr/bin/bash
# ================= etctermux 首次自动配置 =================
# 功能: 离线解压安装内置工具（无需联网）→ 切换国内镜像源 → 在线补充工具 → 创建存储目录
export PATH="$PREFIX/bin:$PATH"
ETC="$PREFIX/etc/etctermux"
OFFDIR="$ETC/offline"
exec > >(tee "$HOME/.etctermux-setup.log")

echo
echo "[etctermux] ===== 首次配置开始 ====="

# [1/6] 离线安装内置工具（无需联网，安装包内置）
echo "[1/6] 解压内置离线工具包并安装（无需联网）..."
if [ -f "$ETC/offline.tar.xz" ]; then
    mkdir -p "$OFFDIR"
    if tar -xJf "$ETC/offline.tar.xz" -C "$OFFDIR" 2>/dev/null; then
        NDEB=$(ls "$OFFDIR"/debs/*.deb 2>/dev/null | wc -l)
        echo "      解压完成: $NDEB 个安装包"
        # 临时启用本地离线源，屏蔽在线源
        cp "$PREFIX/etc/apt/sources.list" "$PREFIX/etc/apt/sources.list.etcbak" 2>/dev/null
        : > "$PREFIX/etc/apt/sources.list"
        printf 'deb [trusted=yes] file://%s/debs ./\n' "$OFFDIR" > "$PREFIX/etc/apt/sources.list.d/etctermux-offline.list"
        if apt-get update -y >/dev/null 2>&1; then
            for p in curl wget git python python-pip nmap netcat-openbsd dnsutils whois traceroute openssh ca-certificates gnupg unzip zip jq tsu proot proot-distro fakeroot dirb socat steghide binwalk exiftool crunch htop tmux; do
                if apt-get install -y --no-install-recommends "$p" >/dev/null 2>&1; then
                    echo "      ✓ $p"
                else
                    echo "      ✗ $p (离线安装失败,已跳过)"
                fi
            done
            # 离线 pip 安装 sqlmap（纯 Python 轮子已内置）
            if ls "$OFFDIR"/sqlmap-wheel/*.whl >/dev/null 2>&1; then
                if python -m pip install --no-index --find-links "$OFFDIR/sqlmap-wheel" --break-system-packages --quiet sqlmap >/dev/null 2>&1; then
                    echo "      ✓ pip: sqlmap (离线)"
                else
                    echo "      ✗ pip: sqlmap (离线安装失败)"
                fi
            fi
        else
            echo "      ✗ 本地离线源初始化失败"
        fi
        # 恢复在线源
        rm -f "$PREFIX/etc/apt/sources.list.d/etctermux-offline.list"
        mv -f "$PREFIX/etc/apt/sources.list.etcbak" "$PREFIX/etc/apt/sources.list" 2>/dev/null
        # 清理离线包释放空间
        rm -rf "$OFFDIR"
        rm -f "$ETC/offline.tar.xz"
        echo "      离线安装完成，已清理临时文件"
    else
        echo "      ✗ 离线包解压失败"
    fi
else
    echo "      未检测到内置离线包（使用在线安装）"
fi

# [2/6] 配置镜像源（默认清华 TUNA）
echo "[2/6] 配置国内镜像源（默认: 清华 TUNA）"
bash "$ETC/mirror" --apply tuna >/dev/null 2>&1

# [3/6] 更新软件包索引
echo "[3/6] 更新软件包索引（apt update）..."
apt-get update -y >/dev/null 2>&1 && echo "      apt update 完成" || echo "      apt update 失败（可稍后输入 mirror 切换其他镜像重试）"

# [4/6] 在线补充安装工具链（nodejs/npm/java，离线包未含）
echo "[4/6] 在线补充安装 nodejs/npm/java..."
for p in nodejs npm openjdk-17; do
    if apt-get install -y --no-install-recommends "$p" >/dev/null 2>&1; then
        echo "      ✓ $p"
    else
        echo "      ✗ $p (在线安装失败,已跳过，可稍后输入 kali 重试)"
    fi
done

# [5/6] 创建共享存储目录 /storage/emulated/0/termux_kali_etc
echo "[5/6] 创建共享存储目录 /storage/emulated/0/termux_kali_etc"
termux-setup-storage >/dev/null 2>&1
sleep 1
if mkdir -p "$HOME/storage/shared/termux_kali_etc" 2>/dev/null; then
    echo "      ✓ 目录已创建: /storage/emulated/0/termux_kali_etc"
else
    echo "      ✗ 未授权存储权限，请在系统弹窗点击“允许”后输入:"
    echo "        termux-setup-storage && mkdir -p ~/storage/shared/termux_kali_etc"
fi

# [6/6] 完成
touch "$ETC/.setup-done"
echo "[6/6] 配置完成"
echo
echo "==============================================="
echo " 输入 kali    打开功能菜单（工具/镜像/更新/网站）"
echo " 输入 mirror  手动切换镜像源"
echo " 输入 sudo    模拟 root 环境（非真实 root）"
echo "==============================================="
