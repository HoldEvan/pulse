# 需求：JSON Schema 校验（#26）

## 背景

路线图 P1 #26。工具箱已有 JSON 格式化、JSONPath 取值与树形查看，缺少「这份 JSON 是否符合约定结构」的判断能力；调试接口返回时只能靠肉眼比对字段。

## EARS 需求

### 校验入口

- WHEN 用户同时填入 Schema 与待校验 JSON 并点击校验，THE 系统 SHALL 输出校验结论与全部不匹配项。
- WHEN 任一输入为空，THE 系统 SHALL 禁用校验按钮。
- WHEN 用户修改任一输入，THE 系统 SHALL 清空上一次的校验结果，避免展示过期结论。
- WHEN 用户点击「载入示例」，THE 系统 SHALL 填入一份带多处不匹配的示例，便于快速理解输出。

### 结构校验

- WHEN Schema 声明 `type`，THE 系统 SHALL 按 object/array/string/number/integer/boolean/null 判断，`integer` 接受整数值的浮点写法。
- WHEN 实例类型不符，THE 系统 SHALL 只报告该节点的类型错误，不再求值该节点的其余关键字。
- WHEN Schema 声明 `required`，THE 系统 SHALL 为每个缺失字段各报告一条，路径指向缺失字段本身。
- WHEN Schema 声明 `properties`，THE 系统 SHALL 递归校验已出现的字段。
- WHEN `additionalProperties` 为 false，THE 系统 SHALL 报告未声明的字段；为对象时，THE 系统 SHALL 用该子 Schema 校验未声明字段。
- WHEN Schema 声明 `items`（单 Schema 或元组），THE 系统 SHALL 逐元素校验，路径带下标。
- WHEN Schema 声明 `minItems` / `maxItems` / `uniqueItems`，THE 系统 SHALL 校验数组长度与元素唯一性。

### 取值校验

- WHEN Schema 声明 `enum` 或 `const`，THE 系统 SHALL 比对取值并给出期望值。
- WHEN Schema 声明 `minimum` / `maximum` / `exclusiveMinimum` / `exclusiveMaximum` / `multipleOf`，THE 系统 SHALL 仅在实例为数字时校验。
- WHEN Schema 声明 `minLength` / `maxLength` / `pattern`，THE 系统 SHALL 仅在实例为字符串时校验。

### 组合与引用

- WHEN Schema 声明 `allOf`，THE 系统 SHALL 汇总每个分支的失败。
- WHEN Schema 声明 `anyOf` 或 `oneOf`，THE 系统 SHALL 用通过分支数判断，并报告实际通过数。
- WHEN Schema 声明 `not`，THE 系统 SHALL 在实例满足排除规则时报告失败。
- WHEN Schema 使用 `$ref` 指向 `#/definitions/...` 或 `#/$defs/...`，THE 系统 SHALL 在根文档内解析并递归校验。
- WHEN `$ref` 无法解析，THE 系统 SHALL 报告 Schema 不可用，不产出逐条失败。

### 输出与边界

- WHEN 校验失败，THE 系统 SHALL 为每条失败展示 JSON 路径、关键字语义与期望/实际值。
- WHEN 路径中的键不是合法标识符，THE 系统 SHALL 使用方括号写法。
- WHEN 失败条数超过上限，THE 系统 SHALL 只保留前 100 条并提示已截断。
- WHEN Schema 或实例不是合法 JSON，THE 系统 SHALL 分别提示具体是哪一侧解析失败。
- WHEN Schema 顶层不是对象，THE 系统 SHALL 提示顶层必须为对象。

## 非目标

- 远程 `$ref`、`format` 语义校验、`patternProperties`、`dependencies`。
- 编辑 Schema 的辅助提示与自动补全。
