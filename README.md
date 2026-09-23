# etctermux — Kali 风格安全测试终端

基于 [Termux](https://github.com/termux/termux-app) 深度定制的 Android 终端环境。

开发者: **etc** | 应用名: **etctermux** | 仓库: [github.com/etqwfd/etctermux](https://github.com/etqwfd/etctermux)

## 特色功能

- **Kali 风格命令行**：定制 Kali 式提示符 (PS1)、彩色 ASCII banner 启动提示语（可自行修改 `$PREFIX/etc/etctermux/banner.txt`）
- **内置镜像源切换** (`mirror`)：清华 TUNA / 中科大 USTC / 阿里云 / 北外 BFSU / 南京大学 NJU / 官方源，一键切换
- **内置安全测试工具菜单** (`kali`)：信息收集、漏洞扫描、Web 渗透、密码攻击、无线安全、取证分析等分类一键安装（nmap、sqlmap、hydra、nikto、nuclei、metasploit 等），支持 `proot-distro install kali` 获得完整 Kali 工具集
- **白帽学习/测试网站直达**：OWASP、HackTheBox、TryHackMe、HackerOne、Vulhub、DVWA 等一键用浏览器打开
- **sudo / root 模拟** (`sudo`)：设备已 root 时使用真实 su；未 root 时自动用 proot/fakeroot 模拟 root 环境（模拟不等于真实 root）
- **版本更新检测** (`etcupdate`)：自动检测 GitHub 最新 Release，下载到 `/storage/emulated/0/termux_kali_etc/` 并打开安装器
- **共享存储目录**：首次启动自动创建 `/storage/emulated/0/termux_kali_etc/` 用于存放 APK 等文件
- 支持 **32 位/64 位** 手机与模拟器（armeabi-v7a / arm64-v8a / x86 / x86_64）

## 首次使用

安装并打开应用后，会自动完成：配置清华镜像 → 更新索引 → 安装基础测试工具 → 创建存储目录。完成后输入 `kali` 打开功能菜单。

## 构建

```bash
# 需要 JDK 17 + Android SDK (platform 36, build-tools 36, NDK 29)
./gradlew :app:assembleRelease
```

## 说明

- 应用基于 Termux 二次开发，保留上游版权声明与许可
- 内置工具仅限 **授权环境下** 的安全测试、渗透学习与漏洞赏金研究，请遵守当地法律法规
- 无 root 的手机无法获得真实 root 权限，`sudo` 仅提供环境模拟
- 包名保持 `com.termux` 以兼容 Termux 生态（bootstrap/软件包仓库）
