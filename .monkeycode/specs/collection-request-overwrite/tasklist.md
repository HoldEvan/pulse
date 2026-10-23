# 需求实施计划

- [x] 1. 存储层支持原地覆盖（需求 1.2-1.4、3.1-3.3）
  - `saveRequest` 返回新建条目 id
  - 新增 `updateRequest(id, data)`，条目不存在返回 false
  - 为命中与未命中分支添加单元测试
- [x] 2. 绑定关系抽成共用类（需求 1.1、2.1-2.2）
  - 新增 `LoadedRequestTracker`：`track` 与 `saveOver`
  - 覆盖失败时清空绑定并回退保存对话框
- [x] 3. 四个协议页接入（需求 4.1-4.2）
  - HTTP / WS / SSE / MQTT ViewModel 各持有 tracker
  - 保存按钮改为先覆盖后弹窗，保存对话框新建成功回传 id
- [x] 4. 检查点 - 确保所有测试通过
  - 运行 Debug 单元测试并修复回归
  - 构建 Release APK 并核验版本与签名
