# CarPlay 手机热点模式测试 app

这是一个独立 APK，复用现有 CarPlay 流程和 `EXISTING_WIFI`（已连接 Wi-Fi / 同一局域网）模式。最低 API 和目标 API 均为 17，适用于 Android 4.2 及以上版本。需要先在 iPhone 开启个人热点，再由车机在 Android 系统设置中手动连接。本 app 不会开启热点，也不会更改车机的 Wi-Fi 连接。

## 实机测试步骤

1. 将本测试 app 安装并打开在车机上。
2. 在 iPhone 开启“个人热点”，然后在车机的 Wi-Fi 设置中手动连接到该热点。
3. 测试 app 默认使用“手机热点模式”。打开无线连接设置，填写 iPhone 热点的 SSID、密码和安全类型，然后保存。
4. 通过蓝牙配对或选择 iPhone，并启动无线 CarPlay 会话。
5. 记录 iPhone 是否弹出 CarPlay 提示、DiPlay 诊断中最后到达的连接阶段，以及是否成功进入投屏。

## 1.4：局域网对照入口

首页 → 连接设置 → 选择测试网络，可切换“手机热点测试”和“局域网 Wi-Fi 对照”。切换会断开当前会话，两种模式分别保存网络名称和密码，默认保留原手机热点配置。

局域网对照步骤：

1. 选择“局域网 Wi-Fi 对照”。
2. iPhone 关闭个人热点，iPhone 和安卓手动加入同一个路由器 Wi-Fi；可用“打开系统 Wi-Fi 设置”按钮。
3. 在测试 App 设置该路由器的 Wi-Fi 名称和密码，保存。
4. 保持蓝牙开启，点击连接，观察能否建立会话。请勿同时用 Safari 访问 7000，以免混淆自动会话日志。
5. 若切换网络后需测试 HTTP 8080，强行停止并重新打开 App，让 HTTP 服务绑定新的 Wi-Fi 地址。

这两个选项均使用 EXISTING_WIFI 和 IPv4 流程，改变的是测试网络及保存的凭证。局域网对照是否成功需实机反馈，不能用历史常规 App 成功替代本次结果。

本 app 会检查当前已连接的 Wi-Fi 接口，并将其信息交给现有 `ExistingWifiManager` 和 iAP2/CarPlay 会话流程。仅仅连上 iPhone 热点不代表 CarPlay 已成功。

1.2 版为 IPv4 单变量实验：仅使用当前 Wi-Fi 的 IPv4 来监听 AirPlay、发布 Bonjour 和通告 iAP2 地址，排除链路本地 IPv6 选择的影响。网络名称、密码、安全类型和蓝牙流程保持与 1.1 版一致。该实验尚待实机连接结果。

## 构建

测试 app 需要外部 CarPlay 认证资产。PowerShell 中在仓库根目录运行：

```powershell
$env:DIPLAY_AUTH_ASSETS_DIR = 'D:\个人项目集合\DiPlay-Legacy-Android-auth-v0.2.7'
.\gradlew :test:assembleHotspotTestDebug
```

外部目录必须包含 `offline-mfi/identity.pk8` 和 `offline-mfi/certificate.p7b`。构建会检查这两个文件，并拒绝把其他认证文件打入 APK；认证资产不应复制到仓库。APK 输出路径为 `test/build/outputs/apk/debug/test-debug.apk`。
# 1.3：HTTP 联通性测试

版本 `1.3-api17-hotspot-http-test`（versionCode 4）在测试 App 启动时，于 wlan0 的 IPv4 地址监听 TCP 8080。最低版本仍为 API 17。

1. 安卓先手动连接 iPhone 个人热点，再打开测试 App，保持前台。
2. iPhone 的 Safari 输入完整地址 `http://172.20.10.2:8080/`（以安卓当前 Wi-Fi 地址为准）。不需要先点击 CarPlay 连接。
3. 出现“访问成功”后，记录访问来源、请求序号。刷新后序号应增加，页面设置了禁止缓存。
4. 地址改变或启动时未连 Wi-Fi，请退出并重新启动 App。测试结束可强行停止 App 关闭监听。

成功说明 iPhone 到安卓 TCP 8080 可达，不能单独证明 TCP 7000、Bonjour 或 CarPlay 自动会话成功；失败也需要排除地址变化、App 退出、服务未监听等原因。日志标签为 `HotspotHttpTest`，不记录请求路径或凭证。

