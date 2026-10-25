# 技术设计：URL 解析器（#29）

## 方案

### 领域层

新文件 `domain/tools/UrlInspector.kt`：

- `QueryParam(name, value)` 保存原始文本，`decodedName` / `decodedValue` 按需解码。
- `ParsedUrl(scheme, userInfo, host, port, path, params, fragment, schemeInferred, error)`，附 `user` / `password` / `hasUserInfo` / `hasDuplicateParams` 派生属性与 `rebuild()`。
- `parse(raw)` 手写分段，不使用 `java.net.URI`：粘贴带空格的地址不会抛异常，且能覆盖 `mailto:` 这类无层级方案。

解析顺序：协议（`^[A-Za-z][A-Za-z0-9+.-]*:`，后随 `//` 才算层级）→ 锚点（首个 `#`）→ 查询串（首个 `?`）→ 路径（首个 `/`）→ 认证信息（最后一个 `@`）→ 主机与端口（IPv6 看方括号，否则按最后一个 `:` 且右侧为数字）。

错误编码：`empty` / `host` / `port` / `invalid`；端口越界与缺主机都会保留其余已解析字段，方便用户定位问题段。

`rebuild()` 只在端口显式存在时输出 `:port`，查询串空值参数输出为裸名称。

### 界面

新文件 `ui/tools/UrlParserScreen.kt`：单输入框 + 组成卡片（协议/用户名/密码/主机/端口/路径/锚点）+ 查询参数卡片（解码开关、空值与同名提示）+ 拼装结果（复制按钮与差异提示）。`FieldRow` 与 `Hint` 为本文件私有组件。

新增路由 `PulseRoute.UrlParserTool`、首页工具箱入口（图标 `Link`）与三语 25 条文案。

## 测试

`UrlInspectorTest` 19 项：完整地址、最简地址、缺协议补全、IPv6（带/不带端口、未闭合）、无层级方案、端口越界与非数字、缺主机、空输入、参数顺序与重复、解码（含 `+` 与中文）、非法百分号、锚点无查询、无密码认证、无损往返、协议小写化、路径含空格。
