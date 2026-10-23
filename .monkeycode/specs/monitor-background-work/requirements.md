# Requirements Document

## Introduction

接口拨测目前依赖进程内协程循环，应用被系统回收或长时间处于后台后检查会静默停止。需要一层系统级调度保证后台仍能按期检查，同时不破坏现有的分钟级精度、告警去重与记录语义。

## Glossary

- **System**: Pulse 接口监控
- **前台循环（Foreground Loop）**: 进程存活时按目标频率执行的协程
- **后台唤醒（Background Wake-up）**: 进程不存在时由系统调度的周期任务
- **到期判断（Due Check）**: 依据上次检查时间与目标频率决定是否执行

## Requirements

### Requirement 1: 后台仍可检查

**User Story:** AS 拨测用户, I want 应用退到后台后继续检查接口, so that 我不会因为没打开 App 而错过故障。

#### Acceptance Criteria

1. WHEN 存在启用中的拨测目标，系统 SHALL 注册周期后台任务
2. WHEN 用户禁用或删除全部启用目标，系统 SHALL 取消周期后台任务
3. WHEN 最短目标频率短于系统周期下限，系统 SHALL 以下限作为后台周期
4. WHEN 最短目标频率长于系统周期下限，系统 SHALL 以该频率作为后台周期

### Requirement 2: 不重复检查

**User Story:** AS 拨测用户, I want 前后台不会把同一次检查做两遍, so that 记录与流量都不被放大。

#### Acceptance Criteria

1. WHEN 后台任务被唤醒，系统 SHALL 只检查已到期的启用目标
2. WHEN 同一目标正被检查，系统 SHALL 拒绝并发的第二次检查
3. WHEN 目标从未被检查过，系统 SHALL 视为立即到期
4. WHEN 距上次检查不足一个频率（含少量提前量），系统 SHALL 跳过该目标

### Requirement 3: 语义一致

**User Story:** AS 拨测用户, I want 后台检查与前台检查表现一致, so that 我不用区分结果来源。

#### Acceptance Criteria

1. WHEN 后台完成任务，系统 SHALL 写入与前台相同的记录与趋势样本
2. WHEN 后台检测到状态变化，系统 SHALL 沿用同一套告警去重规则
3. WHEN 进程重启，系统 SHALL 用持久化历史回填最近状态，避免重复告警
4. WHEN 后台任务执行异常，系统 SHALL 返回重试而不是崩溃

### Requirement 4: 前台精度不回退

**User Story:** AS 拨测用户, I want 打开应用时仍按 15 秒级频率检查, so that 精细化观察不受影响。

#### Acceptance Criteria

1. WHEN 应用处于前台，系统 SHALL 继续按目标频率（最短 15 秒）执行检查
2. WHEN 应用启动，系统 SHALL 立即对账启用目标与正在运行的循环
