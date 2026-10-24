# 自定义 HTTP 方法

Feature Name: custom-http-method
Updated: 2026-10-23

## 设计

**从枚举改值类**：`HttpMethod` 原为 `enum class { GET…HEAD }`，扩展到任意方法只有两条路：加 `CUSTOM` 枚举再带一个字符串字段（调用点到处判空），或改成值类。选后者：`@JvmInline value class HttpMethod(val value: String)`，伴生常量保留 `HttpMethod.GET` 等写法，`COMMON` 提供下拉顺序，`parse` 负责去空白、转大写与空串回落，`isValid` 只允许字母与连字符。

**连带改动**：所有 `method.name` 改为 `.value`（快照写入、导出器、运行器步骤、脚本桥接），`HttpMethod.entries.firstOrNull { … }` 改为 `parse`，`when (method)` 的 Ktor 映射改为 `KtorHttpMethod(method.value)`——Ktor 的 `HttpMethod` 构造器本身就接受任意字符串，因此自定义方法可直接透传。`ScriptModels.toCallConfig` 同样改用 `parse`，脚本里把 `pm.request.method` 设成 `PROPFIND` 也能生效。

**选择器**：新增共用组件 `MethodPicker`：下拉列出 `COMMON`，若当前方法不在其中则额外插入一条；末尾固定一条「自定义…」，点击弹出输入框，非法输入就地报错。HTTP 与 SSE 共用，替换原来的两个下拉。

**兼容**：快照里 `method` 本来就是字符串，旧数据按 `parse` 解析，大小写不敏感、空值回落 GET。

## 验证

单测覆盖解析与校验（4 项）、导出器输出自定义方法（1 项）。手工验证：选 PROPFIND 发送到支持 WebDAV 的地址、保存后从集合重新打开、集合运行器与脚本改写方法。全量单测 327 项与 Release 构建。
