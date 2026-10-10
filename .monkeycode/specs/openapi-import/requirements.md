# Requirements Document

## Introduction

在集合页增加导入功能，把 OpenAPI / Swagger 规范（JSON 或 YAML）转换为一条新的集合及其下的一组 HTTP 接口条目，省去逐个手工录入接口的工作量。本阶段只支持 OpenAPI 与 Swagger 规范，不支持 Postman Collection、HAR、cURL 等其他格式。

## Glossary

- **集合页**：`CollectionScreen`，按面包屑逐层浏览集合，并在其中保存接口条目。
- **规范**：一份 OpenAPI 或 Swagger 文档，JSON 或 YAML 两种文本形式。
- **接口条目**：`SavedRequest`，一条保存的接口，其 `data` 字段是 `HttpSnapshot` 的 JSON 快照。
- **基础地址**：规范声明的服务器地址，由 `servers[0].url`（OpenAPI 3）或 `schemes[0]://host + basePath`（Swagger 2）得到。
- **操作**：`paths` 下某个路径的某个 HTTP 方法（get/put/post/delete/options/head/patch/trace）。
- **标签组**：按操作声明的第一个 `tags` 值归类得到的分组。
- **schema 骨架**：按 JSON Schema 结构生成的最小示例对象，供无示例的请求体使用。

## Requirements

### Requirement 1：导入入口

**User Story:** AS 使用者, I want 在集合页直接导入规范, so that 我不用逐个手工录入接口

#### Acceptance Criteria

1. WHEN 使用者在集合页点击导入入口, 集合页 SHALL 打开 OpenAPI 导入弹窗
2. WHILE 导入弹窗已打开, 集合页 SHALL 同时提供「粘贴内容」与「从地址导入」两种输入方式
3. WHILE 导入弹窗已打开, 集合页 SHALL 允许使用者取消导入并保留已输入的内容

### Requirement 2：规范文本解析

**User Story:** AS 使用者, I want 直接粘贴 JSON 或 YAML, so that 两种常见格式都能用

#### Acceptance Criteria

1. WHEN 使用者提交的文本以 `{` 或 `[` 开头, 导入器 SHALL 按 JSON 解析该文本
2. WHEN 使用者提交的文本不以 `{` 或 `[` 开头, 导入器 SHALL 按 YAML 解析该文本
3. WHEN YAML 解析涉及锚点与别名, 导入器 SHALL 正确解析引用关系
4. IF 文本既不是合法 JSON 也不是合法 YAML, 导入器 SHALL 返回「无法解析」错误
5. IF 提交内容为空白, 导入器 SHALL 返回「内容为空」错误

### Requirement 3：OpenAPI 与 Swagger 识别

**User Story:** AS 使用者, I want 两种规范的地址都能拼对, so that 导入后接口可以直接发送

#### Acceptance Criteria

1. WHEN 规范为 OpenAPI 3.x, 导入器 SHALL 使用 `servers[0].url` 作为基础地址
2. WHEN 规范为 Swagger 2.x, 导入器 SHALL 使用 `schemes[0]://host + basePath` 作为基础地址
3. IF 规范缺少 `paths` 节点, 导入器 SHALL 返回「不是 OpenAPI 或 Swagger 规范」错误
4. IF `paths` 下没有任何可导入的操作, 导入器 SHALL 返回「没有可导入的接口」错误

### Requirement 4：接口条目生成

**User Story:** AS 使用者, I want 每个操作都变成一条可编辑的接口, so that 导入即可使用

#### Acceptance Criteria

1. WHEN 遍历 `paths`, 导入器 SHALL 为每个路径的每个 HTTP 方法生成一条接口条目
2. 导入器生成的接口名 SHALL 依次取 `operationId`、`summary`、`方法 + 路径`，取到第一个非空值为止
3. 导入器生成的接口地址 SHALL 为基础地址拼接路径，路径中的 `{参数}` 占位原样保留
4. IF 操作的方法不被应用支持, 导入器 SHALL 跳过该操作而不中断整体导入

### Requirement 5：参数与请求体映射

**User Story:** AS 使用者, I want 参数和请求体跟着规范一起进来, so that 我只需补值即可发送

#### Acceptance Criteria

1. WHEN 参数位置为 `query` 或 `header` 且声明了 `default`, 导入器 SHALL 生成启用状态的键值条目
2. WHEN 参数未声明 `default`, 导入器 SHALL 不生成该条目
3. WHEN 请求体媒体类型为 `application/json`, 导入器 SHALL 设置 `bodyType` 为 `JSON`
4. WHEN `application/json` 声明了 `example` 或 `examples`, 导入器生成的请求体 SHALL 为该示例的美化 JSON
5. WHEN `application/json` 没有示例, 导入器生成的请求体 SHALL 为按 `schema` 生成的骨架
6. WHEN 请求体媒体类型为 `application/x-www-form-urlencoded`, 导入器 SHALL 设置 `bodyType` 为 `FORM_URLENCODED` 并把 `schema` 属性写入查询参数
7. WHEN 请求体媒体类型为 `multipart/form-data`, 导入器 SHALL 设置 `bodyType` 为 `FORM_DATA` 并把 `schema` 属性写入表单字段
8. WHEN 请求体媒体类型为其他类型, 导入器 SHALL 设置 `bodyType` 为 `RAW`
9. WHEN 参数或请求体以 `$ref` 引用了公共定义, 导入器 SHALL 解析引用后按被引用内容处理

### Requirement 6：认证映射

**User Story:** AS 使用者, I want 规范声明的认证方式带进来, so that 我只需补 token 即可发送

#### Acceptance Criteria

1. WHEN 操作声明 `type: http` 且 `scheme: bearer` 认证, 导入器 SHALL 设置 `authType` 为 `BEARER`
2. WHEN 操作声明 `type: http` 且 `scheme: basic` 认证, 导入器 SHALL 设置 `authType` 为 `BASIC`
3. WHEN 操作声明 `type: apiKey` 认证, 导入器 SHALL 设置 `authType` 为 `APIKEY` 并把 `apiKeyName` 设为该定义的 `name`
4. IF 认证方式无法识别或未声明, 导入器 SHALL 设置 `authType` 为 `NONE`

### Requirement 7：按标签分组

**User Story:** AS 使用者, I want 大规范的接口按标签分层, so that 集合页里看得清

#### Acceptance Criteria

1. WHEN 存在两个以上标签组, 导入器 SHALL 为每个标签组新建一个子集合
2. WHEN 只有一个标签组, 导入器生成的接口条目 SHALL 直接放入新建的集合
3. 未声明标签的操作所属标签组名 SHALL 为「未分类」

### Requirement 8：持久化

**User Story:** AS 使用者, I want 导入结果立刻出现在集合页, so that 我马上能用

#### Acceptance Criteria

1. WHEN 导入成功, 集合存储 SHALL 在当前项目根下新建一个集合
2. 新建集合的名称 SHALL 取规范的 `info.title`
3. WHEN 导入成功, 集合存储 SHALL 一次性写入全部接口条目并触发一次持久化
4. 导入生成的接口条目所属协议类型 SHALL 为 HTTP

### Requirement 9：导入反馈

**User Story:** AS 使用者, I want 知道导入结果, so that 我能判断成没成

#### Acceptance Criteria

1. WHEN 导入成功, 导入弹窗 SHALL 显示新建集合名与导入的接口条目数量
2. IF 导入失败, 导入弹窗 SHALL 显示失败原因
3. IF 导入失败, 导入弹窗 SHALL 保留使用者已输入的内容

### Requirement 10：从地址导入

**User Story:** AS 使用者, I want 直接给一个规范地址, so that 我不必先下载再粘贴

#### Acceptance Criteria

1. WHEN 使用者填写 http 或 https 地址并提交, 导入器 SHALL 请求该地址取得规范文本
2. WHEN 请求耗时超过 15 秒, 导入器 SHALL 返回请求失败错误
3. IF 地址请求失败, 导入器 SHALL 返回请求失败错误并保留已输入内容
4. WHILE 请求进行中, 导入弹窗 SHALL 禁止重复提交

### Requirement 11：解析器与序列化器

**User Story:** AS 使用者, I want 导入结果能被应用正确读写, so that 打开接口时状态完整恢复

#### Acceptance Criteria

1. 导入器使用的解析器为：JSON 解析器、YAML 解析器、OpenAPI 与 Swagger 结构提取器
2. 导入器使用的序列化器为：HTTP 快照序列化器
3. WHEN 生成请求体文本, 导入器 SHALL 输出美化缩进的 JSON
4. WHEN 一条接口条目生成完毕, 其快照经序列化后再反序列化 SHALL 得到等价结果
5. 每条被跳过的操作 SHALL 不影响其余操作的导入
