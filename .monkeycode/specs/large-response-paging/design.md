# 技术设计：大响应虚拟化（#33）

## 方案

### 领域层

新文件 `domain/text/BodyPager.kt`：

- `BodyPages(ranges, totalChars, totalLines)`：区间列表 + 总字符 + 总行数，`pageCount` / `lastPageIndex` / `slice(text, page)`。区间连续覆盖 `[0, totalChars)`，越界页码钳到首尾。
- `BodyPager.paginate(text, charsPerPage = 12000)`：O(n) 单遍切页。断点优先落在预算末段（后半段）的换行处；找不到换行则硬切；硬切位置若切在代理对中间则回退一位。单页字符预算下限校验为 2，非法值抛 `IllegalArgumentException`。
- 纯函数、无 Android 依赖，可直接单测。

### 界面

新文件 `ui/common/PagedCodeBlock.kt`：

- `PagedTextBlock(text, charsPerPage)`：分页栏 + `MonospaceBlock`（`selectable = true`），栏在正文上方，翻页后视线落在新内容开头。
- `PagedHexBlock(bytes, hint)`：按 `HEX_BYTES_PER_PAGE = 1024` 字节出页，直接调用 `hexDumpRange(bytes, offset)`，不为几 MB 内容拼出十几 MB 字符串。
- `PagerBar`：上一页 / `第 x / y 页` / 下一页，首尾置灰，下方一行「共 N 行 · M 页」。

`ui/common/CodeBlock.kt` 调整：

- 删除 `MAX_TEXT_CHARS` / `clipText` / `RenderedBody` / `renderBody`，改为 `renderModeText(body, mode)` 返回完整文本。
- `hexDump` 改为 `hexDumpRange(bytes, offset, byteCount)`，页大小常量导出为 `HEX_BYTES_PER_PAGE`。

`ui/common/ResponseBodyView.kt`：

- RAW / JSON（非法时）/ XML / BASE64 → `PagedTextBlock`。
- HEX → `PagedHexBlock`，优先用原始 `bodyBytes`，没有时用正文 UTF-8 字节。
- 移除原先 64K 截断提示。

`ui/common/JsonTreeView.kt`：

- 解析失败 → `PagedTextBlock`。
- 长度 > `MAX_TREE_CHARS = 60000` → 提示后展示 `PagedTextBlock(prettyBody(...))`。

历史落库仍按 16K 字符裁剪（`HISTORY_BODY_CHAR_CAP`），那是存储成本控制，与展示层无关；对应提示保留。

## 测试

`BodyPagerTest` 14 项：空文本、短文本、恰好一页、超一字符、每页不超预算、优先换行断开、换行过早时行内切、不拆代理对、区间连续且全覆盖、越界钳制、总行数、总字符数、超长单行、非法预算。
