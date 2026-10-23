# Requirements Document

## Introduction

cron 工具从「仅解析标准五段表达式」扩展为「解析 + 生成」双向工具，并支持 Linux、Spring、Quartz 三种方言。

## Glossary

- **System**: cron 工具（Cron Tool）
- **方言（Dialect）**: cron 的语法变体，段数、取值范围与日/周语义不同
- **解析模式（Parse Mode）**: 输入表达式，输出未来十次执行时间
- **生成模式（Generate Mode）**: 按方言逐字段填写，输出表达式文本
- **模板（Preset）**: 生成模式下的常用计划，如每天九点

## Requirements

### Requirement 1: 三种方言解析

**User Story:** AS 后端开发者, I want 选择自己项目使用的 cron 方言, so that 表达式能被正确解释而不必换算字段。

#### Acceptance Criteria

1. 系统 SHALL 支持 Linux Crontab（5 段：分 时 日 月 周）、Spring（6 段：秒 分 时 日 月 周）、Quartz（6 或 7 段，末段为年）
2. WHEN 用户在 Linux 方言输入 `@daily`、`@hourly` 等别名，系统 SHALL 按对应标准表达式解析
3. WHEN 用户在 Quartz 方言输入仅含 6 段或 7 段的表达式，系统 SHALL 均接受，缺省年表示不限年
4. WHEN 输入段数与所选方言不符，系统 SHALL 提示段数错误而不是笼统的「表达式非法」

### Requirement 2: 日与周语义

**User Story:** AS 开发者, I want 各方言的日/周特殊语义被正确处理, so that 结果与线上调度器一致。

#### Acceptance Criteria

1. WHILE Linux 或 Spring 方言中日与周同时受限，系统 SHALL 按 OR 语义匹配日期
2. WHILE Quartz 方言，系统 SHALL 要求日与周恰好一个为 `?`，两者都受限或都通配时视为非法
3. WHEN Quartz 方言出现 `L`（月末/最后一个周几）、`W`（最近工作日）、`#`（第 N 个周几），系统 SHALL 按 Quartz 语义匹配
4. WHEN 表达式使用英文缩写（`JAN`、`MON` 等），系统 SHALL 在各方言下正确换算为数值
5. WHEN Quartz 方言使用 `1`-`7` 表示周，系统 SHALL 将 1 视为周日

### Requirement 3: 生成模式

**User Story:** AS 开发者, I want 不记语法也能拼出表达式, so that 我可以从模板改起而不是从零手写。

#### Acceptance Criteria

1. WHEN 用户切换到生成模式，系统 SHALL 按方言展示对应字段（Linux 无秒、Quartz 含年）
2. 系统 SHALL 提供每分钟、每五分钟、每小时、每天零点、每天九点、每周一九点、每月一号、每年八种模板
3. WHEN 用户选择模板，系统 SHALL 用模板值填充各字段
4. WHEN 用户修改任一字段，系统 SHALL 实时拼接出完整表达式并允许复制
5. WHEN 用户在 Quartz 方言选择模板，系统 SHALL 自动把日或周之一填为 `?`，保证生成结果合法

### Requirement 4: 结果展示

**User Story:** AS 开发者, I want 复制整份执行时间, so that 我可以直接贴到文档或排查记录里。

#### Acceptance Criteria

1. WHEN 解析成功，系统 SHALL 展示未来十次执行时间，按升序排列
2. WHEN 用户复制结果，系统 SHALL 复制全部十行时间
3. WHEN 搜索范围内无解或超出年限，系统 SHALL 提示无解而不是返回空结果
4. 系统 SHALL 使用设备本地时区渲染时间，并提供简体中文、英文、繁体中文界面文案
