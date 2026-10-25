# 技术设计：YAML 与 JSON 互转（#27）

## 关键取舍

kaml 的节点树（现有规范导入使用）在解析后会丢掉标量引号信息：`"123"` 与 `123` 都变成 `content = "123"`，无法区分字符串与数字。互转工具对这类差异敏感，因此 YAML → JSON 改用 snakeyaml-engine 的低层 `ComposeString`：它返回带类型标签与引号样式的原始节点树。

`snakeyaml-engine-kmp` 原本是 kaml 的传递依赖，这里显式声明 `it.krzeminski:snakeyaml-engine-kmp`，避免上游改依赖后编译失败。

## 实现

新文件 `domain/tools/YamlJsonTool.kt`：

- `Result(text, error)`：`error` 为 `empty` / `yamlInvalid` / `jsonInvalid` / `depth`，界面据此选文案。
- `yamlToJson(raw)`：`ComposeString(LoadSettingsBuilder().build()).composeString(raw)` 得到节点树，递归转换：
  - `MappingNode` → `JsonObject`，键取标量值；`SequenceNode` → `JsonArray`；`ScalarNode` 按 `tag` 分派（`NULL` / `BOOL` / `INT` / `FLOAT` / 其他一律字符串）。
  - 整数先按十进制解析，失败再按 `0x` / `0o` / `0b` 前缀换算，仍失败则保留字符串。
  - 递归路径上用 identity 集合检测锚点成环；深度上限 128。
- `jsonToYaml(raw)`：手写块式发射器：
  - 对象逐键输出 `key:`，标量与空容器行内输出，嵌套走下一级缩进 2 空格。
  - 数组标量输出 `- value`，对象或非空数组输出 `-` 独占一行再缩进一级。
  - 字符串先过 `isPlainSafe`：首字符落在 YAML 指示符集合、含 `: ` 或 ` #`、内容为 `null/true/false/yes/no/on/off/y/n`、或匹配数字正则时加双引号，并按 `\\ \" \n \r \t` 转义。
- JSON 输出沿用 `prettyPrint` + 两空格缩进，与工具箱其他 JSON 输出一致。

## 界面

新文件 `ui/tools/YamlJsonScreen.kt`：方向 `AppTabRow` + 单一多行输入 + 结果块（`MonospaceBlock` 与 `CopyIconButton`），错误态用主题错误色。新增路由 `PulseRoute.YamlJsonTool`、首页工具箱入口（图标 `ConvertFile`）与三语 15 条文案。

## 测试

`YamlJsonToolTest` 20 项：标量类型、引号字符串、嵌套映射、映射数组、根数组/标量、锚点别名、非法 YAML、空输入、块映射输出、缩进、对象数组、歧义字符串引用、换行转义、空容器、根数组输出、根标量输出、往返等价、非法 JSON、深度上限。
