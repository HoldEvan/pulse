# 时间格式化

Feature Name: timestamp-format
Updated: 2026-10-23

## 设计

`TimestampToolScreen` 的方向枚举从两项扩展为 `TS_TO_DATE`、`DATE_TO_TS`、`FORMAT`，用 `AppTabRow` 三段式切换；切换时清空 `input`，格式化方向的输入与模式串分别用独立状态，避免与其他方向互相污染。

格式化分支 `TimeFormatSection` 的推导全在渲染时完成：`patternFormatter(pattern)` 编译模式串，非法返回 null；`parseTimeInput(input, pattern)` 先用同一模式串尝试 `LocalDateTime` 再试 `LocalDate`，失败回退到既有的三种日期写法。三者都不成立时按「格式非法 / 时间非法 / 空输入」分档提示，错误优先级与展示区一一对应。

结果区复用 `TimestampFieldRow` + `rememberCopyToClipboard`，与另外两个方向保持一致的复制交互。页尾用 `formatLegendTokens` 渲染模式字母说明，token 与文案资源成对维护。

## 验证

手工核对三条方向的正常与非法输入；单元测试侧沿用既有日期解析测试。运行全量单测与 Release 构建。
