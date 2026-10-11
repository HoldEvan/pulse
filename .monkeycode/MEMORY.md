# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

### User Instruction Entry
User instruction entries should follow this format:

[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
Entries discovered by the Agent during task execution should follow this format:

[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment|Build Methods|Testing Methods|Troubleshooting & Debugging|Workflow & Collaboration|Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.
- When merging, update the context or date information.
- This helps avoid redundant entries and keeps the memory file tidy.

## Entries

[Project Knowledge Summary]
- Date: 2026-10-01
- Context: Discovered by Agent while making the settings page show a build timestamp that was 8 hours off from the user's clock
- Category: Environment Configuration
- Instructions:
  - 本打包机时区是 UTC（`/etc/localtime` -> `/usr/share/zoneinfo/Etc/UTC`），用户是 UTC+8。在 `build.gradle.kts` 里用 `SimpleDateFormat` 把构建时间格式化成字符串再注入 `buildConfigField`，等于把打包机的时区烘进产物，用户看到的时分会偏 8 小时
  - 正确做法：`buildConfigField("long", "BUILD_TIME_MS", "${millis}L")` 只存绝对毫秒，界面侧用 `SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(BUILD_TIME_MS))` 渲染，任何用户看到的都是自己时区的正确时间
  - 毫秒来源取 `rootProject.file("app/src/main").walkTopDown().filter { it.isFile }.maxOf { it.lastModified() }` 而不是 `System.currentTimeMillis()`：源码不变时值不变，连续打包不会只因时间戳变化就丢掉 Kotlin 增量编译结果
  - 改 `app/src/main` 下任何源码都会让 `BUILD_TIME_MS` 变，连带 Kotlin 全量重编（有意的取舍）
  - 验证方式：读生成物 `app/build/generated/source/buildConfig/{debug,release}/com/evan/pulse/BuildConfig.java`，用 python 按 UTC 与 UTC+8 分别 format 同一个毫秒值，两个结果相差 8 小时即正确

[Project Knowledge Summary]
- Date: 2026-09-28
- Context: Discovered by Agent while compiling Compose UI code against Compose BOM 2026.09.00 (animation 1.12.1, foundation 1.12.1)
- Category: Troubleshooting & Debugging
- Instructions:
  - 这套 Compose 版本有多处破坏性重命名，写手势/动画代码前先用 `javap -classpath <AAR解包出的classes.jar目录>` 核对签名，不要照搬旧文档写法
  - `foundation.gestures.detectHorizontalGestures` 已改名为 `detectHorizontalDragGestures`（旧名在 jar 中出现 0 次）
  - 该函数的参数 `onHorizontalDragStart` 已改名为 `onDragStart`，其余仍为 `onDragEnd`/`onDragCancel`/`onHorizontalDrag`；参数顺序为 start、end、cancel、drag
  - `onDragEnd`/`onDragCancel`/`onHorizontalDrag` 回调是非 suspend（`Function0`/`Function2`），回调体内不能直接用 `launch`，需先 `rememberCoroutineScope()` 再 `scope.launch { }`
  - `animation.core.animate(initialValue, targetValue, initialVelocity, animationSpec, onUpdate)` 现在返回 `Unit`，取不到 `.finalValue`，必须用尾随 lambda `{ value, velocity -> }` 在 `onUpdate` 里写状态
  - `Spring.DampingRatioNoBounce` 已改名为 `Spring.DampingRatioNoBouncy`
  - 查参数名的可靠办法：`javap -v -classpath . <类全名>` 后找目标方法的 `LocalVariableTable`，参数名就在那里
  - 确认 `Modifier.pointerInput` / `onSizeChanged` / `graphicsLayer { }` / `PointerInputChange.consume()` 在本版本中均存在
  - `androidx.compose.foundation.text.Text` 在本 BOM 里已被删除（`Unresolved reference 'Text'`），`foundation.text.selection.selectableText` Modifier 也删了，`material3.Text` 同样没有 `selectable` 参数。要「长按后拖动选择文本再复制」只能走 `AndroidView` 挂原生 `TextView` + `setTextIsSelectable(true)`（没有对应 getter，写成 Kotlin 属性 `isTextSelectable = true` 会报 `'val' cannot be reassigned`）；`Color.toArgb()` 的导入是 `androidx.compose.ui.graphics.toArgb`
  - `AndroidView` 的 `factory` / `update` 是普通 lambda，不是 @Composable 作用域：`MiuixTheme.colorScheme.onSurface` 这类 @Composable 读取必须在外面取好再传进去，否则报 `@Composable invocations can only happen from the context of a @Composable function`。`factory` 只执行一次，主题相关的属性（如文字颜色）要同时放进 `update` 才能在深色/浅色切换时更新
  - 判断某个 API 在本依赖版本里是否存在的最快办法：从 gradle cache 把相关 AAR 的 classes.jar 解包拼成 classpath，用 `kotlin-compiler-embeddable` 直接编译一个只含探针调用的 .kt（`java -cp <compiler> org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath <cp> probe.kt -d out`），几十秒出结果，比跑完整 gradle 编译快得多。缺 jetbrains annotations 会让后端 codegen 抛 `NoClassDefFoundError`，但前端的 unresolved reference 报错已经能看清结论

[Project Knowledge Summary]
- Date: 2026-09-28
- Context: Discovered by Agent while verifying whether newly written Compose code actually made it into the Release APK
- Category: Troubleshooting & Debugging
- Instructions:
  - release 构建启用了 R8 full mode（`optimization { enable = true }`），库类名也会被混淆，所以 dex 字符串里查不到 `top/yukonga/miuix/...` 这类类名，用它来判断代码是否编进包是无效的
  - 校验新代码是否进包的正确做法：查 R8 之前的中间产物 `app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/`，用 `javap -c` 看字节码里的方法调用
  - 中文文案类字符串校验仍然有效：`unzip -oq <apk> classes.dex && strings -e S classes.dex | grep -c "文案"`
  - Miuix 的 `Scaffold` 容器背景用的是 `MiuixTheme.colorScheme.surface`，不是 `background`；要给覆盖层配不透明底色须用 `surface`
  - Compose Modifier 链中 `graphicsLayer { }` 只包裹排在其之后的修饰符与内容；要让 `background` 跟着页面一起位移/缩放，必须把 `background` 放在 `graphicsLayer` 之后，而不是之前

[Project Knowledge Summary]
- Date: 2026-09-28
- Context: Discovered by Agent while running Gradle release builds in the sandbox
- Category: Build Methods
- Instructions:
   - 构建命令必须走 `background_terminal_create`，推荐参数 `cpu_percent: 300`、`memory_percent: 80`、`timeout: 1800000`；机器总内存 7 GiB，80% 约等于 6.22 GiB，R8 阶段峰值约 3.7–4.3 GiB，该组合实测安全
   - `memory_percent: 50`（3.89 GiB）实测不够：daemon 会在 `minifyReleaseWithR8` 被 OOM 杀掉，报 `Gradle build daemon disappeared unexpectedly`，APK 时间戳停留在上一轮旧产物、且不报错。R8 上限至少要 5.06 GiB（65%）才稳，推荐直接用 80%
  - 单次完整 release 构建耗时 3m36s 到 5m37s
  - 如果构建过程中修改了源码，Gradle 可能已经按旧源码编译完成；判断产物是否最新的方法是再跑一次 `./gradlew :app:compileReleaseKotlin`（约 17s），出现 `UP-TO-DATE` 即证明当前产物与源码一致
  - build-tools 37.0.0 只提供 apksigner / aapt2 / dexdump，没有 keytool

[Project Knowledge Summary]
- Date: 2026-09-28
- Context: Discovered by Agent while migrating the Pulse UI from Miuix 0.8.8 to 0.9.4 and localizing string resources
- Category: Troubleshooting & Debugging
- Instructions:
  - Miuix 0.9.4 彻底移除了 `top.yukonga.miuix.kmp.extra` 包：`SuperDialog`、`SuperDropdown` 在 0.9.4 所有 AAR 的 classes.jar 中匹配数均为 0，只存在于旧版 `miuix-android`（0.8.8）里
  - 0.9.4 的替代组件：对话框用 `top.yukonga.miuix.kmp.overlay.OverlayDialog`，下拉选择用 `top.yukonga.miuix.kmp.preference.OverlayDropdownPreference`（在 miuix-preference 模块，需在 build.gradle 里单独声明 `miuix-preference-android`）
  - 0.9.4 把单包 `miuix-android` 拆成了 `miuix-ui-android` + `miuix-preference-android` 等多个构件，`libs.versions.toml` 里两个坐标都要写上
  - `TabRowWithContour` 在 0.9.4 里是 `top.yukonga.miuix.kmp.basic.TabRowWithContour(tabs: List<String>, selectedTabIndex: Int, onTabSelected: (Int)->Unit, modifier, ...)`；不存在 `top.yukonga.miuix.kmp.tabrow` 包，也不存在 `ContainerTabRow`
  - `BasicComponent` 在 `top.yukonga.miuix.kmp.basic.ComponentKt` 里，`(modifier, title, titleColor, summary, summaryColor, endAction, ...)`；`OverlayDropdownPreference(items, selectedIndex, title, modifier, titleColor, summary, summaryColor, dropdownColors, startAction, bottomAction, insideMargin, maxHeight, enabled, showValue, renderInRootScaffold, onExpandedChange, onSelectedIndexChange)`
  - 判定某个类/函数在当前依赖里是否存在，用 `javap -p -cp <解包出的 classes.jar> <类全名>` 而不是搜源码；带参数的用 `javap -v` 看 `LocalVariableTable` 里的参数名
  - `androidx.compose.foundation.text.KeyboardOptions` 是本 Compose 版本的正确导入路径，写成 `androidx.compose.ui.text.input.KeyboardOptions` 会报 Unresolved reference
  - 枚举的 `entries` 不能通过伴生对象扩展函数直接引用（`fun AppLanguage.Companion.fromIndex` 里写 `entries` 报 Unresolved reference 'Companion'），改成顶层函数用 `AppLanguage.entries` 最省事
  - `android.content.res.Configuration.setLocale` 在 SDK 24 与更低版本上是两个签名不同的重载（`LocaleList` vs `Locale`），不能把两个分支写在同一个 `configuration.apply { }` 里，否则报 Argument type mismatch；正确做法是两个分支各自 `Configuration(base)` 新建后再 `createConfigurationContext`
  - `Configuration.setLocale(Locale)` 需要 `@Suppress("DEPRECATION")`
  - 会话恢复时若怀疑源码被覆盖，`/root/.local/share/opencode/opencode.db` 的 `part` 表能重放出来：按 `time_created` 顺序遍历，`type='tool'` 的 `state.input` 里取 `write.content` 与 `edit.oldString/newString`（只应用 `status='completed'` 的 edit），同一批消息里并行发出的 edit 的 `time_created` 顺序不保证等于执行顺序，遇到 oldString 匹配不上就是这一批里互相冲突了
  - 重放脚本：`/tmp/opencode/replay_final.py`；`/tmp/opencode/final/` 是完整重放结果，`/tmp/opencode/golden1736/` 是截到某次成功构建时刻的版本；文件名是绝对路径把 `/` 换成 `__` 再加 `.kt`

[Project Knowledge Summary]
- Date: 2026-09-28
- Context: Discovered while converting HttpScreen/MqttScreen/WsScreen literals to stringResource; text replacements silently failed against content that differed from what the Read tool displayed
- Category: Troubleshooting & Debugging
- Instructions:
  - 本环境中 Read 工具可能返回陈旧快照，与磁盘真实内容不一致（本次 HttpScreen.kt 实际为 471 行、bodyTypes 是含 5 项的显式 listOf，Read 却显示 462 行的 BodyType.entries.toList() 版本），基于 Read 内容写 edit/python 替换会静默 no-op
  - 编辑文件前必须用 bash 的 grep/sed 直接读磁盘确认目标片段；python3 的 open().read() 也直接读磁盘，可作为编辑脚本内部的事实来源
  - 批量替换后要立刻 grep 验证替换是否真的生效（打印残留匹配数），防止整轮编辑静默失败
  - 非 Composable 场景（ViewModel 日志等）读字符串资源用 `L10n.t(R.string.xxx, args...)`（ui/l10n/L10n.kt，PulseApp.onCreate 里 bind）；Composable 里统一 stringResource + 三份 strings.xml（values/values-en/values-zh-rTW）
  - 在嵌套 if/lambda 块内插入新代码块时，字符串定位手术极易错配括号归属（本次环境对话框被插进 `if (creating || editing)` 内部还丢了闭合括号）；此类结构编辑应整文件重写，写完立即检查 `{`/`}` 数量平衡并确认新块在函数层

[Project Knowledge Summary]
- Date: 2026-09-29
- Context: Discovered while verifying the release APK for the SSE tab; aapt2 dump 显示 5 个被代码引用的 string 资源不见了
- Category: Build & Compilation
- Instructions:
  - R8 full mode（`optimization { enable = true }`）会把 stub 引擎从未构造的 sealed 事件分支整段删掉（`SseEvent.Failed`、`WsEvent.Failed`、`HttpCallOutcome.Failure` 等），随之被删代码引用的 string 资源也会被一起裁掉；这是正确的 dead-code elimination，不是运行时崩溃（dex 里对这些资源 id 的引用数同为 0）
  - 验证「资源被裁但代码仍引用」是否真的存在：解包 classes.dex 后用 python 统计每个资源 id 的小端 int32 出现次数（`struct.pack('<I', id)`），再和 `aapt2 dump resources` 的 id 集合比对；两边都为 0 才是安全裁剪
  - 资源 id 来源：`app/build/intermediates/runtime_symbol_list/release/processReleaseResources/R.txt`（该文件恒含全部资源，不能用来判断是否被裁）
   - 本项目的 AGP 9.4.1 + R8 full mode 下，把 release 的 `isShrinkResources` 改成 `false` 实测不生效（DSL 里读回是 false，但产物资源集合与 true 完全一致，28 个 app string 照裁），不要指望靠这个开关关掉资源裁剪；要保留某个 string，得保证引用它的代码分支可达

[Project Knowledge Summary]
- Date: 2026-09-29
- Context: Discovered while adding a single-select (radio) row to the Miuix environment list and relocating the save icon onto the section title row
- Category: Troubleshooting & Debugging
- Instructions:
  - Miuix 0.9.4 的 `top.yukonga.miuix.kmp.preference` 模块（`miuix-preference-android`）里有 `RadioButtonPreference(title, selected, onClick, modifier, summary, colors, radioButtonColors, startAction, endActions, radioButtonLocation, bottomAction, insideMargin, holdDownState, enabled)`，签名是 0.8.8 的 `top.yukonga.miuix.preference.RadioButtonPreference(title, selected, onClick, modifier, summary, colors, startAction, endActions)` 的超集；单选行（radio 放在标题左侧）只需传 `title`/`selected`/`onClick`/`modifier`/`summary`/`endActions`，`radioButtonLocation` 默认 `Start`，不用 import
  - 把保存图标放到「卡片上方的小标题行最右侧」的做法：用一个私有的 `SectionTitleRow(label, onSaveClick)` composable（Row + `SmallTitle(weight(1f))` + `IconButton size 36.dp`），四个协议页各自实现一份；卡片内 `OverlayDropdownPreference`/`TextField` 那行去掉原保存 `IconButton`，`showSave` 状态上提到页面主体
  - `SmallTitle(text, modifier, ...)` 第二个参数就是 `modifier`（非 `insideMargin`），用 `javap -v ... SmallTitleKt` 的 `LocalVariableTable` 核参数名
  - 编译验证用 `./gradlew :app:compileReleaseKotlin`（约 15s，`UP-TO-DATE` 即产物最新），全量出包 `./gradlew :app:assembleRelease`（约 1m20s，10 executed/42 up-to-date）；APK 大小 1,891,818 B、V2 签名验证通过

[Project Knowledge Summary]
- Date: 2026-09-29
- Context: Discovered while reordering the SSE/WS/MQTT connection status indicator and redesigning the backup settings card layout
- Category: Build & Compilation
- Instructions:
  - Miuix 0.9.4 的组件包按模块拆：`basic`（`Button`/`Card`/`Text`/`TextField`/`SmallTitle`/`Icon`/`IconButton`/`HorizontalDivider`/`Badge` 等）、`preference`（`CheckboxPreference`/`RadioButtonPreference`/`ArrowPreference`/`OverlayDropdownPreference`/`BasicComponent`）、`overlay`（`OverlayDialog`）、`theme`（`MiuixTheme`）。没有 `top.yukonga.miuix.kmp.component` 这个包，写错包名会整个文件编译不过
  - Miuix 的分隔线叫 `HorizontalDivider`（另有 `VerticalDivider`），没有名为 `Divider` 的组件；`DividerDefaults.Thickness` / `DividerDefaults.DividerColor` 是默认值
  - `MiuixTheme.textStyles` 可用项含 `main`/`subtitle`/`title4`/`footnote2`；`SmallTitle` 内部用的就是 `textStyles.subtitle`
  - `SmallTitle` 默认 `insideMargin = SmallTitleDefaults.InsideMargin`（左右 28dp / 上下 8dp，按全宽使用设计）。放进已有 12dp 内边距的 Card 里会额外多缩 28dp，标题和下方字段对不齐；卡片内做区块小标题要用 `insideMargin = PaddingValues(0.dp)`。本项目已提取共用 composable：`com.evan.pulse.ui.common.CardSectionTitle(text, modifier)`（见 `ui/common/SectionTitle.kt`）
  - `Card` 的 `insideMargin` 默认 `PaddingValues(0.dp)`，所以卡片内要自己套 `Column(modifier = Modifier.padding(12.dp))`；设置页原来两张备份卡片忘了加 padding，内容贴边，是布局难看的主因之一
  - Miuix `TextField` 有 `useLabelAsPlaceholder: Boolean`：为 `true` 时 label 变成单行占位提示、输入后消失，不做浮动上移。长 label（如「事件名过滤（留空接收全部）」）在浮动模式下一定会换行，改占位模式并适当缩短文案是正解
  - Miuix `TextField` 支持 `singleLine: Boolean`（三个重载都有该参数）；URL/路径这类长输入框必须设 `singleLine = true`，否则会换行溢出卡片
  - 本仓库 Compose BOM 2026.09.00 的 foundation **没有** `clickable(shape = ...)` 参数，也**没有** `androidx.compose.foundation.layout.clip`（`Unresolved reference 'clip'`）；同版本 `foundation.shape` 包里只有 `RoundedCornerShape` 等 shape 定义类，没有 Clip 类。唯一可用的 clip 是 `androidx.compose.ui.draw.clip(Shape)`，走 `graphicsLayer(shape=...)` 实现，约束不到涟漪
  - 自定义圆角按压反馈的落地写法（`ui/common/RoundedPressable.kt`，导出 `Modifier.roundedPressable(cornerRadius = 6.dp) { onClick() }`）：`IndicationInstance` / `rememberUpdatedInstance` 在 2026.09.00 已 `@Deprecated(ERROR)`；改实现 `IndicationNodeFactory`（`create(interactionSource): DelegatableNode`，额外声明了抽象的 `hashCode()`/`equals()` 必须自己实现）返回 `Modifier.Node(), DrawModifierNode` 节点类、监听 `PressInteraction` 后调 `invalidateDraw()`（`androidx.compose.ui.node` 里的扩展函数）这套写法能编译，**但高亮只贴合文字**——`clickable` 把该节点插到链条最内侧，其 `DrawScope.size` 只有内容本身，外层 `padding` 不在其中。所以最终实现改成：① `clickable(interactionSource, indication = NoIndication, onClick)`，用一个空 `IndicationNodeFactory`（节点里 `override fun ContentDrawScope.draw() = drawContent()`）关掉系统矩形涟漪；② 再在这一整段之后 `.drawWithContent { if (pressed) drawRoundRect(cornerRadius = CornerRadius(...)); drawContent() }`。**调用处必须写成 `Modifier.roundedPressable { onClick() }.padding(...)`**——Compose 修饰符链是先写的在外层（`Modifier.A().B()` 中 A 在外、B 在内，同官方 `padding(4.dp).background(Red)` 背景被内缩的例子），padding 只给它的内层让出空间，所以 padding 写在最后（最内层）时，外层的 clickable 和 drawWithContent 尺寸都含 padding：点击区和高亮都会盖住整块 padding；反过来写成 `.padding(...).roundedPressable {}` 时两者都只剩内容本身大小，看着就是「改 padding 完全没效果」，这个坑本仓库连踩过三次（padding 数值、圆角数值、换实现三改均无效，改顺序才生效）；③ `pressed` 用 @Composable 的 `collectIsPressedAsState(interactionSource)`，高亮色 `MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)` 在 @Composable 作用域取好再传入绘制 lambda，不在绘制期读主题。无参的 `IndicationNodeFactory` 必须声明成 `object`（`class` 直接当表达式传会报 "does not have a companion object"）；`indication` 参数类型是 `Indication?`
  - 文字可点区域（加号、下拉）一律用 `Row + Icon + Text + Modifier.roundedPressable { } .padding(...)`（padding 写在 roundedPressable 之后，见上一条的顺序说明），添加行 16/12dp、topbar 下拉 12/8dp；不用 Miuix 的 `Button`/`TextButton`——后两者丢掉图标，用户不接受
  - Miuix `TextButton` / `Button` 都有 `cornerRadius: Dp`、`minWidth: Dp`、`minHeight: Dp` 参数（默认已含 48dp 最小点击尺寸），`indication` 不传时取 `LocalIndication`。`TextButton` 只接受 `text: String`（无图标、无 content lambda），需要图标时用 `Button(onClick, ..., content: RowScope.() -> Unit)`（填充款）或 `BasicComponent(title, startAction, onClick)`（整行条目款）。`TextButton` 不做省略号，长文本得 `.take(n)` 预截断


[Project Knowledge Summary]
- Date: 2026-09-29
- Context: Discovered while implementing the real HTTP engine (OkHttpEngine.kt) with Ktor 3.1.3 and rewriting WebDavClient off HttpURLConnection; five compile rounds lost to API shapes that javap alone cannot reveal
- Category: Troubleshooting & Debugging
- Instructions:
  - Ktor 3.x 的客户端创建块 receiver 是 `HttpClientConfig`，引擎级配置要套一层 `engine { ... }`：`HttpClient(OkHttp) { install(HttpTimeout); engine { ... } }`。直接写 `config { }` 会报 `Unresolved reference 'config'`
  - `OkHttpConfig.config(block)` 的 JVM 签名是 `Function1<OkHttpClient.Builder, Unit>`（`javap` 看着像普通参数 lambda），但 Kotlin 编译器实际要求 **receiver 形式**：`config { connectTimeout(0, TimeUnit.MILLISECONDS) }` 能过，`config { builder -> builder.connectTimeout(...) }` 报 `Argument type mismatch ... but 'OkHttpClient.Builder.() -> Unit' was expected`。这种"字节码签名与 Kotlin 期望类型不符"的情况 javap 判断不出来，只能实际编译一次确认；不确定时把两种写法同时写进两个私有函数里编译一次，哪个报错就删哪个（未使用的私有函数只告警不报错）
  - `Headers` 在 Ktor 3.x 继承 `io.ktor.util.StringValues`，`entries()` 是**函数**不是属性，要 `response.headers.entries()` 或直接用 `flattenEntries()`（`io.ktor.util.flattenEntries`，返回 `List<Pair<String,String>>`）；写 `.entries` 会报 `Function invocation 'entries()' expected`
  - `io.ktor.client.request.forms.FormDslKt.formData` 在 Ktor 3.1.3 里无法通过 import 使用（`Unresolved reference 'FormDslKt'`，即使 class 在 jar 里存在）。multipart 改用手动拼装 `ByteArrayContent(bytes, ContentType.MultiPart.FormData.withParameter("boundary", boundary))`；表单编码用 `FormDataContent(Parameters.build { fields.forEach { append(k, v) } })`（`Parameters.build` 在 `io.ktor.http.Parameters$Companion`）
  - `ByteArray.decodeToString(charset)` 在 3.1.3 里第二个参数被推断成 `Int` 报类型不匹配，改用 `String(bytes, charset)`
  - `HttpTimeout` 不是内置插件，必须显式 `install(HttpTimeout)`，否则 per-request 的 `timeout { requestTimeoutMillis = ... }` 只写 attribute 没人消费。相关类型：`HttpTimeoutConfig`（`requestTimeoutMillis`/`connectTimeoutMillis`/`socketTimeoutMillis` 均为 `Long?`，`INFINITE_TIMEOUT_MS`）、`io.ktor.client.plugins.HttpRequestTimeoutException`、`io.ktor.client.network.sockets.ConnectTimeoutException`
  - 代理必须走引擎级 `OkHttpConfig.config { proxy(java.net.Proxy(...)) }`；Ktor 自己的 `ProxyBuilder` 不支持代理认证，代理认证用预置 `Proxy-Authorization: <okhttp3.Credentials.basic(u,p)>` 头（等同 `curl --proxy-user`），SOCKS5 无法携带认证
  - WebDAV 不能用 `java.net.HttpURLConnection`：它校验方法白名单，PROPFIND/MKCOL 直接抛异常（OpenJDK 17 是 `ProtocolException: Invalid HTTP method: PROPFIND`，Android libcore 报 `Expected one of [options, get, head, post, put, delete, trace, connect, patch] but was propfind`），与服务器无关；本项目 WebDavClient 已整体改用 OkHttp，用本地 socket server 实测能收到 `PROPFIND /dav/ HTTP/1.1`、`MKCOL`、`PUT`
  - Android 的 android.jar 里没有 `java.util.concurrent.Timeout`（只有 `TimeUnit`），所以 OkHttp 的 `Call.timeout = Timeout(...)` 写不了，还会连带报 `Function invocation 'timeout()' expected` / `Variable expected`；做单次调用总超时改用 `client.newBuilder().callTimeout(ms, TimeUnit.MILLISECONDS).build()`
  - 本环境 cache 里的 `okhttp-4.12.0.jar` 是残缺构件：没有 `okhttp3.MediaTypeKt` 类，`String.toMediaType()` 与 `ByteArray.toRequestBody()` 扩展函数都不存在；`MediaType.get()`/`parse()` 标了 `DeprecationLevel.ERROR` 调不了；JVM 合成的 `RequestBody.create(byte[])` 从 Kotlin 也调不到（只列 `create(contentType, content, ...)` 候选并报参数缺失）。绕过办法：匿名 `RequestBody` 子类（`contentType()` 返回 `null`、`contentLength()` 写字节数、`writeTo` 用块体调 `sink.write(bytes)`）再在 `Request.Builder` 上显式加 `Content-Type` 头
  - OkHttp 的 `RequestBody.writeTo(sink)` 返回 `Unit`（不是 `BufferedSink`），块体写法必须用 `{ sink.write(bytes) }`；`Request.Builder.method(String, RequestBody)` 返回 Builder，必须再 `.build()`
  - Kotlin 的 `RegexOption` 是 enum，多个选项用 `Regex(pattern, setOf(RegexOption.A, RegexOption.B))`，写 `A or B` 报 `Unresolved reference 'or'`；解析 WebDAV PROPFIND 响应要按 `<response>` 块解析（href 是 response 的直接子元素、在 `<prop>` 外面，按 prop 块内找 href 会永远解析出空列表），且命名空间前缀因服务器而异（`D:`/`d:`/默认无前缀），正则要写 `(?:[\w.-]+:)?` 可选前缀 + IGNORE_CASE

[Project Knowledge Summary]
- Date: 2026-09-30
- Context: Discovered while turning the collection page into an unlimited-nesting folder tree (parentId 递归 + 展开/折叠/新建/重命名/递归删除/移动) and fighting through 5 compile rounds
- Category: Troubleshooting & Debugging
- Instructions:
  - Compose 2026.09.00 的拖拽 API 拿不到落点坐标：`androidx.compose.ui.draganddrop.DragAndDropEvent` 只有 `getDragEvent$ui()`（模块 internal），`dragAndDropTarget` 的回调签名里没有坐标，做不了「拖到行上半插为同级前项」这类坐标判定；同级也只能插到末尾。所以「移动文件夹」全程不做拖拽：第一版长按进入移动模式+点击目标放置，被反馈不好用后改成「移动到」候选弹窗——行首拖拽把手图标或长按集合名打开，单选一个候选父级后确认
  - 「移动到」弹窗要在**数据层预过滤候选**，而不是在 UI 层禁点：`ViewModel.moveTargets(id)` 用 `descendantsOf` 排除自身与全部后代，非法目标根本不出现在列表里，`move()` 的 false 分支退化成兜底错误文案。要跳层级（三级降二级）就选「根层级」候选项，一张候选列表同时覆盖新增层级与跳级移动
  - `depth * 16.dp` 编译不过（`Int.times(Dp)` 不存在，报 `None of the following candidates is applicable`），必须写成 `16.dp * depth`（`Dp.times(Int)`）；更省事是在 Row 里 `repeat(depth) { Spacer(Modifier.width(16.dp)) }`，完全绕开 Dp 算术
  - `var collapsed by remember { mutableStateOf(mutableSetOf<Long>()) }` 会把状态类型固定成 `MutableSet<Long>`，而 `set - id` / `set + id` 返回 `Set<Long>`，赋值报 `ASSIGNMENT_TYPE_MISMATCH`；改成 `mutableStateOf(emptySet<Long>())` + 全程不可变 `Set<Long>` 加减操作
  - 写文件时包名会被显示层吃掉字符：键入 `com/evan` 落成 `com/ein`，键入 `com.evan.pulse` 落成 `com.ean.pulse`（少一个 `v`）。`edit` 工具的 oldString 只要含包名就匹配失败；bash heredoc 里的包名同样被改写，所以「写 python 脚本再 replace」这条路也不通。实测可靠的两种修法：①python 里字符串拼接 `'com.'+'ev'+'an.pulse'`；②从目标文件里已存在且正确的行**取包名前缀**做锚点插入/替换（例如 `next(k for k,l in enumerate(lines) if l.endswith('.data.monitor.MonitorChecker'))` 那行的前缀）。修完对全树跑 `grep -rln 'com\.ean\|com\.evn\|androiex' app/src` 复核
  - 同一条规则也吃全限定 import 的尾巴：`import androidx.lifecycle.viewmodel.compose.viewModel` 落成 `import androidx.lifecycle.viewmodel.compose(viewModel)`，报 `Packages cannot be imported`。全限定 import 写完后必须逐条 `grep -n '^import'` 目检一遍
  - 任何整文件重写后，对全树跑一遍 `grep -rln 'com\.ean\|com\.ein' app/src/main/java` 再编译
  - `edit`/`write` 按「字段行」做批量插入会误伤同名构造块：匹配 `projectId = o.longValue("projectId", 1L),` 时 EnvironmentInfo / HistoryEntry / CollectionInfo 三处构造器各两份共 6 处全中，`parentId` 被塞进 4 个不认识的构造器（`No parameter with name 'parentId' found`）。用字段行做锚点插入前，先 grep 出现次数并逐个确认所属构造器
  - `JsonObjectBuilder.put(key, value)` 没有可空 JsonElement 重载：`put("parentId", c.parentId?.let { JsonPrimitive(it) })` 报 NONE_APPLICABLE，写成 `c.parentId?.let { put("parentId", it) }`
  - 三语字符串多参数用位置参数（`%1$d` / `%2$d`），AAPT2 对多替换的非位置参数会报格式错误
  - 树形分组用**扁平有序列表做唯一数据源 + 展示层按 parentId 派生**（`data/model/CollectionTree.kt` 的 `childrenOf`/`pathOf`/`descendantsOf`/`ancestorsOf`/`isAncestorOrSelf`），不维护并行树结构、不加排序字段：同级顺序即列表相对顺序
  - 展开/折叠的递归树交互被用户推翻，最终改成 **Miuix `BreadcrumbBar` 逐层下钻**（外层一级集合，点进去是二级，以此类推），导航状态只是「根到当前集合的 id 链」，`emptyList` 表示根层级；重命名/删除挂在面包屑右侧工具位，根层级没有当前集合所以那两个按钮不显示。折叠 UI 状态随之全部删除
  - Miuix 0.9.4 的 `BreadcrumbBar` 在 `top.yukonga.miuix.kmp.basic`，签名 `BreadcrumbBar(items: List<BreadcrumbItem>, onItemClick: (Int) -> Unit, modifier, highlightIndex, enabled, colors, insideMargin, itemMaxWidth: Dp, scrollState, interactionSource, indication)`；`BreadcrumbItem(path: String, text: String? = null)`，渲染时取 `text ?: path`，只传 `path` 就以它当显示名。`highlightIndex` 是当前项下标（没有「无选中」值），根标签占下标 0、`stack.size` 就是当前所在集合；`onItemClick` 回调里按下标截断路径即可回退
  - 取 Miuix 组件精确参数名的可靠办法：`javap -v` 看字节码里 Compose 的 `sourceInformationMarkerStart` 常量串，形如 `C(BreadcrumbBar)N(items,onItemClick,modifier,highlightIndex,...)`，这是内联源码级签名（带 `:c#` 前缀的是内联类参数）。别指望 `MethodParameters`/`LocalVariableTable`，主方法体被内联后两个属性都不在
  - 反直觉但实测成立：**`@Composable` 函数的尾参数默认值在字节码里没有 `$default` 静态方法**（`Icon`/`BreadcrumbBar` 都没有），跳过这些参数直接编译通过。所以 `BreadcrumbBarColors` 构造器虽然是 private、没法手工构造，也不需要——不传 `colors` 就走默认
  - 下钻导航的路径必须**派生而不是信任已存状态**：每次组合都按真实父子关系收敛一次（链上任何一环已删除、或不再是前一环的孩子就在此截断），这样删除/跨项目移动之后不会留下指向悬空节点的路径，`remember(own, rawPath)` 的键用结构相等，内容不变就不会重算
  - 非内联 lambda 里不能用 `return@FunctionName`（`FunctionName` 返回 `Unit` 时该标签不可用），写成先取局部变量再 `if (x != null) { ... }` 包一层
  - 移动文件夹要防循环引用：目标若是自身或自身后代则拒绝（`isAncestorOrSelf`）；跨项目移动时整棵子树同步 `projectId`；删除是递归删除整棵子树及其下全部接口条目

[Project Knowledge Summary]
- Date: 2026-09-30
- Context: Discovered by Agent while implementing YAML support for the OpenAPI import feature and diagnosing 7 failing unit tests whose exception message did not match the suspected cause
- Category: Troubleshooting & Debugging
- Instructions:
  - kaml 0.83.0 **不能**把 YAML 直接解码成 `JsonElement`：`yaml.decodeFromString(JsonElement.serializer(), text)` 在 `<root>` 处抛 `com.charleskorn.kaml.MissingTypeTagException: Value is missing a type tag (eg. !<type>)`（`JsonElement` 是 sealed class，kaml 需要类型标签才能判定分支）。正确做法是 `yaml.parseToYamlNode(text)` 拿到节点树后自己递归转 `JsonElement`
  - 节点树类在 `com.charleskorn.kaml`：`YamlMap.getEntries(): Map<YamlScalar, YamlNode>`、`YamlList.getItems(): List<YamlNode>`、`YamlScalar.content: String`、`YamlNull`、`YamlTaggedNode(tag, innerNode)`。递归时先拆 `YamlTaggedNode.innerNode` 再分派，`when` 分支顺序要把 `YamlMap`/`YamlList` 放在具体类型前面
  - `YamlConfiguration(anchorsAndAliases = AnchorsAndAliases.Permitted())`：默认是 `Forbidden`，YAML 里有 `&anchor` / `*alias` 会直接抛 `ForbiddenAnchorOrAliasException`。`Permitted`/`Forbidden` 是 **data class**，要写 `Permitted()` 带括号，写 `AnchorsAndAliases.Permitted` 报 `Argument type mismatch ... actual type is 'Unit'`
  - 允许锚点/别名后，别名在节点树里就是 `YamlTaggedNode` 包着被引用的真实节点，递归里对 `YamlTaggedNode` 取 `innerNode` 即等于「解析前已解析别名」，不必自己实现锚点表
  - YAML 标量转 JSON 必须自己判别 null/bool/number：`~`、空串、`null` → `JsonNull`；`true`/`false`（忽略大小写）→ `JsonPrimitive(Boolean)`；其余先试 `toLongOrNull`/`toDoubleOrNull` 再退回字符串，否则会丢类型
  - OpenAPI 参数的默认值通常在 **`schema.default` / `schema.example`** 下，不在参数对象顶层；只读参数级 `default` 会导致 query/header 参数一个都收集不到，最终表现为 `params` 空列表（单测里 `List.single()` 抛 `NoSuchElementException`，比字段值不对更隐蔽）
  - `security` 是 `{方案名: [scope...]}` 映射，方案名在 **keys** 上；写 `.values.firstOrNull()` 会取到 `[]` 数组，`.text()` 得空串，鉴权永远套不上，单测表现为 `expected:<[BEARER]> but was:<[NONE]>`
  - `kotlinx.serialization` 的 `encodeDefaults` 默认 false：`params` 空列表这种等于默认值的字段会被整体省略，所以「产物 JSON 里没有某字段」无法区分是「没生成」还是「生成后被判为默认值裁剪」，排错时要在生成侧直接断言中间对象
  - 单测失败详情取 `app/build/test-results/testDebugUnitTest/*.xml`：断言失败原因在 `<failure message="...">`，测试里 `println` 的输出在 `<system-out>`（带 CDATA）；要打印 `JsonObject` 结构直接 `.toString()` 即可得紧凑 JSON，别用 `Json.encodeToString(JsonElement.serializer(), ...)` 之外的花活，`JsonElement` 没有 `toString(indent)` 重载
  - 「接口名/路径是数字_数字」这个症状在本项目有**两个**不同成因，别只查 URL：(1) path key 缺前导斜杠 + `base + path` 直粘 → 拼出 `https://api.example.com/v21_2`；(2) 更要命的是**显示名**——API 网关常把 `operationId` 编成 `1_1`/`1_2` 流水号，若命名顺序是 `operationId → summary`，整列接口名就变成 `1_7`、`1_8`，而规范里的 `summary`（`用户协议`）完全正常。本项目的 `displayName` 定为 `summary → "$method $path"`，`operationId` 不参与命名；path 侧要先补前导斜杠、再保证 base 与 path 之间恰好一个 `/`（`base` 为空时相对路径也要带前导斜杠）
  - 用户报「还是不对」时，先确认他装的是哪一版 APK 再用真实规范复现：本轮 470KB 的真实规范下载后跑临时诊断用例，50 组 202 接口，`numeric-only names=0` 才确认修好——靠猜代码定位不到 operationId 这个原因
  - 规范里 path item 本身可以是 `$ref`（`paths: {"/alias": {"$ref": "#/components/pathItems/Shared"}}`），遍历前要先解析；解析后要保留原 path key（`/alias`）而不是被引用目标的 key
  - `security` 的方案定义位置：OpenAPI 3 在 `components.securitySchemes`、Swagger 2 在顶层 `securityDefinitions`，两处都要回退
  - UI 侧按标签分组渲染时用 LazyColumn，item key 不能用 `request.name`——同一分组里两个 operation 同名（`operationId` 不保证唯一、兜底名也可能撞）会抛 duplicate key，改用分组内序号
  - 排查「数据看起来不对」类反馈时，最快路径是往测试里加一个临时诊断用例，把中间对象直接 `println` 到 system-out 读回来；比反复猜代码便宜得多（本轮靠这个 5 分钟定位到 URL 拼接问题）

[Project Knowledge Summary]
- Date: 2026-09-30
- Context: Discovered by Agent while adding a global toast, a history detail page and request rename/move to the collection tree
- Category: Troubleshooting & Debugging
- Instructions:
  - Miuix 0.9.4 的 `Text` **没有默认值**的 `text`/`color` 参数：只传 `modifier` 做占位会报 `No value passed for parameter 'text'`，想要一块占位宽度直接改用 `Spacer(modifier = Modifier.weight(1f))`
  - `@Composable` 函数里 `x?.let { ... }` 这类 lambda 体内**可以**调 `remember`（`let`/`also`/`apply`/`run`/`map`/`filter`/`firstOrNull` 等在 Compose 编译器的高阶函数白名单里），写「打开弹窗时才需要算候选列表」用 `target?.let { val targets = remember(key) { ... }; OverlayDialog(...) }` 是合法且惯用的写法
  - Compose 2026.09.00 的 `foundation.clickable` 签名已变成 `(Modifier, enabled, contentDescription, role, interactionSource, indication, onClick)`，多了 `Role`；`roundedPressable` 里 `clickable(interactionSource = ..., indication = NoIndication, onClick = ...)` 这种命名参数调用不受影响，能继续编译
  - `stringResource(R.string.xxx_with_dollar, someLong)` 可以直接传 `Long` 给 `%1$d`，不需要转 `Int`
  - 校验「中文文案有没有进 release 包」：release 走资源裁剪，用 `unzip -p <apk> resources.arsc` 抽出后用 **python 按 UTF-8 字节**搜（`data.count("已复制".encode())`）；`strings` 找不到是因为 AAPT2 现在把中文按 UTF-8 存，`strings -e l`（UTF-16LE）也会漏。同一字符串出现 2 次通常是它既是某个值的全文、又是另一个长句子里的子串（如 `全部项目` 同时出现在 `history_filter_all` 和 `history_clear_confirm` 里），属正常
   - Kotlin 里 `Modifier.weight(1f)` 只能出现在 `RowScope`/`ColumnScope` 内：在 `Text` 上直接挂 `weight` 而该调用不在 Row 的 lambda 里会报 `Unresolved reference`

[Project Knowledge Summary]
- Date: 2026-10-01
- Context: Discovered by Agent while adding Postman-style pre/post scripts (Rhino 1.7.15) and a swipe-to-delete history row, then getting `testDebugUnitTest` + both APKs to pass
- Category: Troubleshooting & Debugging
- Instructions:
  - `androidx.compose.animation.core.animateFloat` 是 `@Composable`，**不能**在 `LaunchedEffect` 里用（报 `@Composable invocations can only happen from the context of a @Composable function`）。手势松手后要做补间动画：`remember { Animatable(0f) }` 存值，`graphicsLayer { translationX = animatable.value }` 读取，松手回调里 `animatable.animateTo(target, tween(200))`；`snapTo` 也是 suspend，所以必须放进协程。指针输入回调不是协程，用 `rememberCoroutineScope()` 拿到 scope 再 `gestureScope.launch { ... }`，拖拽过程中每帧 `launch { animatable.snapTo(offset) }`
  - 直接写 `launch { }`（顶层 import）报 `DEPRECATION_ERROR: 'launch' can not be called without the corresponding coroutine scope`；改成员调用后若报 `Unresolved reference 'launch' on receiver of type 'CoroutineScope'` 说明 `kotlinx.coroutines.launch` 的 import 漏了，两个报错容易来回打转
  - 全量校验 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` 实测 2m30s~3m（testDebugUnitTest 单独 36s，assemble 双版本 ~2m20s）。**background terminal 的 `timeout` 必须给 1800000 以上**：给 1200000 会在 `mergeExtDexDebug` 处被杀（`killed_by_timeout: yes`，APK 文件停留在上一轮的旧产物，不会报错也不会更新时间戳）。同一台机器上不要并发跑两个 gradle：前一个没退干净的 daemon 会让后一个的 `mergeExtDexDebug` 卡死失败
  - Rhino 1.7.15 参数桥接实测：JS 数字到 Kotlin 是 `java.lang.Double`（即使 `5` 这种整数），到 JS 侧用 `Number` 即可。**JS 数字回显成字符串要自己处理小数点**：`Context.toString(5.0)` 输出 `"5.0"`，所以 `pm.console.log(3)` 会打出 `3.0`；正确做法是整数值（`value % 1.0 == 0.0` 且在 `Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()` 区间内）先 `toLong().toString()`
  - Rhino 抛出的异常消息自带脚本位置后缀，形如 `Error: boom (pulse-script#9)`；展示给用户时要去掉尾部的 `\s*\([^()]*\)$`，否则每条断言失败都带上一串文件名行号
  - `Context.enter()` 必须 `finally { Context.exit() }`；`ScriptableObject.defineProperty` 的 attribute 参数 0 可省略时用 3 参版本；`IdFunctionCall`/`IdFunctionObject` 是 pm 方法绑定的正确姿势（探针见 `probe/Probe16.java`）。完整实测清单已记在对话上下文，需要时看 `app/src/main/java/com/evan/pulse/domain/script/RhinoScriptExecutor.kt` 顶部的类注释
  - 写新测试前先确认编译期约束：`const val` 只能放在顶层或 companion object 里，写在 test class 体内报 `CONST_VAL_NOT_TOP_LEVEL_OR_OBJECT`；Triple 解构顺序要和返回值声明顺序一致，`(_, scope, result)` 和 `(scope, _, result)` 反了会得到 `MutableRequest` 而不是预期的类型
   - 单测里断言脚本输出时，先用一个只打日志的最小脚本跑一遍看真实输出，再写断言：Rhino 的 JS 语义（`NaN`、对象 `toString`、数字精度）和 Kotlin 直觉经常不一致，硬猜断言会白跑好几轮编译（每轮 40s+）

[User Instruction Summary]
- Date: 2026-10-09
- Context: 用户反馈无法区分每轮真机测试安装的是哪一版 APK
- Instructions:
  - 每次构建发布前递增版本号：`app/build.gradle.kts` 的 `versionCode` 与 `versionName` 同步 +1（如 0.1.5/5 -> 0.1.6/6），便于用户在真机上区分新旧构建
  - 出包后在仓库根目录留一份带版本号命名的 APK 副本（如 `Pulse-v0.1.6-release.apk`）
  - 仓库根目录只保留最新一版 APK，出包后删除更早版本的副本，避免堆积


[Project Knowledge Summary]
- Date: 2026-10-09
- Context: Discovered by Agent while fixing the script-editor line-number gutter and the liquid-glass bottom bar occluding page content
- Category: Troubleshooting & Debugging
- Instructions:
  - 在 `drawBehind` 里画行号时，`drawTextLayoutResult(textLayoutResult, topLeft)` 把 `topLeft` 当作文本框**左上角**，所以纵向位置只需 `topLeft.y = 目标行 baseline - 结果 firstBaseline`；**不能再加 `getLineTop(line)`**。多加一次 `getLineTop` 会让第 N 行往下多偏移约 N 行高，表现为「行号 2 跑到第三行」，第 1 行因为 `getLineTop(0)==0` 看起来正常，很容易漏判
  - `TextLayoutResult` 的坐标原点是文本框左上角。`BasicTextField` 外层任何 `padding`/`wrapContentWidth`/滚动容器都会整体平移这个原点，行号列若固定在外层 `drawBehind` 上就必须保证两层原点重合（同列无纵向 padding）
  - `isRuntimeShaderSupported()` 在 Android 12 以下 / 无 AGSL 的设备返回 false，`rememberLayerBackdrop` 拿不到 backdrop。`LiquidGlassNavigationBar` 这种「blur 开启时半透明、否则 `surfaceContainer` 实色」的写法在真机上会退化成一整块不透明实色底栏，用户直接看出「有背景色」。不支持 shader 时也要用半透明色（`surfaceContainer.copy(alpha = 0.4f)`），否则玻璃效果完全消失
  - miuix `Scaffold` 的 `contentPadding` 里已经包含 `bottomBarPlaceable.height`（Scaffold.kt 实测），所以 `paddingValues.calculateBottomPadding()` 与底栏实测高度一致；内容底部再手动加 `liquidGlassBottomInset()` 是重复计算但无害。真正会遮挡的是胶囊 `dropShadow` 半径 10dp 溢出到内容区，底栏内容留位要额外 +10dp
  - 当前 BOM（2026.09.00 / compose ui 1.12.1）的 `BasicTextField` **已经没有 `contentPadding` 参数**了，写了直接报 `No parameter with name 'contentPadding' found`。想让文本框不带任何内边距/垂直居中（行号列、提示文字、光标要严格对齐基线的前提），传一个极简的 `decorationBox = { inner -> Box { inner() } }` 即可，不要用默认的 `BasicTextFieldDefaults.DecorationBox`（它会加内边距 + `Alignment.CenterStart` 垂直居中，文本会被推下 2dp）
  - 编辑器提示文字（placeholder）要和内容基线严格对齐时，别在 `decorationBox` 里放一个兄弟 `Text`：那既可能在默认装饰框下被 padding/居中推偏，又会跟着 `horizontalScroll` 一起横向跑。正确做法是和行号一起画在**同一个 `drawBehind`** 里，用 `measurer.measure(...)` 量出提示文字的 `firstBaseline`，`drawText(hint, Offset(gutterWidth.toPx(), layout.getLineBaseline(0) - hint.firstBaseline))` —— 同一个坐标系 + 同一基线，对齐是恒等式

[Project Knowledge Summary]
- Date: 2026-10-11
- Context: Discovered by Agent while building v0.1.34 release and seeing a masked build failure
- Category: Troubleshooting & Debugging
- Instructions:
  - 后台终端里跑 gradle 时管道会吞退出码：`./gradlew assembleRelease 2>&1 | tail -5; echo "EXIT=$?"` 打印的是 tail 的退出码，构建失败也显示 EXIT=0。要用 `./gradlew ... 2>&1; echo "GRADLE_EXIT=$?"`（无管道）或开头加 `set -o pipefail`，并以日志末尾出现 `BUILD SUCCESSFUL` 为准
