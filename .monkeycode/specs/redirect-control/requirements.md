# Requirements Document

## Introduction

HTTP 请求目前统一由 OkHttp 自动跟随重定向，无法关闭，也无法限制跳数；调试 301/302 链路时看不到中间响应与 Location 头。需要按请求提供「跟随/不跟随」与最大跳数。

## Glossary

- **System**: Pulse HTTP 请求页
- **跳数（Hop）**: 一次 3xx 跳转；首跳不计入
- **同源（Same Origin）**: 协议、主机、端口完全一致

## Requirements

### Requirement 1: 跟随开关

**User Story:** AS 开发者, I want 关闭自动跟随, so that 我能看到 3xx 响应与 Location。

#### Acceptance Criteria

1. WHEN 用户关闭「跟随重定向」，系统 SHALL 把第一个 3xx 响应原样返回
2. WHEN 跟随关闭，系统 SHALL 隐藏最大跳数输入框
3. WHEN 用户重新打开跟随，系统 SHALL 恢复使用上一次的跳数设置

### Requirement 2: 跳数限制

**User Story:** AS 开发者, I want 限制最多跳几次, so that 循环重定向不会无限请求。

#### Acceptance Criteria

1. WHEN 用户输入最大跳数，系统 SHALL 只接受 0–20 的整数
2. WHEN 跳数达到上限且响应仍是 3xx，系统 SHALL 返回该 3xx 响应
3. WHEN 跳数为 0，系统 SHALL 等同关闭跟随
4. WHEN 用户未填写跳数，系统 SHALL 按 10 跳处理

### Requirement 3: 跳转语义

**User Story:** AS 开发者, I want 跳转行为与浏览器一致, so that 结果可预期。

#### Acceptance Criteria

1. WHEN 收到 303，系统 SHALL 改为 GET 并丢弃请求体
2. WHEN 收到 301/302 且原方法是 POST，系统 SHALL 改为 GET 并丢弃请求体
3. WHEN 收到 301/302 且原方法不是 POST，系统 SHALL 保持方法与请求体
4. WHEN 收到 307/308，系统 SHALL 保持方法与请求体
5. WHEN 跳转到不同源，系统 SHALL 丢弃 Authorization 头
6. WHEN Location 为空、无法解析或不是 http(s)，系统 SHALL 停止跟随并返回当前响应

### Requirement 4: 全链路一致

**User Story:** AS 用户, I want 保存与运行时行为一致, so that 集合里的请求结果可复现。

#### Acceptance Criteria

1. WHEN 用户保存请求，系统 SHALL 写入跟随开关与跳数
2. WHEN 从集合加载，系统 SHALL 恢复两项设置
3. WHEN 集合运行器执行，系统 SHALL 使用快照里的设置
4. WHEN 读取旧快照，系统 SHALL 默认跟随、最多 10 跳
