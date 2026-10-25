# 需求：YAML 与 JSON 互转（#27）

## 背景

路线图 P1 #27。规范导入链路已经能读 YAML，但工具箱里没有独立转换入口；接口调试时经常需要把 YAML 配置贴进 JSON 工具，或把响应 JSON 交给只吃 YAML 的流水线。

## EARS 需求

### 转换入口

- WHEN 用户进入工具页，THE 系统 SHALL 提供 YAML → JSON 与 JSON → YAML 两个方向。
- WHEN 用户输入内容，THE 系统 SHALL 实时转换并在结果区展示输出，无需点击按钮。
- WHEN 用户切换方向，THE 系统 SHALL 保留已输入内容并按新方向重新转换。
- WHEN 结果非空，THE 系统 SHALL 提供一键复制。

### YAML → JSON

- WHEN YAML 使用未加引号的 `true` / `false` / 整数 / 浮点 / `null` / `~`，THE 系统 SHALL 输出对应类型的 JSON 值。
- WHEN YAML 标量加了引号（如 `"123"`、`"true"`），THE 系统 SHALL 输出 JSON 字符串，不做类型推断。
- WHEN YAML 根节点是映射、序列或标量，THE 系统 SHALL 都接受并输出对应 JSON 结构。
- WHEN YAML 使用锚点与别名，THE 系统 SHALL 展开为等价结构。
- WHEN YAML 整数使用 `0x` / `0o` / `0b` 前缀，THE 系统 SHALL 按对应进制输出十进制数字。
- WHEN YAML 语法错误，THE 系统 SHALL 提示无法解析 YAML，不输出半截结果。
- WHEN YAML 节点形成环，THE 系统 SHALL 停止转换并提示嵌套过深。

### JSON → YAML

- WHEN JSON 是对象，THE 系统 SHALL 输出块式映射，两级缩进为两个空格。
- WHEN JSON 是数组，THE 系统 SHALL 输出块式序列；数组元素为对象时，`-` 独占一行，其字段缩进一级。
- WHEN JSON 是空对象或空数组，THE 系统 SHALL 输出行内 `{}` 与 `[]`。
- WHEN 字符串会被 YAML 误读为布尔、数字、null，或以 `-`、`#` 等特殊字符开头，THE 系统 SHALL 用双引号包裹。
- WHEN 字符串包含换行、制表符或双引号，THE 系统 SHALL 使用转义写法。
- WHEN JSON 语法错误，THE 系统 SHALL 提示无法解析 JSON。
- WHEN 嵌套超过 128 层，THE 系统 SHALL 提示嵌套过深。

### 边界

- WHEN 输入为空或全为空白，THE 系统 SHALL 提示输入为空。
- WHEN 两个方向往返转换一次，THE 系统 SHALL 保持结构等价（标量类型不丢失）。

## 非目标

- YAML 合并键 `<<` 的语义展开。
- 自定义标签、二进制标签、时间戳类型的特殊映射。
- 多文档流（`---` 分隔）的批量转换。
