# Requirements Document

## Introduction

集合目前只能逐层下钻浏览，找不到接口时只能一层层翻；备份只能手动导出，中断几天就可能丢数据。需要跨文件夹搜索与定时自动备份。

## Glossary

- **System**: Pulse 集合页与设置页
- **搜索结果（Search Hit）**: 名称或地址命中关键词的接口及其所在文件夹
- **自动备份（Auto Backup）**: 按周期导出数据到 WebDAV 并裁剪旧文件的定时任务
- **保留份数（Keep Count）**: 远端最多保留的备份文件数

## Requirements

### Requirement 1: 集合内搜索

**User Story:** AS 用户, I want 一次搜到整个项目的接口, so that 我不用逐层翻文件夹。

#### Acceptance Criteria

1. WHEN 用户在搜索框输入关键词，系统 SHALL 在当前项目全部文件夹内按接口名与地址匹配
2. WHEN 关键词非空，系统 SHALL 用搜索结果替换正常浏览视图
3. WHEN 展示结果，系统 SHALL 显示协议类型、接口名、所在文件夹路径与地址
4. WHEN 用户点击结果，系统 SHALL 加载该接口并切到对应协议页
5. WHEN 没有匹配项，系统 SHALL 提示无结果并提供清除入口
6. WHEN 关键词清空，系统 SHALL 回到面包屑浏览
7. WHEN 关键词只含空白，系统 SHALL 视为空关键词

### Requirement 2: 自动备份配置

**User Story:** AS 用户, I want 设定周期自动上传备份, so that 不必记得手动导出。

#### Acceptance Criteria

1. WHEN 用户打开设置页备份区，系统 SHALL 提供「定时自动备份」开关
2. WHEN WebDAV 地址为空，系统 SHALL 禁用开关并说明原因
3. WHEN 开关打开，系统 SHALL 允许选择周期与保留份数
4. WHEN 用户选择周期，系统 SHALL 支持 6 小时、12 小时、每天、3 天、每周
5. WHEN 用户设置保留份数，系统 SHALL 限制在 1–30

### Requirement 3: 自动备份执行

**User Story:** AS 用户, I want 备份在后台按期执行, so that 关闭应用也不会中断。

#### Acceptance Criteria

1. WHEN 自动备份启用，系统 SHALL 注册周期后台任务
2. WHEN 自动备份关闭或地址被清空，系统 SHALL 取消周期任务
3. WHEN 任务执行，系统 SHALL 以执行时刻的最新配置为准
4. WHEN 导出成功，系统 SHALL 删除超出保留份数的旧备份
5. WHEN 导出失败，系统 SHALL 返回重试而不是崩溃
6. WHEN 用户修改周期，系统 SHALL 就地更新任务周期而不再叠加新任务

### Requirement 4: 配置持久化

**User Story:** AS 用户, I want 设置重启后仍生效, so that 我不必每次重新配置。

#### Acceptance Criteria

1. WHEN 用户修改自动备份设置，系统 SHALL 持久化开关、周期与保留份数
2. WHEN 读取到越界的周期下标，系统 SHALL 回落到每天一次
3. WHEN 读取到越界的保留份数，系统 SHALL 收敛到合法区间
