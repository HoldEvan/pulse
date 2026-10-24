# 请求复制

Feature Name: request-duplicate
Updated: 2026-10-23

## 设计

复制逻辑放在 `CollectionStore.duplicateRequest(id, name)`：查找原条目，按新 id 与当前时间复制快照、协议类型与文件夹，再插入到原条目之后（`add(index + 1, copy)`），最后落盘。条目不存在或名称为空返回 null 且不写盘，避免留下空副本。

名称由界面层传入：`CollectionStore` 不依赖 Android 资源，语言切换由调用方用 `stringResource(R.string.request_copy_name, name)` 计算，英文与繁体各自得到本地化的后缀。为避免在点击回调里读资源，`RequestRow` 在组合期把 `copyName` 算好再传给 `onDuplicate`。

界面入口是接口行更多菜单的第二项（改名之后、移动之前），用 Material 的 `ContentCopy` 图标，点击即完成复制，不再弹确认——复制是低风险且可删除的操作。

## 验证

单测覆盖：副本紧跟原条目、快照与文件夹一致、条目不存在返回 null 且不落盘、空白名称拒绝（3 项）。手工验证三语命名与复制后立即加载。全量单测 350 项与 Release 构建。
