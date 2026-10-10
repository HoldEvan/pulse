# Requirements Document

## Introduction

为拨测功能增加一级分组能力：拨测目标可归属到一个自定义分组名（自由文本，无层级），目标列表按分组名分块展示并显示组头，未设置分组的目标显示在「未分组」组头下。

## Glossary

- **System**: 拨测功能（Monitor）
- **拨测目标（Monitor Target）**: 一条可被自动检查的 IP/端口/接口配置
- **分组（Group）**: 拨测目标的归类标签，一级、无层级，以自由文本分组名区分
- **未分组（Ungrouped）**: 分组名为空或为"未分组"文本的目标所属的默认组

## Requirements

### Requirement 1: 目标携带分组字段

**User Story:** AS 拨测用户, I want 每条拨测目标有一个可编辑的分组名, so that 我可以按用途归类拨测目标。

#### Acceptance Criteria

1. WHEN 用户编辑或新增拨测目标，系统 SHALL 提供一个分组名输入框
2. WHEN 用户保存目标，系统 SHALL 将输入的分组名随目标一同持久化
3. WHEN 旧备份数据中不存在分组字段，系统 SHALL 将分组名视为空字符串

### Requirement 2: 目标列表按分组展示

**User Story:** AS 拨测用户, I want 目标列表按分组名分块显示组头和组内目标, so that 我可以快速定位某个用途下的目标。

#### Acceptance Criteria

1. WHEN 拨测页展示目标列表，系统 SHALL 按分组名对目标进行分组
2. WHEN 存在多个分组，系统 SHALL 对每个分组显示组头与组内目标
3. WHEN 目标的分组名为空，系统 SHALL 将其归入并显示「未分组」组头
4. WHEN 分组排序，系统 SHALL 保持组内目标原有的时间倒序（最新的在前）

### Requirement 3: 分组名的创建与管理

**User Story:** AS 拨测用户, I want 直接在目标编辑弹窗中填写或修改分组名, so that 我不需要单独维护分组列表。

#### Acceptance Criteria

1. WHEN 用户填写分组名，系统 SHALL 接受任意非空白文本作为分组名
2. WHEN 两个目标填写相同分组名，系统 SHALL 将其展示在同一组
3. WHEN 用户修改某目标的分组名为新文本，系统 SHALL 在保存后将该目标移动到对应组
4. WHEN 用户将分组名清空，系统 SHALL 将该目标归入「未分组」

## Non-Requirements

- 系统 SHALL NOT 提供多层级分组或子分组
- 系统 SHALL NOT 提供单独的分组管理界面（新建/重命名/删除分组列表）
- 系统 SHALL NOT 在未通记录列表中按分组展示
