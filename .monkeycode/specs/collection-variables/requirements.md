# Requirements Document

## Introduction

变量目前只有项目级环境，同一项目里不同集合的接口只能共用一套值；想按集合区分 host、token 只能复制环境。需要文件夹（集合）级变量，支持继承与覆盖，并在脚本里可读写。

## Glossary

- **System**: Pulse 集合页、协议页与集合运行器
- **文件夹变量**: 绑定在某个集合/文件夹上的键值对
- **继承**: 子文件夹自动获得祖先变量，同名时子级覆盖父级
- **pm.collectionVariables**: 脚本里读写当前文件夹变量的对象

## Requirements

### Requirement 1: 变量编辑

**User Story:** AS 用户, I want 给集合配一套变量, so that 同一集合的接口共用配置。

#### Acceptance Criteria

1. WHEN 用户打开当前文件夹的更多菜单，系统 SHALL 提供「变量」入口
2. WHEN 用户编辑变量，系统 SHALL 支持键值对的增删改，并保留启用状态
3. WHEN 用户保存，系统 SHALL 只保留键非空的条目
4. WHEN 用户清空全部变量，系统 SHALL 删除该文件夹的变量记录

### Requirement 2: 继承与覆盖

**User Story:** AS 用户, I want 子文件夹沿用父级变量, so that 我不必重复填写。

#### Acceptance Criteria

1. WHEN 解析某文件夹的变量，系统 SHALL 按根到叶的顺序合并
2. WHEN 父子存在同名变量，系统 SHALL 采用子级的值
3. WHEN 变量被禁用或键为空，系统 SHALL 跳过该条
4. WHEN 删除文件夹，系统 SHALL 一并删除其变量
5. WHEN 恢复备份，系统 SHALL 只保留仍存在文件夹的变量

### Requirement 3: 解析优先级

**User Story:** AS 用户, I want 集合变量能覆盖环境默认值, so that 我可以按集合定制。

#### Acceptance Criteria

1. WHEN 解析请求占位符，系统 SHALL 使用「环境变量 + 文件夹变量」，同名时文件夹变量优先
2. WHEN 用户从集合加载请求，系统 SHALL 使用该请求所在文件夹的变量
3. WHEN 用户把新请求保存到集合，系统 SHALL 立即切换为该文件夹的变量
4. WHEN 请求不属于任何集合，系统 SHALL 只使用环境变量
5. WHEN HTTP、WS、SSE、MQTT 解析占位符，系统 SHALL 采用同一套合并规则

### Requirement 4: 脚本与运行器

**User Story:** AS 用户, I want 脚本能读写集合变量, so that 我能在链路里传递集合级配置。

#### Acceptance Criteria

1. WHEN 请求属于某个文件夹，系统 SHALL 在脚本里提供 `pm.collectionVariables`
2. WHEN 脚本写入集合变量，系统 SHALL 只改当前文件夹自身的变量，不复制继承值
3. WHEN 脚本删除集合变量，系统 SHALL 从当前文件夹移除该键
4. WHEN 集合运行器执行请求，系统 SHALL 按每条请求所在文件夹合并变量
5. WHEN 请求没有所属文件夹，系统 SHALL 不提供 `pm.collectionVariables`

### Requirement 5: 持久化

**User Story:** AS 用户, I want 变量重启后仍在, so that 配置不丢。

#### Acceptance Criteria

1. WHEN 变量变更，系统 SHALL 立即落盘
2. WHEN 应用启动，系统 SHALL 读回变量
3. WHEN 旧数据没有变量段，系统 SHALL 以空表启动
