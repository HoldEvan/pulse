# Requirements Document

## Introduction

v0.2.1 的界面与交互打磨：折叠式信息区块、可横向滚动的标签栏、更清晰的 JSON 树、监控记录按拨测项过滤、Cookie 删除二次确认、主题顺序调整、环境新建入口前移、跟随系统取色的启动图标。

## Glossary

- **System**: Pulse Android 客户端
- **可折叠区块（Collapsible Section）**: 标题行可点击展开/收起的容器，标题右侧显示条目数
- **键值表（Key-Value Table）**: 两列布局的请求头/响应头展示，键列定宽、值列换行
- **拨测项限定（Target Scope）**: 记录列表只显示某一个拨测目标的记录的状态

## Requirements

### Requirement 1: 可折叠区块

**User Story:** AS 用户, I want 长列表默认收起并按需展开, so that 页面首屏不被大量明细淹没。

#### Acceptance Criteria

1. WHEN 页面展示请求头，系统 SHALL 默认折叠，点击标题行展开
2. WHEN 监控目标按分组展示，系统 SHALL 用可折叠分组标题，标题右侧显示该组目标数
3. WHEN 分组内容增删，系统 SHALL 按标题记忆折叠状态，不把状态错配到其他分组
4. WHEN 历史详情展示响应头，系统 SHALL 默认展开，请求头默认折叠
5. WHEN 请求头以 `key: value` 单行存储，系统 SHALL 在展示前拆成两列

### Requirement 2: 响应体标签栏

**User Story:** AS 开发者, I want 标签文字完整可读, so that 我不必猜测被截断的按钮是什么。

#### Acceptance Criteria

1. WHEN 响应体标签超过五个，系统 SHALL 让整排可横向滚动
2. 系统 SHALL 为每个标签提供不小于 84dp 的最小宽度，避免文字被截断

### Requirement 3: JSON 树

**User Story:** AS 开发者, I want 展开的层级有明确边界, so that 我能看清对象与数组的嵌套范围。

#### Acceptance Criteria

1. WHEN JSON 容器展开，系统 SHALL 在键名后显示 `{` 或 `[`，并在内容结束后显示配对的右括号
2. WHEN 容器折叠，系统 SHALL 显示内容预览而不是空字符串
3. WHEN 容器展开，系统 SHALL 用次级文字色渲染括号，与键名区分

### Requirement 4: 监控记录按目标过滤

**User Story:** AS 拨测用户, I want 从某个拨测项直接看它的记录, so that 我不需要在全量记录里反复筛选。

#### Acceptance Criteria

1. WHEN 用户点击某个拨测目标行，系统 SHALL 进入记录页并只显示该目标的记录
2. WHEN 处于限定状态，系统 SHALL 在列表顶部显示「仅看 X」卡片，并提供取消按钮
3. WHEN 用户取消限定，系统 SHALL 让卡片淡出、列表平滑回到全量
4. WHEN 用户从记录入口进入，系统 SHALL 清空目标限定并显示全部记录

### Requirement 5: Cookie 删除确认

**User Story:** AS 用户, I want 删除前确认, so that 误触不会直接清掉登录状态。

#### Acceptance Criteria

1. WHEN 用户删除单条 Cookie、清空某域名或清空项目全部 Cookie，系统 SHALL 先弹出确认对话框
2. WHEN 确认对话框展示，系统 SHALL 说明删除范围与不可恢复
3. WHEN 用户取消，系统 SHALL 不修改任何 Cookie

### Requirement 6: 主题与环境入口

**User Story:** AS 用户, I want 常用操作在顺手的位置, so that 少点几次。

#### Acceptance Criteria

1. WHEN 用户查看主题模式选项，系统 SHALL 按「跟随系统、深色、浅色」顺序展示，且不影响已保存的设置
2. WHEN 用户在资源页的环境标签，系统 SHALL 在顶栏显示「新建环境」按钮
3. WHEN 用户从设置进入资源页，系统 SHALL 默认停在环境标签
4. WHEN 用户尝试新建环境，系统 SHALL 与顶栏按钮共享同一份页面状态

### Requirement 7: 启动图标与版本

**User Story:** AS 用户, I want 图标跟随系统配色, so that 桌面风格统一。

#### Acceptance Criteria

1. WHEN 设备为 Android 12 及以上，系统 SHALL 用系统莫奈强调色作为启动图标背景
2. 系统 SHALL 将版本号提升到 0.2.1（versionCode 39）
