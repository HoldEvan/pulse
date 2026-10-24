# cURL 导入

Feature Name: curl-import
Updated: 2026-10-23

## 设计

**解析与界面分离**：`CurlImporter` 是纯 JVM 代码，输出 `HttpSnapshot`，因此可直接单测，也不依赖 Android 的文件与界面。

**分词**：先实现一个够用的 shell 分词器：单引号内原样保留、双引号内处理 `\n \t \r \" \\`、反斜杠续行吞掉换行、未加引号的反斜杠转义下一个字符。不做变量展开与命令替换——粘贴来的 curl 命令几乎不会依赖它们。

**参数**：按集合分类识别 `-X/-H/-d/--data-urlencode/-F/-u/-b/-A/-e/--url/--max-redirs` 与开关 `-G/-I/-L/-k`，其余未知开关忽略；第一个非开关 token 视为地址。`-b` 只有含 `=` 且不以 `@` 开头才当 Cookie 值，避免把 cookie 文件路径写进请求头。

**推断**：无 `-X` 时，有请求体或表单即 POST，否则 GET；`-G` 把数据搬到查询参数并强制 GET。请求体类型按 Content-Type 与首字符判断 JSON/RAW，`--data-urlencode` 归为 form-urlencoded，`-F` 归为 form-data。

**地址**：`splitQuery` 把 query 拆成参数表（URL 解码），地址只保留 `scheme://authority/path`，导入后参数可在界面继续编辑。

**安全项**：`-L` 打开跟随（curl 默认不跟随），`--max-redirs` 限制 0–20，`-k` 打开跳过证书校验，与上一版新增的请求选项对齐。

**界面**：集合页更多菜单新增「从 cURL 导入」，弹出对话框：多行输入、解析失败就地提示、成功写入当前文件夹（无当前文件夹时落到默认集合）并弹 Toast。写入复用 `CollectionsViewModel.saveRequest`，因此新建条目与集合覆盖机制一致。

## 验证

单测覆盖 GET、方法/请求头/JSON 体、query 拆分、POST 推断、表单与 Basic、urlencode、`-L/-k/--max-redirs`、`-G`、Cookie、分词（引号与续行）、空输入与非 curl 命令、缺地址（13 项）。手工验证：从 Chrome 复制 cURL 导入后直接发送。全量单测 347 项与 Release 构建。
