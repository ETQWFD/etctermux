# etctermux — Kali 风格白帽安全终端

基于 [Termux](https://github.com/termux/termux-app) 深度定制的 Android 终端环境，集成了 Kali Linux 常用终端工具链，开箱即用。

开发者: **etc** | 版本: **v1.1.0** | 官网: [https://etc.tw.kg](https://etc.tw.kg) | 联系: 2416444244@qq.com | 仓库: [github.com/etqwfd/etctermux](https://github.com/etqwfd/etctermux)

## 特色功能

- **Kali 风格命令行**：Kali 式提示符 (PS1)、彩色 ASCII banner 启动提示语（可自行修改 `$PREFIX/etc/etctermux/banner.txt`）
- **左侧功能抽屉**：镜像源 / 工具菜单 / 检查更新 / 白帽网站 / sudo / 关于，一键直达；右侧 + 新建终端会话
- **内置镜像源切换** (`mirror`)：清华 TUNA / 中科大 USTC / 阿里云 / 北外 BFSU / 南京大学 NJU / 官方源
- **工具菜单** (`kali`)：信息收集、漏洞扫描、Web 渗透、密码攻击、无线安全、取证等分类一键安装（nmap、sqlmap、hydra、nikto、nuclei、metasploit 等），支持 `proot-distro install kali` 完整 Kali 环境
- **内置工具链**：首次启动自动安装 git、python、nodejs、npm、java(openjdk-17) 及核心安全测试工具
- **白帽学习/测试网站直达**：OWASP、HackTheBox、TryHackMe、HackerOne、Vulhub、DVWA 等一键打开
- **sudo / root 模拟** (`sudo`)：已 root 用真实 su；未 root 自动 proot/fakeroot 模拟（32 位/64 位/模拟器均可用）
- **版本更新检测** (`etcupdate`)：按设备 ABI 自动匹配下载最新版到 `/storage/emulated/0/termux_kali_etc/` 并打开安装器
- **正版签名校验**：启动时校验官方签名，非官方签名版本将提示盗版、销毁数据并跳转官网
- **共享存储目录**：首次启动自动创建 `/storage/emulated/0/termux_kali_etc/`
- 支持 **32 位/64 位** 手机与模拟器（armeabi-v7a / arm64-v8a / x86 / x86_64）

## 首次使用

安装并打开应用后，自动完成：签名校验 → 配置清华镜像 → 更新索引 → 安装内置工具链 → 创建存储目录。完成后输入 `kali` 打开功能菜单。

## 许可协议与免责声明

- 本软件为白帽安全测试与学习工具，仅限**授权环境**下使用
- 因使用本软件造成的任何损失，**etc 团队不负任何责任**
- 联系方式: 2416444244@qq.com
- 应用基于 Termux 二次开发，保留上游版权声明与许可

## 构建

```bash
# 需要 JDK 17 + Android SDK (platform 36, build-tools 36, NDK 29)
./gradlew downloadBootstraps
./gradlew :app:assembleRelease
```
