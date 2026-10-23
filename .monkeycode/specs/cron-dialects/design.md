# Cron 方言解析与生成

Feature Name: cron-dialects
Updated: 2026-10-23

## 设计

`CronParser` 整体重写，从 `java.util.Calendar` 迁到 `java.time`：`LocalDate` 按日判定是否命中，命中日再在时/分/秒集合上取最近时刻，避免逐秒遍历。

日/周语义被抽成两组策略接口：`DomSpec`（`All`、`Days`、`Last`、`NearestWeekday`）与 `DowSpec`（`All`、`Days`、`LastSaturday`、`Nth`、`DaysWithNoDomConstraint`）。`dayMatches` 决定 OR 语义（Linux/Spring）或 Quartz 的互斥校验，Quartz 的 `?` 在解析阶段就被记录为「通配且让位」。

各方言差异集中在 `layoutOf`：段数、字段顺序、周编号基准（Quartz 1=周日，解析后归一到 0-6）、是否允许年。别名、英文缩写、`L/W/#` 都在字段解析层处理，`parseValues` 统一支持 `*`、列表、区间、步进。

`CronBuilder` 负责反向：`fields(type)` 给字段顺序，`defaults(type)` 给初值（Quartz 的周默认 `?`），`preset(type, preset)` 给模板值并就地满足 Quartz 的 `?` 约束，`build` 拼接时年字段为空则省略。

`CronResult` 保持十个时间戳与逐行文本；搜索上限五十年，用于覆盖世纪非闰年附近的闰日计划。

界面 `CronToolScreen` 拆成解析/生成两段，方言用三段式标签切换，生成模式用模板下拉 + 逐字段输入。

## 验证

单测覆盖三方言的段数校验、别名、`L/W/#`、周编号基准、跨年与闰日十次结果、无解日期、模板生成结果。运行全量单测与 Release 构建。
