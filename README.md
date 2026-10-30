# Pulse

[![Release](https://img.shields.io/github/v/release/HoldEvan/pulse)](https://github.com/HoldEvan/pulse/releases)
[![Platform](https://img.shields.io/badge/platform-Android-green)](https://developer.android.com)
[![MinSdk](https://img.shields.io/badge/minSdk-26-blue)](https://developer.android.com/about/versions/oreo)
[![Language](https://img.shields.io/badge/language-Kotlin-orange)](https://kotlinlang.org)

Pulse 是一款 Android 原生 API 调试工具，覆盖 HTTP / SSE / WebSocket / MQTT 全协议调试，内置接口集合管理、环境变量、前后置脚本、Postman / HAR / OpenAPI 导入、接口监控与 WebDAV 备份，界面基于 Jetpack Compose 构建，提供 Miuix 与 Material 双皮肤。

## 功能

### 协议调试
- **HTTP**：完整请求编辑（Query 参数、请求头、请求体 JSON / RAW / form-urlencoded / form-data），鉴权支持 Bearer、Basic、API Key；支持二进制文件流式上传、逐请求跟随重定向与跳过 TLS 校验
- **响应正文**：格式化查看、大响应体分页渲染，HTML 默认展示源码并可跳转全屏 WebView 预览，媒体预览入口由响应头 Content-Type 决定，一键导出与分享完整响应
- **响应断言**：断言类型覆盖包含 / 不包含 / JSONPath / 状态码 / 响应头 / 耗时，JSONPath 支持等于 / 包含 / 数值比较 / 类型校验等运算符；HTTP 发送后自动校验并汇总 PASS / FAIL，WebSocket 与 MQTT 也可对每条收到的消息实时断言并累计通过率，配置随快照保存
- **Cookie / 会话**：自动跟随 `Set-Cookie` 并在后续请求注入，按域名分组查看、单条删除、按域名清除，数据按项目隔离
- **响应时间线**：DNS / TCP / TLS / 首字节等待 / 内容下载分段水瀑图
- **SSE**：支持自定义请求方法、参数与请求体，实时事件流展示；断线自动重连，可用 `Last-Event-ID` 续传，支持指数退避、重连抖动、最大重试次数、空闲超时与服务端 `retry:` 指令解析
- **WebSocket**：连接、收发文本 / 二进制帧与事件日志，自定义握手头与子协议，命名消息模板一键填充，消息持久化与可配置指数退避自动重连
- **MQTT**：连接 Broker、订阅/发布（含 retain 标记与批量订阅），订阅持久化与可折叠 Topic 树
- **TLS 证书管理**：四种协议共用自定义 CA 证书与客户端证书（mTLS）PEM 导入，支持系统信任库叠加而非替换，保留逐请求跳过证书校验开关
- **消息视图**：WebSocket 与 MQTT 的文本消息可解析 JSON 时，一键在树形与原文视图间切换，超长消息保留原文避免卡顿
- **弱网模拟**：5G / 4G / 3G / 2G 档位与强制超时，按上下行速率与时延采样
- **代理**：全局 HTTP / SOCKS5 代理（支持认证），每次连接前本地化提示当前代理状态

### 请求管理
- **集合**：多级文件夹 + 面包屑逐层下钻，接口重命名、移动、排序、原地复制，删除接口与递归删除文件夹均有二次确认；支持关键字跨文件夹搜索、curl 命令导入、文件夹级变量（子文件夹继承）以及导出为 Postman / OpenAPI 文档
- **集合运行器**：顺序运行一组请求，前序响应提取变量传给后序，配合脚本断言完成接口链路测试
- **历史**：完整请求/响应历史，详情页复现请求并回看当时的断言结论，支持收藏与置顶
- **响应对比**：任选两条历史响应做行级 diff，JSON / XML 先格式化再比较
- **环境变量**：多环境定义，`{{variable}}` 占位符全字段解析，缺失变量连接前预警
- **导入**：OpenAPI 3 / Swagger 2、Postman Collection、HAR（JSON / YAML，支持 URL 拉取与粘贴），分组预览后批量入库
- **导出**：cURL（Bash / PowerShell）、HTTP 原文、Python requests、JS fetch

### 自动化
- **前置/后置脚本**：Rhino JavaScript 引擎，`pm` API 支持变量读写、断言与控制台输出，原生 `console` 输出日志；脚本编辑器基于 sora-editor + TextMate 高亮，提供 `pm.*` / `console.*` / 内置对象成员补全、脚本内自定义变量补全、一键格式化、行号、双指缩放与底部符号栏，并内置语法文档
- **接口监控**：定时探测目标接口，支持 IP 连通、TCP 端口、HTTP 三类探测，HTTP 可配置方法、头、请求体与鉴权；响应断言支持包含 / 不包含 / JSONPath；通过与否全量记录，可按状态与检查类型筛选；延迟趋势曲线支持拖动查看样本详情；分组与批量开关；异常时发送系统通知，并可通过 Webhook 推送到企业微信、钉钉、飞书、Slack 或自定义端点
- **桌面小组件**：展示最近拨测状态，支持手动刷新与逐目标立即拨测；长按图标可直达常用工具

### 工具箱
- **JSON**：树形查看器（可折叠）、JSONPath 查询、格式化、JSON Schema 校验、YAML / JSON 互转
- **编解码**：Base64、URL、Unicode 编解码与进制转换、JWT 解析、Hash（MD5 / SHA 系列 / HMAC）
- **生成与校验**：UUID、二维码生成与识别、URL 解析、正则测试
- **时间**：时间戳互转（本地 / UTC 双时区）、时间格式化、cron 表达式解析与生成（Linux / Spring / Quartz 三种方言）
- **局域网扫描**：探测当前网段存活主机（ICMP + 常用端口），显示 IP、主机名、延迟与探测方式，可一键加入拨测

### 其他
- **双皮肤**：Miuix（液态玻璃底栏、磨砂二级栏）与 Material 3 两套皮肤，共享设计令牌，一键切换
- **主题**：深色 / 浅色 / 跟随系统，动态取色（Monet）、纯黑模式、自定义主题色与配色风格
- **WebDAV 备份**：手动与定时备份，数据上云随时恢复；支持本地备份导入导出
- **安全与诊断**：敏感字段本地加密存储，一键导出脱敏诊断包
- **多语言**：简体中文、繁体中文、English

## 下载

前往 [Releases](https://github.com/HoldEvan/pulse/releases) 下载最新 APK（如 `Pulse-v0.2.3-release.apk`），Android 8.0（API 26）及以上可安装。

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
| 语言 | Kotlin 2.4.20 |
| UI | Jetpack Compose（BOM 2026.09.00）+ Miuix 0.9.4 |
| 代码编辑器 | sora-editor 0.24.7 + TextMate 语法高亮 |
| 网络 | OkHttp、Ktor 3.1.3（SSE / WebSocket）、Paho MQTT 1.2.5 |
| 存储 | Room 2.8.5（SQLite）+ DataStore Preferences |
| 脚本 | Mozilla Rhino 1.7.15 |
| 序列化 / 规范解析 | kotlinx.serialization 1.9.0、kaml（YAML） |
| 协程 | kotlinx.coroutines 1.10.2 |
| 最低支持 | Android 8.0（API 26），compileSdk 37 / targetSdk 36 |

## 项目结构

```
app/src/main/java/com/evan/pulse/
├── data/          # Room 持久化、设置、备份、加密、诊断、Webhook、监控调度
├── domain/        # 网络引擎（HTTP/SSE/WS/MQTT）、脚本执行、规范导入、导出、diff、工具算法
├── di/            # ServiceLocator 依赖注入
├── widget/        # 桌面小组件
└── ui/            # Compose 页面：http/sse/ws/mqtt/collection/history/
                     monitor/env/tools/settings/design/glass 等
```
