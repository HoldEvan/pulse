# Requirements Document

## Introduction

集合内接口按保存顺序排列，没有调整入口；新建的接口总是在最前面，常用的接口会被后来的条目挤下去。

## Glossary

- **System**: Pulse 集合页
- **同级顺序**: 同一文件夹内接口在扁平列表里的相对顺序
- **置顶**: 把接口移到所在文件夹的最前面

## Requirements

### Requirement 1: 上移与下移

**User Story:** AS 用户, I want 调整接口顺序, so that 我能把常用接口放在顺手的位置。

#### Acceptance Criteria

1. WHEN 用户打开接口行更多菜单，系统 SHALL 提供「上移」与「下移」
2. WHEN 接口已在文件夹首位，系统 SHALL 禁用「上移」
3. WHEN 接口已在文件夹末位，系统 SHALL 禁用「下移」
4. WHEN 用户执行移动，系统 SHALL 只与同文件夹的相邻条目交换位置
5. WHEN 移动成功，系统 SHALL 立即落盘；已在边界时 SHALL 不写盘

### Requirement 2: 置顶

**User Story:** AS 用户, I want 把接口钉在最前, so that 高频接口不用每次往下翻。

#### Acceptance Criteria

1. WHEN 用户点击「置顶」，系统 SHALL 把接口移到所在文件夹最前
2. WHEN 接口已在首位，系统 SHALL 禁用「置顶」
3. WHEN 置顶完成，系统 SHALL 保持其他文件夹条目的相对顺序不变

### Requirement 3: 隔离

**User Story:** AS 用户, I want 调整顺序不影响其他集合, so that 排序结果可预期。

#### Acceptance Criteria

1. WHEN 用户调整某个文件夹的顺序，系统 SHALL 不改动其他文件夹条目的相对顺序
2. WHEN 用户移动接口到其他文件夹，系统 SHALL 由既有的移动功能处理，排序操作不越权

### Requirement 4: 持久化

**User Story:** AS 用户, I want 顺序重启后保持, so that 我整理过的列表不会复原。

#### Acceptance Criteria

1. WHEN 顺序变化，系统 SHALL 立即落盘
2. WHEN 应用重启，系统 SHALL 按落盘后的顺序展示
