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
  - 本机 `/root/.ktlint/ktlint` 是 0.29.0，无法解析 Kotlin 尾随逗号：对未改动的 `ui/settings/SettingsViewModel.kt` 也会报 `Not a valid Kotlin file (expecting a parameter declaration) (cannot be auto-corrected)` 并直接判整文件失败，因此它在这个仓库里不可用作校验门禁；风格以 Kotlin 编译器 + 同目录既有文件为准
  - 校验「新代码是否真进包」：`assembleRelease` 后查 `app/build/intermediates/packaged_res/release/packageReleaseResources/`，注意各语言目录里文件名是 `values-en/values-en.xml`、`values-zh-rTW/values-zh-rTW.xml`（不是 `values.xml`），只有默认目录叫 `values/values.xml`
  - 三份 strings.xml 的 key 集合必须完全一致：改完后用 `grep -o 'name="[^"]*"' | sort | md5sum` 三份比对（当前各 276 条，三份 md5 相同）

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

