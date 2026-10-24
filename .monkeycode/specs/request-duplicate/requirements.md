# Requirements Document

## Introduction

集合里的接口没有复制入口，想基于已有请求改一版只能重新保存，容易产生两个互不相关的条目，也不方便对比。

## Glossary

- **System**: Pulse 集合页
- **副本**: 与原条目同文件夹、同快照、仅 id 与名称不同的新条目

## Requirements

### Requirement 1: 复制入口

**User Story:** AS 用户, I want 一键复制接口, so that 我能基于已有请求快速改一版。

#### Acceptance Criteria

1. WHEN 用户展开接口行的更多菜单，系统 SHALL 提供「复制」
2. WHEN 用户点击复制，系统 SHALL 立即在集合中生成副本，不弹额外对话框
3. WHEN 当前界面为英文或繁体，系统 SHALL 使用对应语言的副本命名

### Requirement 2: 副本内容

**User Story:** AS 用户, I want 副本与原请求一致, so that 我只改需要改的部分。

#### Acceptance Criteria

1. WHEN 生成副本，系统 SHALL 复制协议类型与完整快照
2. WHEN 生成副本，系统 SHALL 保持与原条目相同的文件夹
3. WHEN 生成副本，系统 SHALL 使用新的条目 id 与创建时间
4. WHEN 生成副本，系统 SHALL 命名为主条目名加「副本」后缀
5. WHEN 原条目不存在，系统 SHALL 不产生任何写入
6. WHEN 名称计算结果为空，系统 SHALL 拒绝复制

### Requirement 3: 排列位置

**User Story:** AS 用户, I want 副本就在原条目旁边, so that 我能马上找到它。

#### Acceptance Criteria

1. WHEN 生成副本，系统 SHALL 把它插入到原条目之后
2. WHEN 列表随后重新渲染，系统 SHALL 保持该相邻关系

### Requirement 4: 持久化

**User Story:** AS 用户, I want 副本立即生效并保存, so that 重启后仍在。

#### Acceptance Criteria

1. WHEN 复制成功，系统 SHALL 触发一次数据落盘
2. WHEN 复制失败（条目不存在或名称空白），系统 SHALL 不触发落盘
