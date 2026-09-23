#!/data/data/com.termux/files/usr/bin/bash
# ================= etctermux 首次自动配置 =================
# 功能: 切换国内镜像源 / 更新索引 / 安装基础安全测试工具 / 创建共享存储目录
export PATH="$PREFIX/bin:$PATH"
ETC="$PREFIX/etc/etctermux"
exec > >(tee "$HOME/.etctermux-setup.log")

echo
echo "[etctermux] ===== 首次配置开始 ====="

# [1/5] 配置镜像源（默认清华 TUNA）
echo "[1/5] 配置国内镜像源（默认: 清华 TUNA）"
bash "$ETC/mirror" --apply tuna >/dev/null 2>&1

# [2/5] 更新软件包索引
echo "[2/5] 更新软件包索引（apt update）..."
apt-get update -y >/dev/null 2>&1 && echo "      apt update 完成" || echo "      apt update 失败（可稍后输入 mirror 切换其他镜像重试）"

# [3/5] 安装内置工具链（git/python/nodejs/npm/java 等）与基础安全测试工具
echo "[3/5] 安装内置工具链与基础安全测试工具（逐个安装，失败自动跳过）..."
for p in git python python-pip nodejs npm openjdk-17 nmap hydra sqlmap nikto dnsutils netcat curl wget openssh tsu proot fakeroot traceroute whois unzip zip jq; do
    if apt-get install -y "$p" >/dev/null 2>&1; then
        echo "      ✓ $p"
    else
        echo "      ✗ $p (安装失败,已跳过)"
    fi
done

# [4/5] 创建共享存储目录 /storage/emulated/0/termux_kali_etc
echo "[4/5] 创建共享存储目录 /storage/emulated/0/termux_kali_etc"
termux-setup-storage >/dev/null 2>&1
sleep 1
if mkdir -p "$HOME/storage/shared/termux_kali_etc" 2>/dev/null; then
    echo "      ✓ 目录已创建: /storage/emulated/0/termux_kali_etc"
else
    echo "      ✗ 未授权存储权限，请在系统弹窗点击“允许”后输入:"
    echo "        termux-setup-storage && mkdir -p ~/storage/shared/termux_kali_etc"
fi

# [5/5] 完成
touch "$ETC/.setup-done"
echo "[5/5] 配置完成"
echo
echo "==============================================="
echo " 输入 kali    打开功能菜单（工具/镜像/更新/网站）"
echo " 输入 mirror  手动切换镜像源"
echo " 输入 sudo    模拟 root 环境（非真实 root）"
echo "==============================================="
