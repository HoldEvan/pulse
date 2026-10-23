# 正则提取

Feature Name: regex-extract
Updated: 2026-10-23

## 设计

新增 `domain/tools/RegexExtract.kt`，用枚举 `ExtractPreset` 承载规则本身：每个枚举项带 `pattern` 与可选 `trimTrailing`，正则即唯一事实来源，界面只维护名称映射，新增规则只需加一枚举项与一句文案。

`RegexExtractor.extract` 是纯函数：编译规则、`findAll`、按 `trimTrailing` 去尾、可选 `distinct()`。规则编译失败时通过 `ExtractResult.patternError` 返回错误，内置规则正常不会触发该分支。

网址规则的字符类显式排除空白、引号、尖括号、CJK 与全角标点，避免中文排版下把后续汉字吞进网址；尾部标点在 `trimTrailing` 里二次兜底。

界面侧 `RegexToolScreen` 拆成 `MatchPane` 与 `ExtractPane`，共享页级 `pattern`/`text` 状态，切换标签不清空输入。

## 验证

单测覆盖各预设的正例与误报边界、去重顺序、网址去尾标点与 CJK 截断。运行全量单测与 Release 构建。
