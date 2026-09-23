#!/data/data/com.termux/files/usr/bin/bash
# ================= etctermux 首次自动配置 =================
# 功能: 修复 dpkg → 校验内置工具（已预装，开箱即用）→ 配置镜像源 → 在线补充 → 创建存储目录
export PATH="$PREFIX/bin:$PATH"
ETC="$PREFIX/etc/etctermux"
exec > >(tee "$HOME/.etctermux-setup.log")

echo
echo "[etctermux] ===== 首次配置开始 ====="

# [1/7] 修复 dpkg 中断状态（若先前安装被中断）
echo "[1/7] 检查并修复 dpkg 状态..."
if [ -d "$PREFIX/var/lib/dpkg" ]; then
    if dpkg --configure -a >/dev/null 2>&1; then
        echo "      dpkg 状态正常"
    else
        echo "      ✗ dpkg 修复失败（可能无网络，稍后可重试: dpkg --configure -a）"
    fi
else
    echo "      无需修复"
fi

# [2/7] 校验内置工具（已预装进安装包，开箱即用，无需联网安装）
echo "[2/7] 校验内置工具..."
FOUND=0; MISS=0
for cmd in nmap git python curl wget openssh unzip zip jq proot tsu sqlmap node npm; do
    if command -v "$cmd" >/dev/null 2>&1; then FOUND=$((FOUND+1)); else MISS=$((MISS+1)); fi
done
echo "      已就绪 $FOUND 个，缺失 $MISS 个（缺失项可用 kali 菜单安装）"

# [3/7] 生成 SSH 主机密钥与 CA 证书哈希（离线预装不执行 postinst 所需）
echo "[3/7] 补齐 SSH 主机密钥与 CA 证书..."
if [ -x "$PREFIX/bin/ssh-keygen" ] && [ ! -f "$PREFIX/etc/ssh/ssh_host_rsa_key" ]; then
    ssh-keygen -A >/dev/null 2>&1 && echo "      SSH 主机密钥已生成" || echo "      SSH 密钥生成失败"
fi
if [ -x "$PREFIX/bin/update-ca-certificates" ]; then
    update-ca-certificates --fresh >/dev/null 2>&1 && echo "      CA 证书已刷新" || echo "      CA 刷新失败"
fi

# [4/7] 配置镜像源（默认清华 TUNA，失败自动尝试其他镜像）
echo "[4/7] 配置国内镜像源（默认: 清华 TUNA）..."
bash "$ETC/mirror" --apply tuna >/dev/null 2>&1
if ! apt-get update -y >/dev/null 2>&1; then
    echo "      清华镜像不可用，尝试中科大..."
    bash "$ETC/mirror" --apply ustc >/dev/null 2>&1
    if ! apt-get update -y >/dev/null 2>&1; then
        echo "      尝试官方源..."
        bash "$ETC/mirror" --apply official >/dev/null 2>&1
        apt-get update -y >/dev/null 2>&1 && echo "      ✓ apt update 完成（官方源）" || echo "      ✗ apt update 失败（可稍后输入 mirror 重试）"
    else
        echo "      ✓ apt update 完成（中科大）"
    fi
else
    echo "      ✓ apt update 完成（清华）"
fi

# [5/7] 在线补充安装大型工具链（nodejs/npm/java；离线包未含，失败不影响核心工具）
echo "[5/7] 在线补充安装 nodejs/npm/java..."
for p in nodejs npm openjdk-17; do
    case "$p" in
        openjdk-17) [ -x "$PREFIX/bin/java" ] && { echo "      ✓ $p 已存在"; continue; } ;;
        *) command -v "$p" >/dev/null 2>&1 && { echo "      ✓ $p 已存在"; continue; } ;;
    esac
    if apt-get install -y --no-install-recommends "$p" >/dev/null 2>&1; then
        echo "      ✓ $p"
    else
        echo "      ✗ $p (安装失败,已跳过)"
    fi
done

# [6/7] 创建共享存储目录 /storage/emulated/0/termux_kali_etc
echo "[6/7] 创建共享存储目录 /storage/emulated/0/termux_kali_etc"
termux-setup-storage >/dev/null 2>&1
sleep 1
if mkdir -p "$HOME/storage/shared/termux_kali_etc" 2>/dev/null; then
    echo "      ✓ 目录已创建: /storage/emulated/0/termux_kali_etc"
else
    echo "      ✗ 未授权存储权限，请在系统弹窗点击“允许”后输入:"
    echo "        termux-setup-storage && mkdir -p ~/storage/shared/termux_kali_etc"
fi

# [7/7] 完成
touch "$ETC/.setup-done"
echo "[7/7] 配置完成"
echo
echo "==============================================="
echo " 输入 kali    打开功能菜单（工具/镜像/更新/网站）"
echo " 输入 mirror  手动切换镜像源"
echo " 输入 sudo    模拟 root 环境（非真实 root）"
echo " 标识: com.termux.etc | 官网: https://etc.tw.kg"
echo "==============================================="
