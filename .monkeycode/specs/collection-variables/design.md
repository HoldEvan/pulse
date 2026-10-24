# 文件夹（集合）变量

Feature Name: collection-variables
Updated: 2026-10-23

## 设计

**存储**：变量按文件夹 id 存在 `CollectionStore` 的 `Map<Long, List<KeyValueEntry>>` 里，与集合、请求同属一份快照，因此备份、恢复、删除文件夹都能自然覆盖。`deleteRecursive` 会清掉被删子树的变量；`restore` 只保留仍存在文件夹的键，避免恢复出悬空配置。

**继承**：`CollectionTree.idPathOf` 返回根到自身的 id 链，`resolvedVars` 沿链合并，后者覆盖前者，只取启用且键非空的条目。这样父级放公共 host、子级覆盖 token 即可。

**解析优先级**：`ServiceLocator.varsForCollection(collectionId)` 先取活动环境，再叠加文件夹变量。HTTP 页与 WS/SSE/MQTT 页在从集合加载时记录 `collectionId`，保存新请求后按条目回查文件夹，因此「加载 → 改 → 保存」始终用对作用域。

**脚本**：`ScriptSession` 增加可选的 `collectionScope`，`buildPm` 据此暴露 `pm.collectionVariables`。该作用域的查找只读当前文件夹自身变量，写入走 `CollectionVarWriter`（只覆盖自身，不把继承值复制进来），与 Postman 的 collectionVariables 语义一致。HTTP 页与集合运行器都会传入；运行器按每条请求的文件夹构造作用域。

**界面**：集合页更多菜单新增「变量」，对话框复用 `KeyValueEditor`，保存时过滤空键，全部清空即删除记录。

## 验证

单测覆盖继承覆盖、禁用与空键过滤、删除文件夹清理、快照往返（4 项）。手工验证：父级 host + 子级 token 的请求能解析、脚本 `pm.collectionVariables.set` 后同名占位符在后续请求生效。全量单测 354 项与 Release 构建。
