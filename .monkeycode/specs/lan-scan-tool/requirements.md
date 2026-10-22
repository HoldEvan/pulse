# Requirements Document

## Introduction

Pulse 工具箱新增「局域网扫描」：检测设备所在网段，扫描网段内所有主机地址，列出在线主机的 IP、主机名、延迟与响应方式，帮助开发者在 HTTP / MQTT / WS 调试前找到局域网内的目标设备与服务。

## Glossary

- **网段（Network range）**：设备当前局域网 IPv4 地址与其前缀长度构成的地址块，如 `192.168.1.0/24`。
- **主机地址（Host address）**：网段内除网络地址与广播地址外的可探测 IPv4 地址。
- **存活主机（Alive host）**：对 ICMP 探测或任一常用 TCP 端口连接探测给出响应的主机。
- **探测（Probe）**：向一个主机地址发起的可达性测试（ICMP 或 TCP connect）。

## Requirements

### Requirement 1: 网段检测

**User Story:** AS a developer, I want the tool to detect the current LAN automatically, so that I scan the correct address range without manual input.

#### Acceptance Criteria

1. WHEN the user opens the LAN scan tool, Pulse SHALL display the detected local IPv4 address with its prefix length.
2. IF the device has no IPv4 LAN connection, Pulse SHALL display localized guidance and keep the scan action unavailable.
3. IF the detected address block contains more than 512 host addresses, Pulse SHALL probe the lowest 512 host addresses and display the probed count.

### Requirement 2: 主机扫描

**User Story:** AS a developer, I want to scan every address in the LAN, so that I can find devices and services on my network.

#### Acceptance Criteria

1. WHEN the user starts a scan, Pulse SHALL probe every host address of the detected range concurrently within a bounded parallelism of 64 probes.
2. WHEN a host answers a probe, Pulse SHALL add the host to the result list with IP, hostname when resolvable, round-trip latency, and the responding probe.
3. WHILE a scan is running, Pulse SHALL update the scanned-count progress and the result list continuously.
4. WHEN the user stops a scan, Pulse SHALL cancel all pending probes and keep the hosts already discovered.

### Requirement 3: 结果呈现

**User Story:** AS a developer, I want the discovered hosts presented in a stable, readable order, so that I can locate a device quickly.

#### Acceptance Criteria

1. Pulse SHALL order result rows by numeric IPv4 value ascending.
2. WHILE a result row represents an alive host, Pulse SHALL show the responding probe as `ICMP` or the TCP port number.
3. WHEN the user taps the copy action on a result row, Pulse SHALL copy the host IP to the clipboard.
4. WHEN the user starts a new scan, Pulse SHALL clear the previous result list.
