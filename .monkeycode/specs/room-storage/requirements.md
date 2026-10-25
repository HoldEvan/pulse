# 需求：存储迁移 Room（#31）

## 背景

路线图 P2 #31。此前所有运行时数据（15 个 Store）共享一个 `app_data.json`：任何一次改动都会把全部模块重新序列化、逐字段加密并整文件重写。历史上限 500 条、单条正文最大 16KB，拨测记录 2000 条，写放大随数据量线性上升；也无法做分页与部分更新，新增模块必须改同一个巨型 `save()`。

## EARS 需求

### 存储

- WHEN 保存任一模块的改动，THE 系统 SHALL 只重写该模块对应的表，不触碰其他模块。
- WHEN 保存一组数据，THE 系统 SHALL 在同一事务内完成该模块的删除与写入，避免读到中间态。
- WHEN 应用启动，THE 系统 SHALL 从 Room 恢复全部模块。
- WHEN 新增一个数据模块，THE 系统 SHALL 只需新增表、DAO 与映射，无需修改其他模块的读写代码。
- WHEN 列表顺序有意义（集合、接口、文件夹变量、MQTT 订阅、WS 模板），THE 系统 SHALL 用显式位置列保存顺序，不依赖插入顺序。

### 迁移与兼容

- WHEN 首次启动且数据库尚无初始化标记，THE 系统 SHALL 尝试导入旧的 `app_data.json`。
- WHEN 导入成功，THE 系统 SHALL 保留旧文件，不删除，便于回滚。
- WHEN 旧文件不存在，THE 系统 SHALL 直接落库默认种子数据并写入初始化标记。
- WHEN 数据库已有初始化标记，THE 系统 SHALL 只从数据库恢复，不再读取旧文件。
- WHEN 旧版 App 读取新版本导出的备份，THE 系统 SHALL 保持 JSON 结构兼容。

### 备份

- WHEN 导出本地备份或 WebDAV 备份，THE 系统 SHALL 从当前状态生成与旧版结构一致的 JSON。
- WHEN 从本地或 WebDAV 恢复，THE 系统 SHALL 解析 JSON 写回各 Store 后再整体落库。
- WHEN 备份恢复完成，THE 系统 SHALL 保证重启后读到的是恢复后的数据。

### 安全

- WHEN 敏感字段写入实体，THE 系统 SHALL 先加密，表中不得出现明文。
- WHEN 从实体读回敏感字段，THE 系统 SHALL 解密后再交给 Store。
- WHEN 设备无可用密钥，THE 系统 SHALL 降级为明文读写，且不影响功能可用性。

### 可测试性

- WHEN 在 JVM 单测中验证映射，THE 系统 SHALL 允许替换加解密实现，不依赖 Android Keystore。
- WHEN 领域模型与实体互转，THE 系统 SHALL 保证往返后字段完全一致。

## 非目标

- 数据库加密（SQLCipher）与备份文件整体加密。
- 跨进程并发写入与多实例访问。
- 历史与拨测记录的分页查询接口（表结构已支持，查询层留待后续）。
