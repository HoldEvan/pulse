# Pulse

[![Release](https://img.shields.io/github/v/release/HoldEvan/pulse)](https://github.com/HoldEvan/pulse/releases)
[![Platform](https://img.shields.io/badge/platform-Android-green)](https://developer.android.com)
[![MinSdk](https://img.shields.io/badge/minSdk-26-blue)](https://developer.android.com/about/versions/oreo)
[![Language](https://img.shields.io/badge/language-Kotlin-orange)](https://kotlinlang.org)

Pulse 是一款 Android 原生 API 调试工具，覆盖 HTTP / SSE / WebSocket / MQTT 全协议调试，内置接口集合管理、环境变量、前置/后置脚本、OpenAPI 规范导入、接口监控与 WebDAV 备份，界面基于 Jetpack Compose 与 Miuix 构建。

## 功能

### 协议调试
- **HTTP**：完整请求编辑（Query 参数、请求头、请求体 JSON / RAW / form-urlencoded / form-data），鉴权支持 Bearer、Basic、API Key
- **SSE**：支持自定义请求方法、参数与请求体，实时事件流展示
- **WebSocket**：连接、收发消息与事件日志
- **MQTT**：连接 Broker、订阅/发布，事件日志
- **代理**：全局 HTTP / SOCKS5 代理（支持认证），每次连接前本地化提示当前代理状态

### 请求管理
- **集合**：多级文件夹 + 面包屑逐层下钻，接口重命名、移动、递归删除
- **历史**：完整请求/响应历史，详情页复现请求
- **环境变量**：多环境定义，`{{variable}}` 占位符全字段解析，缺失变量连接前预警
- **导入**：OpenAPI 3 / Swagger 2 规范（JSON / YAML，支持 URL 拉取与粘贴），分组预览后批量入库
- **导出**：cURL（Bash / PowerShell）、HTTP 原文、Python requests、JS fetch

### 自动化
- **前置/后置脚本**：Rhino JavaScript 引擎，`pm` API 支持变量读写、断言与控制台输出；内置带行号的脚本编辑器与语法文档
- **监控**：定时探测目标接口，异常时发送系统通知，历史记录可追溯

### 工具箱
- **JSON**：树形查看器（可折叠）、JSONPath 查询、格式化
- **时间戳**：互转与本地/UTC 双时区显示
- **Base64**：编解码

### 其他
- **WebDAV 备份**：数据上云，随时恢复
- **多语言**：简体中文、繁体中文、English
- **主题**：深色/浅色模式，Miuix 液态玻璃风格底栏

## 下载

前往 [Releases](https://github.com/HoldEvan/pulse/releases) 下载最新 APK（如 `Pulse-v0.1.34-release.apk`），Android 8.0（API 26）及以上可安装。

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
| 存储 | Room、DataStore |
| 脚本 | Mozilla Rhino 1.7.15 |
| 规范解析 | kotlinx.serialization JSON、kaml（YAML） |
| 最低支持 | Android 8.0（API 26），targetSdk 36 |

## 项目结构

```
app/src/main/java/com/evan/pulse/
├── data/          # 持久化（Room/DataStore）、设置、备份、监控调度
├── domain/        # 网络引擎（HTTP/SSE/WS/MQTT）、脚本执行、规范导入、导出
├── di/            # ServiceLocator 依赖注入
└── ui/            # Compose 页面：http/sse/ws/mqtt/collection/history/
                     monitor/env/tools/settings/glass 等
```
