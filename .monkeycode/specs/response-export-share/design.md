# 响应体导出与分享

Feature Name: response-export-share
Updated: 2026-10-23

## 设计

**纯逻辑与平台分离**：`ResponseExport` 只做 MIME 归一、后缀推断、文件名生成与字节选择，全部是纯函数，可直接单测；`ResponseFileStore` 负责 SAF 写入、缓存落地与分享意图，依赖 Android。

**字节来源**：`HttpCallOutcome.Success.bodyBytes` 已经是完整响应字节（截断只发生在展示层的 `renderBody`），所以实时路径直接用原始字节；历史详情只存了文本，退化为 UTF-8 编码。`ResponseExport.bytesOf(text, rawBytes)` 把这条优先级固化下来，避免调用方各写一遍。

**UI**：新增 `ResponseExportActions` 挂在 `ResponseBodyView` 底部，显示完整字节数并给出保存/分享两个图标。保存用 `ActivityResultContracts.CreateDocument(mime)`，MIME 取自响应头；分享写入 `cache/exports/` 后经 `FileProvider` 生成 content URI，`stageForShare` 每次先清空目录避免缓存堆积。

**FileProvider**：新增 `res/xml/file_paths.xml` 只暴露 `cache-path exports/`，清单里注册 `androidx.core.content.FileProvider`，authority 为 `${applicationId}.fileprovider`。

**历史详情**：`ResponseBodyView` 增加可选 `contentType`，历史页从 `responseHeaders` 里按不区分大小写取 `Content-Type` 传入，导出后缀与媒体预览因此都能正确推断。

## 验证

单测覆盖 MIME 归一、后缀推断（已知/家族/未知）、文件名、原始字节优先与 UTF-8 回退。手工作业：实时响应保存为 JSON、分享到聊天工具；历史详情导出与实时内容一致。全量单测 311 项与 Release 构建。
