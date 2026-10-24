# 重定向控制

Feature Name: redirect-control
Updated: 2026-10-23

## 设计

OkHttp 只能开关自动跟随，没有跳数上限，也拿不到中间 3xx。因此引擎统一关闭 OkHttp 自身的跟随（`followRedirects(false)` / `followSslRedirects(false)`），在 `OkHttpEngine.execute` 里显式循环。

**决策逻辑独立成纯函数**：`RedirectPolicy.next(currentUrl, location, method, status)` 返回下一跳地址、应使用的方法、是否丢弃请求体、是否丢弃 Authorization。相对地址用 `URI.resolve` 解析；303 与 POST 的 301/302 降级为 GET；跨源（协议/主机/端口任一不同）丢弃 Authorization，避免把凭据带给第三方。

**循环细节**：查询参数只拼在首跳，重定向地址自带完整 query；每跳重新构造请求体，避免 multipart 流不可重放；每跳读取并释放响应体，防止连接泄漏；时间线按每一跳的 URL 注册，结束后统一清理。达到跳数上限或 Location 不可用时，把当前 3xx 作为最终结果返回，用户能看到 Location 与状态码。

**配置**：`HttpCallConfig` 与 `HttpSnapshot` 增加 `followRedirects`、`maxRedirects`，默认跟随 10 跳；界面在网络标签加开关与跳数输入，关闭时隐藏输入；集合运行器沿用快照设置（同时补上了此前遗漏的 `insecureTls`）。

## 验证

单测覆盖 3xx 判定、相对地址解析、303/301/302/307/308 的方法与请求体策略、跨源丢弃 Authorization、非法地址拒绝（7 项）。手工验证：对 httpbin 的 302 链路分别开关跟随、把跳数设为 1 观察中间响应。全量单测 334 项与 Release 构建。
