# 手机热点模式实机记录

日期：2026-10-10。接收端：SM-C5000，Android API 26。测试 app：1.1-api17-hotspot-test，minSdk / targetSdk 均为 17。用户确认 iPhone 型号为 iPhone 17，iOS 版本为 26.2。

## 本轮观察

- 接收端手动加入 iPhone 个人热点，当前模式为 EXISTING_WIFI，接口为 wlan0，IPv4 为 172.20.10.2。
- 接收端 ping 热点网关 172.20.10.1，3 次请求全部收到响应。
- AirPlay 服务已同时绑定 wlan0 的 IPv6 链路本地地址和 IPv4，端口为 7000。
- 蓝牙 RFCOMM / iAP2 建立成功；iPhone 接受设备识别并返回 AuthenticationSucceeded。
- iPhone 请求 Wi-Fi 配置，并报告无线 CarPlay 可用。
- 接收端发送 CarPlayStartSession，包含热点网络信息、接收端 IPv4 / IPv6 和 AirPlay 端口。
- 两轮已读取的连接日志中，发送启动请求之后均未记录 AirPlay TCP 连接接入或 SETUP，会话未建立。
- 用户观察：没有弹出“使用 CarPlay”提示，个人热点没有断开；此前可能已使用过 CarPlay 配对。
- 用户确认正向对照：同一台 iPhone 使用局域网 Wi-Fi 模式可以连接 CarPlay。
- 1.2 IPv4 实验版：设备核实 versionCode=3、versionName=1.2-api17-hotspot-ipv4-test。当前进程日志确认 addressPolicy=ipv4-only，AirPlay 仅绑定 172.20.10.2:7000；认证成功并发送启动请求后仍未记录 AirPlay 连接，用户报告仍停留在等待会话。

## 当前判断

失败阶段位于蓝牙认证之后、AirPlay 会话建立之前。当前设备组合与实现尚未验证成功。现有实现会把 iPhone 自身热点作为待加入的目标网络通告给 iPhone，可能影响 iPhone 在热点角色下启动 CarPlay；这只是候选原因，尚无 iPhone 端日志或网络抓包证明。

IPv4 网关可达只能证明本次 ICMP 通信可用，不能证明 iPhone 的 CarPlay 进程已尝试连接接收端 TCP 7000。服务绑定成功也不能证明 iPhone 一侧的接口选择或策略允许连接。

## 待补充与下一步

1. 确认 iPhone 设置中的 CarPlay 车辆记录是否已包含本接收端。
2. 局域网 Wi-Fi 模式正向对照已成功，故优先排查手机热点链路与网络角色的差异。
3. 1.2 版 IPv4 单变量实验未解决问题，不支持将 IPv6 优先选择视为本轮失败的充分原因。下一步需要 iPhone 端 CarPlay / Wi-Fi 诊断或 TCP 抓包，区分网络角色、iPhone 连接策略和协议通告问题。
4. 当前不应仅延长等待时间或把超时视为“所有 iPhone 个人热点都不支持 CarPlay”的证据。
# HTTP 联通性补充（1.3）

- 已构建并安装 versionCode=4、versionName=1.3-api17-hotspot-http-test，minSdk/targetSdk 仍为 17。
- 设备日志确认 HTTP 监听 `172.20.10.2:8080`；通过安卓本机 wlan0 地址请求得到 HTTP 200 和中文页面。
- ADB 端口转发连接的是设备 loopback，因服务仅绑定 wlan0，该路径无法验证此服务；临时转发已移除。
- iPhone Safari 实际访问结果待用户反馈。本机 HTTP 200 不作为 iPhone 到安卓可达的证据。

