# 需求实施计划

- [x] 1. 导出纯逻辑（需求 3.1-3.3、4.3）
  - `ResponseExport`：MIME 归一、后缀推断、文件名、字节选择
  - 补 7 项单测
- [x] 2. 平台落地（需求 1.1-1.5、2.1-2.4）
  - `ResponseFileStore`：SAF 写入、缓存落地、FileProvider 分享
  - `file_paths.xml` 与清单注册 FileProvider
- [x] 3. 界面接入（需求 4.1-4.2）
  - `ResponseExportActions` 挂在响应体底部，显示完整字节数
  - 保存走 CreateDocument，分享走系统面板并提示失败
  - 历史详情传 Content-Type 供后缀推断
- [x] 4. 三语文案
  - 保存/分享/失败提示与图标描述
- [x] 5. 检查点 - 确保所有测试通过
  - 全量单测 311 项
  - Release 打包并覆盖根目录 APK
