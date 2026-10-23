# Requirements Document

## Introduction

从集合打开的请求，以及刚保存过一次的请求，再次点击保存时直接覆盖原条目，避免在同一集合里堆出重复的接口记录。

## Glossary

- **System**: 集合与协议页（Collection / HTTP / WS / SSE / MQTT）
- **绑定（Binding）**: 当前协议页与某条已保存请求之间的关联关系
- **原地覆盖（Overwrite）**: 更新已保存条目的快照，不新增条目、不改名称与位置
- **快照（Snapshot）**: 协议页当前配置序列化后的字符串

## Requirements

### Requirement 1: 从集合加载后绑定

**User Story:** AS 接口调试用户, I want 从集合点开的请求保存时回到原条目, so that 我不会无意中产生第二条同名记录。

#### Acceptance Criteria

1. WHEN 用户从集合或历史中加载一条已保存请求，系统 SHALL 记住该条目 id
2. WHEN 已绑定条目时用户点击保存，系统 SHALL 原地覆盖该条目并提示「已更新」
3. WHEN 覆盖完成，系统 SHALL 保持条目的名称、所属集合与排序位置不变
4. WHEN 用户随后修改后再次保存，系统 SHALL 继续覆盖同一条目，不再提示保存对话框

### Requirement 2: 新建后立即绑定

**User Story:** AS 接口调试用户, I want 保存过一次之后继续调试再保存不再弹窗, so that 反复微调时不会每次都走一遍保存流程。

#### Acceptance Criteria

1. WHEN 用户在保存对话框确认新建，系统 SHALL 返回新条目 id 并立即绑定
2. WHEN 绑定后用户再次保存，系统 SHALL 直接覆盖，不弹出保存对话框

### Requirement 3: 失效回退

**User Story:** AS 接口调试用户, I want 条目被删除后保存仍能正常工作, so that 我的配置不会因为条目消失而丢失。

#### Acceptance Criteria

1. WHEN 已绑定条目在别处被删除，系统 SHALL 解除绑定
2. WHEN 绑定失效时用户点击保存，系统 SHALL 弹出保存对话框而不是静默失败
3. WHEN 覆盖失败，系统 SHALL 不写入任何数据

### Requirement 4: 多协议一致

**User Story:** AS 用户, I want HTTP、WebSocket、SSE、MQTT 行为一致, so that 我不需要记住每个协议页有不同的保存规则。

#### Acceptance Criteria

1. 系统 SHALL 在 HTTP、WS、SSE、MQTT 四个协议页应用同一套绑定与覆盖逻辑
2. WHEN 切换项目或从空白页新建请求，系统 SHALL 不保留上一个条目的绑定
