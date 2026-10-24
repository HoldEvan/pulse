# 集合导出

Feature Name: collection-export
Updated: 2026-10-23

## 设计

**纯函数导出器**：`CollectionExporter.export(root, folders, requests, variables, format)` 只依赖数据模型，返回文本与「导出/跳过」计数，便于单测。范围按 `CollectionTree.descendantsOf(root) + root` 计算；WS/SSE/MQTT 与损坏快照统一跳过并计数——两种目标格式都没有通用表达，硬塞会让导入方解析失败。

**Postman**：输出 v2.1 schema，`variable` 来自当前文件夹的合并变量；`item` 递归按父子关系生成。请求侧映射 method、headers、url（raw + protocol/host/path/query，地址含 `{{var}}` 时只保证 raw）、body（raw/urlencoded/formdata）、auth（bearer/basic/apikey）与 pre/test 事件。

**OpenAPI**：3.0.3 文档，按「方法 + 路径」去重后归入 `paths`；query 与请求头写入 `parameters`，请求体按类型映射到 `requestBody.content`，响应固定 `200 OK`。operationId 由方法名与请求名生成，冲突时追加序号。

**界面**：集合菜单新增「导出集合」，对话框提供两种格式，选择后立即生成文本并用 `CreateDocument("application/json")` 交给 SAF；文本暂存到用户选定位置再写入，取消不落盘，写入成功/失败均用 Toast 反馈。

## 验证

单测覆盖 Postman 结构（文件夹嵌套、变量、请求方法/URL/请求体/鉴权、跳过计数）、OpenAPI 路径合并与参数、文件名清洗（3 项）。手工验证导出的 Postman 文件能重新导入本应用、OpenAPI 文件可被常见查看器打开。全量单测 361 项与 Release 构建。
