# Requirements Document

## Introduction

集合页从「项目 → 集合 → 接口」两级扁平结构改为无限嵌套的文件夹树。文件夹（沿用「集合」命名）可包含子文件夹与接口条目，支持展开/折叠、新建、重命名、递归删除、移动；保存接口时的目标选择改为扁平下拉并按完整路径展示。

## Glossary

- **集合页**：Tab 栏中的「集合」页面，展示当前项目下的接口分组。
- **文件夹**：原「集合」的容器实体，可包含子文件夹与接口条目。
- **根文件夹**：直接属于项目的文件夹，`parentId` 为空。
- **子文件夹**：`parentId` 指向某个文件夹的文件夹。
- **接口条目**：保存的 HTTP / WS / MQTT / SSE 请求快照。
- **完整路径**：从根文件夹到该文件夹的名称链，以「 / 」连接。
- **移动模式**：长按文件夹后进入的临时交互状态，用于选择新的父文件夹。

## Requirements

### Requirement 1: 树形展示

**User Story:** AS 用户, I want 在集合页以缩进树形浏览接口分组, so that 我能直观判断每个接口属于哪一层分组

#### Acceptance Criteria

1. WHEN 用户进入集合页, THE 集合页 SHALL 以树形结构展示当前项目下的全部文件夹与其接口条目
2. WHILE 展示树形结构, THE 集合页 SHALL 让根文件夹不缩进、子文件夹按其层级逐级增加缩进
3. THE 集合页 SHALL 在每个文件夹行前显示一个方向箭头标识其展开状态
4. WHILE 文件夹处于展开状态, THE 集合页 SHALL 显示指向下方的箭头并递归展示其子文件夹与接口条目
5. WHILE 文件夹处于折叠状态, THE 集合页 SHALL 显示指向右侧的箭头并隐藏其子文件夹与接口条目

### Requirement 2: 展开与折叠

**User Story:** AS 用户, I want 折叠和展开文件夹, so that 我能聚焦当前关心的分支

#### Acceptance Criteria

1. WHEN 用户点击文件夹行的箭头, THE 集合页 SHALL 切换该文件夹的展开与折叠状态
2. WHEN 用户点击文件夹行的名称文字, THE 集合页 SHALL 切换该文件夹的展开与折叠状态
3. WHILE 用户浏览集合页, THE 集合页 SHALL 默认展开全部文件夹
4. WHEN 用户在展开状态下创建子文件夹, THE 集合页 SHALL 保持该文件夹处于展开状态
5. IF 用户切换项目后再返回, THE 集合页 SHALL 重新按默认展开状态展示

### Requirement 3: 新建文件夹

**User Story:** AS 用户, I want 新建根文件夹与子文件夹, so that 我能按自己的习惯组织接口

#### Acceptance Criteria

1. WHEN 用户点击集合页的「新建集合」入口, THE 集合页 SHALL 打开命名对话框并在当前项目下创建根文件夹
2. WHEN 用户点击某个文件夹行的「新建子文件夹」入口, THE 集合页 SHALL 打开命名对话框并标注该操作的目标位置
3. WHEN 用户提交非空名称, THE 集合页 SHALL 创建文件夹、持久化数据并更新界面
4. IF 用户提交空白名称, THE 集合页 SHALL 保持对话框打开且不创建任何文件夹
5. IF 同一父文件夹下已存在相同名称的文件夹, THE 集合页 SHALL 允许创建重名文件夹

### Requirement 4: 重命名文件夹

**User Story:** AS 用户, I want 重命名文件夹, so that 我能纠正拼写或调整分类语义

#### Acceptance Criteria

1. WHEN 用户点击文件夹行的「重命名」入口, THE 集合页 SHALL 打开名称已填入当前值的对话框
2. WHEN 用户提交非空名称, THE 集合页 SHALL 更新文件夹名称并持久化
3. IF 用户提交空白名称, THE 集合页 SHALL 保留原名称且不关闭提示
4. WHEN 用户重命名一个文件夹, THE 集合页 SHALL 保持其子文件夹与接口条目不变

### Requirement 5: 递归删除文件夹

**User Story:** AS 用户, I want 删除整个文件夹分支, so that 我能一次清理废弃的分组

#### Acceptance Criteria

1. WHEN 用户点击文件夹行的「删除」入口, THE 集合页 SHALL 显示确认对话框并在对话框中显示将被删除的文件夹数量与接口条目数量
2. WHEN 用户确认删除, THE 集合页 SHALL 删除该文件夹、其全部后代文件夹与全部后代下的接口条目并持久化
3. WHEN 用户在确认对话框中选择取消, THE 集合页 SHALL 保留该文件夹的全部数据
4. IF 被删除的文件夹处于折叠状态, THE 集合页 SHALL 仍删除其全部后代
5. WHILE 存在已删除文件夹的引用, THE 集合页 SHALL 从界面中移除该引用而不崩溃

### Requirement 6: 移动文件夹

**User Story:** AS 用户, I want 把文件夹移动到另一个位置, so that 我能随时调整分组结构

#### Acceptance Criteria

1. WHEN 用户长按文件夹行, THE 集合页 SHALL 进入移动模式并给出正在移动的文件夹名称提示
2. WHILE 处于移动模式, THE 集合页 SHALL 把每一个可见文件夹标记为可放置目标
3. WHILE 处于移动模式, THE 集合页 SHALL 在用户点击某个文件夹时把被移动的文件夹放入该目标文件夹
4. WHEN 移动成功, THE 集合页 SHALL 展开目标父文件夹、持久化新结构并退出移动模式
5. IF 目标文件夹是被移动文件夹自身或其任一后代, THE 集合页 SHALL 拒绝该次移动并提示无法移动到自身或其子文件夹内
6. WHEN 移动目标是属于其他项目的文件夹, THE 集合页 SHALL 同步更新被移动文件夹的所属项目为目标所在项目
7. WHEN 用户取消移动模式, THE 集合页 SHALL 保留原有结构不变
8. WHILE 处于移动模式, THE 集合页 SHALL 提供明确的取消入口

### Requirement 7: 保存接口到文件夹

**User Story:** AS 用户, I want 把当前请求保存到指定文件夹, so that 我能把接口归档到正确的分支

#### Acceptance Criteria

1. WHEN 用户打开保存对话框, THE 保存对话框 SHALL 以下拉列表展示当前项目下的全部文件夹
2. WHILE 下拉列表包含嵌套文件夹, THE 保存对话框 SHALL 用完整路径区分不同文件夹
3. WHEN 用户选择某个文件夹并提交保存, THE 集合页 SHALL 把接口条目保存为该文件夹的直接成员
4. IF 当前项目下没有任何文件夹, THE 保存对话框 SHALL 显示默认文件夹项并由存储层自动创建根文件夹
5. WHEN 接口条目被保存, THE 集合页 SHALL 在目标文件夹下显示该条目

### Requirement 8: 旧数据迁移

**User Story:** AS 用户, I want 升级后原有集合数据不丢失, so that 我能继续使用历史接口

#### Acceptance Criteria

1. WHEN 持久化层读取缺少父文件夹字段的旧记录, THE 持久化层 SHALL 把该记录视为根文件夹
2. WHEN 迁移发生, THE 持久化层 SHALL 保留原有文件夹名称、排列顺序与其下的接口条目
3. IF 迁移后某条接口条目引用的文件夹不存在, THE 持久化层 SHALL 丢弃该接口条目
4. WHEN 持久化层写出集合数据, THE 持久化层 SHALL 写出父文件夹字段以保证升级往返一致

### Requirement 9: 空状态

**User Story:** AS 用户, I want 在空状态下得到明确指引, so that 我知道下一步该做什么

#### Acceptance Criteria

1. IF 当前项目下没有任何根文件夹, THE 集合页 SHALL 显示空状态提示并保留新建入口
2. WHILE 某个展开的文件夹内没有任何接口条目且没有子文件夹, THE 集合页 SHALL 显示该文件夹为空的提示
3. WHILE 展示树形结构, THE 集合页 SHALL 在页面底部保留新建集合入口

### Requirement 10: 点击接口条目

**User Story:** AS 用户, I want 从集合页直接打开一个已保存的接口, so that 我能快速复用历史请求

#### Acceptance Criteria

1. WHEN 用户点击接口条目行, THE 集合页 SHALL 写入待加载快照并切换到该接口所属协议的 Tab
2. WHEN 用户点击接口条目行右侧的删除入口, THE 集合页 SHALL 仅删除该接口条目而不影响其所属文件夹
3. THE 集合页 SHALL 在接口条目行上显示协议类型标识与接口名称
