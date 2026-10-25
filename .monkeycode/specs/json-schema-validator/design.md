# 技术设计：JSON Schema 校验（#26）

## 方案

### 1. 领域层

新文件 `domain/tools/JsonSchemaValidator.kt`，纯 JVM 逻辑，不依赖 Android：

- `SchemaViolation(path, keyword, expected, actual)`：`keyword` 是稳定语义编码，界面据此选择文案；`expected`/`actual` 已是可展示字符串。
- `SchemaReport(violations, schemaError, truncated)`：`schemaError` 非空表示 Schema 不可求值（`schemaInvalid` / `schemaType` / `dataInvalid` / `refNotFound` / `pattern` / `depth`）。
- `validate(schemaText, instanceText)` 负责文本解析并区分两侧错误；`validate(schema, instance)` 是纯树遍历入口。

遍历实现要点：

- 先解析 `$ref`（支持 `#`、`#/a/b`、`~0`/`~1` 转义与数组下标），解析失败即终止整次校验。
- `type` 校验失败后直接返回，避免后续关键字对错误类型产生噪声。
- 组合关键字用临时 `Context` 试跑分支，`anyOf` 任一通过即视为通过，`oneOf` 要求恰好一个，`not` 要求零个。
- `Context` 持有根文档、失败列表、截断标记与致命错误；达到 `MAX_VIOLATIONS = 100` 后置 `truncated` 并停止遍历。
- 递归深度上限 64，超过归入 `depth` 错误，防止自引用 Schema 栈溢出。
- 数字比较统一走 `Double`，`integer` 用 `abs(v - round(v)) < 1e-9` 判断；`multipleOf` 同样用容差比较。

### 2. 界面

新文件 `ui/tools/JsonSchemaScreen.kt`：

- 两个多行输入框（Schema / 待校验 JSON），输入变化即清空结果。
- 校验按钮在有输入时可用；「载入示例」填入一份会触发 4 类错误的样例。
- 结果区四态：未校验提示、Schema 错误、校验通过、失败列表。
- 每条失败用 `AppCard` 展示路径（主题色）与本地化句子；截断时追加提示。

### 3. 接入

- `Nav.kt` 新增 `PulseRoute.JsonSchemaTool`。
- `PulseApp.kt` 注册二级页与首页回调。
- `DashboardScreen.kt` 工具箱列表在 JSON 格式化之后插入新入口，图标用 `MiuixIcons.Light.Ok`。
- 三语 `strings.xml` 新增 40 条文案：入口 2 条、标题与表单 5 条、状态与错误 8 条、关键字句子 25 条。

## 测试

`JsonSchemaValidatorTest` 26 项，覆盖：通过场景、根/嵌套类型错误、类型错误短路、required 路径、非标识符键的方括号路径、enum/const、additionalProperties 两种形态、长度与 pattern（含非法正则）、items 单/元组、数组边界与唯一性、数值边界与 multipleOf、integer 与 number 差异、type 数组、anyOf/oneOf/not/allOf、本地 $ref 与解析失败、两侧 JSON 解析失败、失败条数截断。
