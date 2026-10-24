# Requirements Document

## Introduction

应用已能把请求导出为 cURL，反向从 cURL 还原请求尚未支持。开发者从浏览器、文档或聊天记录里拿到 curl 命令时，只能手工逐项填写。

## Glossary

- **System**: Pulse 集合页
- **cURL 命令**: 形如 `curl -X POST 'https://…' -H '…' -d '…'` 的命令行
- **快照（Snapshot）**: 请求的可恢复配置，导入后直接写入集合

## Requirements

### Requirement 1: 导入入口

**User Story:** AS 开发者, I want 粘贴 curl 命令直接生成请求, so that 我不必逐项手填。

#### Acceptance Criteria

1. WHEN 用户打开集合页的更多菜单，系统 SHALL 提供「从 cURL 导入」
2. WHEN 用户确认导入，系统 SHALL 把请求写入当前文件夹
3. WHEN 没有当前文件夹，系统 SHALL 写入项目的默认集合
4. WHEN 导入成功，系统 SHALL 提示并关闭对话框
5. WHEN 解析失败，系统 SHALL 在对话框内显示原因且不写入任何数据

### Requirement 2: 参数覆盖

**User Story:** AS 开发者, I want 常用 curl 参数都能还原, so that 复制来的命令可直接用。

#### Acceptance Criteria

1. WHEN 命令包含 `-X` 或 `--request`，系统 SHALL 使用指定方法
2. WHEN 命令包含 `-H` 或 `--header`，系统 SHALL 逐条还原请求头
3. WHEN 命令包含 `-d`、`--data`、`--data-raw`、`--data-binary`，系统 SHALL 还原请求体；多个 `-d` 按 `&` 拼接
4. WHEN 命令包含 `--data-urlencode`，系统 SHALL 还原为 form-urlencoded 请求体
5. WHEN 命令包含 `-F` 或 `--form`，系统 SHALL 还原为 form-data；`name=@path` 记为文件字段
6. WHEN 命令包含 `-u user:pass`，系统 SHALL 还原为 Basic 认证
7. WHEN 命令包含 `-b` 且值含 `=`，系统 SHALL 还原为 Cookie 请求头
8. WHEN 命令包含 `-A`、`-e`、`--url`，系统 SHALL 分别还原 User-Agent、Referer 与地址
9. WHEN 命令包含 `-G`，系统 SHALL 把数据拼到查询参数并把方法设为 GET
10. WHEN 命令包含 `-I`，系统 SHALL 把方法设为 HEAD

### Requirement 3: 地址与请求体推断

**User Story:** AS 开发者, I want 导入后的请求可直接编辑, so that 我能继续调整。

#### Acceptance Criteria

1. WHEN 地址带 query，系统 SHALL 拆成参数表，地址只保留基础部分
2. WHEN 未指定方法且存在请求体或表单，系统 SHALL 推断为 POST
3. WHEN 未指定方法且无请求体，系统 SHALL 推断为 GET
4. WHEN 请求头声明 JSON 或请求体以 `{`/`[` 开头，系统 SHALL 使用 JSON 类型，否则用 RAW
5. WHEN 生成名称，系统 SHALL 取路径最后一段，没有路径时用主机名

### Requirement 4: 安全与重定向

**User Story:** AS 开发者, I want 导入时保留安全相关选项, so that 行为与命令行一致。

#### Acceptance Criteria

1. WHEN 命令包含 `-L`，系统 SHALL 打开跟随重定向；未包含时 SHALL 保持关闭（与 curl 默认一致）
2. WHEN 命令包含 `--max-redirs N`，系统 SHALL 设置最大跳数并限制在 0–20
3. WHEN 命令包含 `-k`，系统 SHALL 打开跳过证书校验
4. WHEN 命令包含引号、转义与反斜杠续行，系统 SHALL 正确拆分参数
5. WHEN 命令不是以 curl 开头或没有地址，系统 SHALL 拒绝并提示
