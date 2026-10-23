# Requirements Document

## Introduction

正则工具新增「提取」用法：用内置规则从任意文本中批量抓取常见结构化内容（网址、手机号、邮箱、IP 等），与现有「匹配」用法并存于同一二级页的两个标签下。

## Glossary

- **System**: 正则工具（Regex Tool）
- **提取预设（Extract Preset）**: 内置的一条命名正则规则，对应一类常见内容
- **匹配模式（Match Mode）**: 现有用法，用户自填正则并查看整体匹配与捕获组
- **提取结果（Extract Result）**: 预设规则在输入文本上命中的字符串列表

## Requirements

### Requirement 1: 双标签与内容隔离

**User Story:** AS 开发者, I want 匹配与提取共用一个输入框, so that 我已经粘贴的文本不需要在两个标签间重复输入。

#### Acceptance Criteria

1. WHEN 用户进入正则工具二级页，系统 SHALL 展示「匹配」「提取」两个标签，默认选中「匹配」
2. WHEN 用户切换标签，系统 SHALL 保留已输入的正则与文本内容
3. WHEN 用户在「提取」标签修改文本，系统 SHALL 在切回「匹配」标签后使用同一份文本

### Requirement 2: 内置提取预设

**User Story:** AS 开发者, I want 一键选择常见内容类型, so that 我不需要为每次提取重写正则。

#### Acceptance Criteria

1. 系统 SHALL 提供网址、手机号、邮箱、IPv4、MAC、身份证号、日期、时间、UUID、十六进制颜色、数字、中文汉字共十二种预设
2. WHEN 用户选择一个预设，系统 SHALL 立即用该规则对输入文本做全局提取
3. WHEN 文本为空，系统 SHALL 显示空输入提示
4. WHEN 没有任何命中，系统 SHALL 显示「没有提取到内容」而非空列表

### Requirement 3: 结果展示与去重

**User Story:** AS 开发者, I want 结果按出现顺序展示并可去重, so that 我能快速扫读和复制。

#### Acceptance Criteria

1. WHEN 提取完成，系统 SHALL 按出现顺序展示全部命中项，并显示命中总数
2. WHILE「结果去重」开启，系统 SHALL 只保留每种内容首次出现的一项
3. WHEN 网址命中尾部带有标点，系统 SHALL 去除这些尾部标点后再计入结果
4. WHEN 中文正文紧邻网址，系统 SHALL 在网址后遇到汉字或全角标点时终止匹配
