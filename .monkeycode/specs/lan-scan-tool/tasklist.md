# 需求实施计划

- [x] 1. 网段计算与扫描引擎（需求 1.3、2.1-2.4、3.1）
  - 实现 `lanHostAddresses` 网段展开（排除网络/广播、31/32 边界、512 截断）
  - 实现 `HostProbe` 抽象与 `DefaultHostProbe`（ICMP + 常用 TCP 端口回退）
  - 实现 `LanScanner.scan` 有界并发与取消语义
  - 为网段展开、排序与探测映射添加单元测试
- [x] 2. 扫描页面与工具箱入口（需求 1.1-1.2、2.2-2.4、3.2-3.4）
  - `LanScanViewModel`：网段检测、启停、进度与升序结果
  - `LanScanToolScreen`：网段卡片、按钮、进度、结果列表（IP / 主机名 / 延迟 / 探测方式 / 复制）
  - Nav 路由、PulseApp entry、Dashboard 入口与三语字符串
- [x] 3. 检查点 - 确保所有测试通过
  - 运行 Debug 单元测试并修复回归
  - 构建 Release APK 并核验版本、签名和工作区 APK
