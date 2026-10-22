# Pulse

[![Release](https://img.shields.io/github/v/release/HoldEvan/pulse)](https://github.com/HoldEvan/pulse/releases)
[![Platform](https://img.shields.io/badge/platform-Android-green)](https://developer.android.com)
[![MinSdk](https://img.shields.io/badge/minSdk-26-blue)](https://developer.android.com/about/versions/oreo)
[![Language](https://img.shields.io/badge/language-Kotlin-orange)](https://kotlinlang.org)

Pulse 是一款 Android 原生 API 调试工具，覆盖 HTTP / SSE / WebSocket / MQTT 全协议调试，内置接口集合管理、环境变量、前后置脚本、Postman / HAR / OpenAPI 导入、接口监控与 WebDAV 备份，界面基于 Jetpack Compose 构建，提供 Miuix 与 Material 双皮肤。

## 功能

### 协议调试
- **HTTP**：完整请求编辑（Query 参数、请求头、请求体 JSON / RAW / form-urlencoded / form-data），鉴权支持 Bearer、Basic、API Key；支持二进制文件流式上传
- **Cookie / 会话**：自动跟随 `Set-Cookie` 并在后续请求注入，按域名分组查看、单条删除、按域名清除，数据按项目隔离
- **响应时间线**：DNS / TCP / TLS / 首字节等待 / 内容下载分段水瀑图
- **SSE**：支持自定义请求方法、参数与请求体，实时事件流展示
- **WebSocket**：连接、收发消息与事件日志，消息持久化与可配置指数退避自动重连
- **MQTT**：连接 Broker、订阅/发布，订阅持久化与可折叠 Topic 树
- **弱网模拟**：5G / 4G / 3G / 2G 档位与强制超时，按上下行速率与时延采样
- **代理**：全局 HTTP / SOCKS5 代理（支持认证），每次连接前本地化提示当前代理状态

### 请求管理
- **集合**：多级文件夹 + 面包屑逐层下钻，接口重命名、移动、递归删除
- **集合运行器**：顺序运行一组请求，前序响应提取变量传给后序，配合脚本断言完成接口链路测试
- **历史**：完整请求/响应历史，详情页复现请求，支持收藏与置顶
- **响应对比**：任选两条历史响应做行级 diff，JSON / XML 先格式化再比较
- **环境变量**：多环境定义，`{{variable}}` 占位符全字段解析，缺失变量连接前预警
- **导入**：OpenAPI 3 / Swagger 2、Postman Collection、HAR（JSON / YAML，支持 URL 拉取与粘贴），分组预览后批量入库
- **导出**：cURL（Bash / PowerShell）、HTTP 原文、Python requests、JS fetch

### 自动化
- **前置/后置脚本**：Rhino JavaScript 引擎，`pm` API 支持变量读写、断言与控制台输出；内置带行号的脚本编辑器与语法文档
- **接口监控**：定时探测目标接口，支持 IP 连通、TCP 端口、HTTP 三类探测；通过与否全量记录，可按状态与检查类型筛选；延迟趋势曲线支持拖动查看样本详情；异常时发送系统通知
- **桌面小组件**：展示最近拨测状态，支持手动刷新与逐目标立即拨测；长按图标可直达常用工具

### 工具箱
- **JSON**：树形查看器（可折叠）、JSONPath 查询、格式化
- **编解码**：Base64、URL、Unicode 编解码与进制转换、JWT 解析、Hash（MD5 / SHA 系列）
- **生成与校验**：UUID、正则测试
- **时间**：时间戳互转（本地 / UTC 双时区）、cron 表达式解析
- **局域网扫描**：探测当前网段存活主机（ICMP + 常用端口），显示 IP、主机名、延迟与探测方式，可一键加入拨测

### 其他
- **双皮肤**：Miuix（液态玻璃底栏、磨砂二级栏）与 Material 3 两套皮肤，共享设计令牌，一键切换
- **主题**：深色 / 浅色 / 跟随系统，动态取色（Monet）、纯黑模式、自定义主题色与配色风格
- **WebDAV 备份**：数据上云，随时恢复
- **多语言**：简体中文、繁体中文、English

## 下载

前往 [Releases](https://github.com/HoldEvan/pulse/releases) 下载最新 APK（如 `Pulse-v0.2.0-release.apk`），Android 8.0（API 26）及以上可安装。

## 构建

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（需在 keystore/ 下配置签名，或使用本地 debug 签名）
./gradlew assembleRelease

# 单元测试
./gradlew testDebugUnitTest
```

推荐使用 Android Studio（Ladybug 或更新版本）打开项目，JDK 17+。

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin 2.4 |
| UI | Jetpack Compose（BOM 2026.09.00）+ Miuix 0.9.4 |
| 网络 | OkHttp、Ktor 3.1（SSE / WebSocket）、Paho MQTT |
| 存储 | DataStore Preferences、JSON 快照（app_data.json） |
| 脚本 | Mozilla Rhino 1.7.15 |
| 规范解析 | kotlinx.serialization JSON、kaml（YAML） |
| 最低支持 | Android 8.0（API 26），targetSdk 36 |

## 项目结构

```
app/src/main/java/com/evan/pulse/
├── data/          # 持久化、设置、备份、监控调度
├── domain/        # 网络引擎（HTTP/SSE/WS/MQTT）、脚本执行、规范导入、导出、工具算法
├── di/            # ServiceLocator 依赖注入
├── widget/        # 桌面小组件
└── ui/            # Compose 页面：http/sse/ws/mqtt/collection/history/
                     monitor/env/tools/settings/design/glass 等
```
