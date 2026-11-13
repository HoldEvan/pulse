# Pulse

[![Release](https://img.shields.io/github/v/release/HoldEvan/pulse)](https://github.com/HoldEvan/pulse/releases)
[![Platform](https://img.shields.io/badge/platform-Android-green)](https://developer.android.com)
[![MinSdk](https://img.shields.io/badge/minSdk-26-blue)](https://developer.android.com/about/versions/oreo)
[![Language](https://img.shields.io/badge/language-Kotlin-orange)](https://kotlinlang.org)

English | [简体中文](README.md)

Pulse is a native Android API debugging tool covering HTTP, SSE, WebSocket and MQTT. It ships with collection management, environment variables, pre-request and post-response scripts, Postman / HAR / OpenAPI import, endpoint monitoring and WebDAV backup. The UI is built with Jetpack Compose and offers two skins: Miuix and Material.

## Features

### Protocol debugging
- **HTTP**: up to 5 request tabs running in parallel, each holding its own request configuration, response and assertions. Full request editing (query parameters, headers, body as JSON / RAW / form-urlencoded / form-data), with Bearer, Basic and API Key auth. Request bodies can stream directly from a local file, connect and read timeouts are configured separately, redirects show the full hop-by-hop chain, and TLS verification can be skipped.
- **Response body**: formatted view, paginated rendering for large bodies, HTML shown as source by default with a full-screen WebView preview. Images are decoded from raw bytes and support pinch zoom and pan, so oversized base64 data URIs no longer stall the view. Media preview entry points are driven by the response Content-Type header, and the full response can be exported or shared in one tap.
- **Response assertions**: assertion types cover contains / not contains / JSONPath / status code / response headers / elapsed time, with JSONPath operators for equals, contains, numeric comparison and type checks. HTTP assertions run automatically after each send and summarize PASS / FAIL, while WebSocket and MQTT can assert on every received message and track a running pass rate. Assertion config travels with the saved snapshot.
- **Cookies and sessions**: `Set-Cookie` is followed automatically and replayed on later requests. Cookies are grouped by domain, can be deleted individually or cleared per domain, and are isolated per project.
- **Response timeline**: waterfall chart split into DNS / TCP / TLS / time to first byte / content download.
- **SSE**: custom method, parameters, body and auth, with up to 5 connections debugged in parallel tabs. Auto-reconnect with `Last-Event-ID` resumption, exponential backoff, reconnect jitter, max retries, idle timeout and parsing of the server `retry:` directive. The last 50 connection records can be reviewed, exported and migrated with backups.
- **WebSocket**: connect and exchange text / binary frames with an event log, custom handshake headers and subprotocols, named message templates for one-tap fill, real ping/pong heartbeats on an interval, scheduled resend of the current message, message persistence and configurable exponential-backoff reconnects.
- **MQTT**: connect to a broker with MQTT 5 / 3.1.1, Clean Start and session expiry, auto-reconnect and will messages. Subscribe and publish with retain, binary payloads and batch subscribes, unsubscribe at will, persisted subscriptions in a collapsible topic tree, and reviewable session history.
- **TLS certificates**: all four protocols share imported custom CA certificates and client certificates (mTLS) in PEM format. The system trust store is extended rather than replaced, and the per-request skip-verification switch is preserved.
- **Message view**: when a WebSocket or MQTT text message parses as JSON, switch between tree and raw views in one tap. Very long messages stay in raw view to keep the UI responsive.
- **Network conditioning**: 5G / 4G / 3G / 2G profiles and forced timeouts, sampled against uplink, downlink and latency targets.
- **Proxy**: global HTTP / SOCKS5 proxy with authentication, plus a localized hint of the active proxy before every connection.

### Request management
- **Collections**: multi-level folders with breadcrumb drill-down, request rename, move, reorder and in-place duplicate. Deleting a request or a folder recursively always asks for confirmation. Also keyword search across folders, curl import, folder-level variables inherited by subfolders, and export to Postman / OpenAPI documents. A request loaded from a collection asks for confirmation when saved again: rename it or move it to another collection, and it is updated in place instead of creating a duplicate.
- **Collection runner**: run a group of requests in order, extract variables from earlier responses for later ones, and use script assertions for end-to-end chain testing.
- **History**: full request / response history, reproduce a request from its detail page and review the assertion verdict from that time, with favorite and pin support.
- **Response diff**: pick any two historical responses for a line-level diff; JSON and XML are formatted before comparison.
- **Environment variables**: multiple environments, `{{variable}}` resolution across every field, and a pre-connect warning when a variable is missing.
- **Import**: OpenAPI 3 / Swagger 2, Postman Collection and HAR (JSON / YAML, from a URL or pasted text), with a grouped preview before batch import.
- **Export**: cURL (Bash / PowerShell), raw HTTP, Python requests and JS fetch.

### Automation
- **Pre-request and post-response scripts**: Rhino JavaScript engine with a `pm` API for variable access, assertions and console output, plus a native `console`. The script editor is built on sora-editor with TextMate highlighting, completion for `pm.*` / `console.*` / built-in object members and script-local variables, one-tap formatting, line numbers, pinch zoom, a bottom symbol bar and built-in syntax docs.
- **Endpoint monitoring**: scheduled probes for target endpoints, covering IP reachability, TCP port and HTTP checks. IP probes try ICMP first, then race the common ports, and a refused connection (RST) also counts as reachable; LAN targets bypass the proxy. HTTP checks support method, headers, body and auth, with contains / not contains / JSONPath assertions. Every result is recorded and filterable by status and check type, the latency trend chart can be dragged to inspect individual samples, targets can be grouped and toggled in bulk, and failures raise system notifications or Webhook pushes to WeCom, DingTalk, Feishu, Slack or a custom endpoint.
- **Home screen widget**: shows the latest probe status with manual refresh and per-target immediate probes; long-press the icon to jump to frequently used tools.

### Toolbox
- **JSON**: collapsible tree viewer, JSONPath queries, formatting, JSON Schema validation and YAML / JSON conversion.
- **Encoding and hashing**: Base64, URL and Unicode encoding / decoding, base conversion, JWT parsing, and MD5 / SHA family / HMAC hashes.
- **Generate and verify**: UUID, QR code generation and scanning, URL parsing and regex testing.
- **Time**: timestamp conversion in local and UTC time zones, time formatting, and cron expression parsing and generation for Linux / Spring / Quartz dialects.
- **LAN scan**: discover live hosts on the current subnet (ICMP plus common ports) with IP, hostname, latency and probe method, and add any of them to monitoring in one tap.

### Other
- **Two skins**: Miuix (liquid-glass bottom bar, frosted secondary bar) and Material 3 sharing one set of design tokens, switchable in one tap.
- **Theme**: dark / light / follow system, dynamic color (Monet), true black mode, and custom primary color and palette.
- **WebDAV backup**: manual and scheduled backups, restore from the cloud at any time, plus local backup import and export.
- **Security and diagnostics**: sensitive fields encrypted at rest, and a one-tap redacted diagnostic bundle export.
- **Localization**: Simplified Chinese, Traditional Chinese and English.

## Download

Get the latest APK from [Releases](https://github.com/HoldEvan/pulse/releases) (for example `Pulse-v0.2.2-release.apk`). Android 8.0 (API 26) and above.

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (configure signing under keystore/, or use the local debug key)
./gradlew assembleRelease

# Unit tests
./gradlew testDebugUnitTest
```

Android Studio (Ladybug or newer) with JDK 21+ is recommended.

## Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin 2.4.20 |
| UI | Jetpack Compose (BOM 2026.09.00) + Miuix 0.9.4 |
| Code editor | sora-editor 0.24.7 + TextMate syntax highlighting |
| Networking | OkHttp, Ktor 3.1.3 (SSE / WebSocket), HiveMQ MQTT Client 1.4.1 |
| Storage | Room 2.8.5 (SQLite) + DataStore Preferences |
| Scripting | Mozilla Rhino 1.7.15 |
| Serialization / spec parsing | kotlinx.serialization 1.9.0, kaml (YAML) |
| Coroutines | kotlinx.coroutines 1.10.2 |
| Minimum | Android 8.0 (API 26), compileSdk 37 / targetSdk 36 |

## Project layout

```
app/src/main/java/com/evan/pulse/
├── data/          # Room persistence, settings, backup, encryption, diagnostics, Webhook, monitoring scheduler
├── domain/        # Network engines (HTTP/SSE/WS/MQTT), script runtime, spec import, export, diff, tool algorithms
├── di/            # ServiceLocator dependency graph
├── widget/        # Home screen widget
└── ui/            # Compose screens: http/sse/ws/mqtt/collection/history/
                     monitor/env/tools/settings/design/glass and more
```
