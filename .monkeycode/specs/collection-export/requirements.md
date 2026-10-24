# Requirements Document

## Introduction

集合只能导入，无法导出：换设备、交给同事或沉淀成文档时，只能逐条复制 cURL。需要把文件夹及其子树导出为通用格式。

## Glossary

- **System**: Pulse 集合页
- **导出范围**: 当前文件夹及其全部子文件夹
- **可导出请求**: 能解析的 HTTP 请求；WS / SSE / MQTT 不在两种格式的通用表达内

## Requirements

### Requirement 1: 导出入口

**User Story:** AS 用户, I want 一键导出当前集合, so that 我能分享或备份接口定义。

#### Acceptance Criteria

1. WHEN 用户打开当前文件夹的更多菜单，系统 SHALL 提供「导出集合」
2. WHEN 用户选择格式，系统 SHALL 弹出系统文件保存对话框
3. WHEN 用户选定位置，系统 SHALL 写入 JSON 文件并提示成功
4. WHEN 写入失败，系统 SHALL 提示换位置重试
5. WHEN 用户取消保存，系统 SHALL 不产生任何文件

### Requirement 2: 导出范围

**User Story:** AS 用户, I want 导出整个子树, so that 分组结构不丢。

#### Acceptance Criteria

1. WHEN 导出某个文件夹，系统 SHALL 包含其全部子文件夹与其中的请求
2. WHEN 请求不是 HTTP，系统 SHALL 跳过并计入跳过数
3. WHEN 快照损坏，系统 SHALL 跳过该条
4. WHEN 导出完成，系统 SHALL 提示实际导出数量

### Requirement 3: Postman 格式

**User Story:** AS 用户, I want 导出为 Postman Collection, so that 同事能直接导入。

#### Acceptance Criteria

1. WHEN 导出 Postman，系统 SHALL 输出 v2.1 schema 与集合名
2. WHEN 文件夹有变量，系统 SHALL 写入 `variable` 数组
3. WHEN 存在子文件夹，系统 SHALL 按嵌套 `item` 组织
4. WHEN 请求带 query，系统 SHALL 在 `url.raw` 拼接并在 `url.query` 列出
5. WHEN 请求体为 JSON/RAW/表单，系统 SHALL 分别写 raw / urlencoded / formdata
6. WHEN 请求带 Bearer/Basic/API Key，系统 SHALL 写入对应 `auth`
7. WHEN 请求带前后置脚本，系统 SHALL 写入 `event`

### Requirement 4: OpenAPI 格式

**User Story:** AS 用户, I want 导出为 OpenAPI, so that 能生成文档或对接网关。

#### Acceptance Criteria

1. WHEN 导出 OpenAPI，系统 SHALL 输出 3.0.3 文档与标题
2. WHEN 多个请求路径相同，系统 SHALL 合并到同一 path 下按方法区分
3. WHEN 请求带 query 与请求头，系统 SHALL 写入 `parameters`
4. WHEN 请求有请求体，系统 SHALL 按类型写入 `requestBody.content`
5. WHEN 方法+路径重复，系统 SHALL 只保留第一条
6. WHEN operationId 冲突，系统 SHALL 追加序号保证唯一

### Requirement 5: 文件名

**User Story:** AS 用户, I want 文件名可读, so that 我能分辨导出的集合。

#### Acceptance Criteria

1. WHEN 生成文件名，系统 SHALL 使用文件夹名并替换路径不安全字符
2. WHEN 文件夹名为空，系统 SHALL 使用 `collection`
3. WHEN 选择 Postman / OpenAPI，系统 SHALL 分别追加 `.postman.json` / `.openapi.json`
