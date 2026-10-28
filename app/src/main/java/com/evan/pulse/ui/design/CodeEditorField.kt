package com.evan.pulse.ui.design

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource

/** 代码编辑器支持的语言，取值对应 assets/textmate/languages.json 中的 scopeName */
enum class CodeLanguage(val scopeName: String) {
    JSON("source.json"),
    YAML("source.yaml"),
    JAVASCRIPT("source.js"),
}

/**
 * 一条内置自动补全候选。
 *
 * [description] 展示在候选右侧（通常是调用签名）；[insert] 是选中后写入的内容；
 * [caretBack] 表示插入后光标回退的字符数，用于把光标停在一对圆括号中间。
 */
data class CodeCompletion(
    val label: String,
    val description: String = "",
    val insert: String = label,
    val caretBack: Int = 0,
)

/** 按当前输入前缀给出补全候选；在编辑器的补全线程里调用，实现方不能触碰 Compose 状态 */
fun interface CodeCompletionProvider {
    fun complete(prefix: String): List<CodeCompletion>
}

/**
 * TextMate 语法与主题的全局注册表。
 *
 * 注册表是进程级单例，无论打开多少个编辑器，资源只解析一次；首次加载放在 IO 线程，
 * 避免大体积语法文件（JavaScript 语法约 240KB）阻塞首帧。
 */
object CodeEditorSupport {

    private const val LIGHT_THEME = "quietlight"
    private const val DARK_THEME = "darcula"

    private val initMutex = Mutex()

    @Volatile
    private var ready = false

    @Volatile
    private var currentTheme: String? = null

    suspend fun ensureReady(context: Context) {
        if (ready) return
        initMutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) { load(context.applicationContext) }
            ready = true
        }
    }

    /** 切换配色方案；主题是全局状态，所有已挂载的编辑器会一起刷新 */
    fun applyTheme(dark: Boolean) {
        if (!ready) return
        val name = if (dark) DARK_THEME else LIGHT_THEME
        if (currentTheme == name) return
        currentTheme = name
        ThemeRegistry.getInstance().setTheme(name)
    }

    private fun load(context: Context) {
        val fileRegistry = FileProviderRegistry.getInstance()
        fileRegistry.addFileProvider(AssetsFileResolver(context.assets))
        val themeRegistry = ThemeRegistry.getInstance()
        loadTheme(themeRegistry, LIGHT_THEME, dark = false)
        loadTheme(themeRegistry, DARK_THEME, dark = true)
        currentTheme = LIGHT_THEME
        themeRegistry.setTheme(LIGHT_THEME)
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }

    private fun loadTheme(registry: ThemeRegistry, name: String, dark: Boolean) {
        val path = "textmate/themes/$name.json"
        val source = IThemeSource.fromInputStream(
            FileProviderRegistry.getInstance().tryGetInputStream(path),
            path,
            null
        )
        registry.loadTheme(ThemeModel(source, name).apply { isDark = dark })
    }
}

/**
 * 给 TextMate 语言加上自定义补全的语言包装。
 *
 * 语法解析全部委托给 [delegate]，只接管 requireAutoComplete：sora-editor 内置的
 * 补全弹窗会直接展示 [provider] 返回的候选，插入与光标回退由 [CompletionEntry] 完成。
 */
private class CompletionLanguage(
    private val delegate: Language,
    private val provider: CodeCompletionProvider,
) : Language by delegate {

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        val prefix = prefixAt(content, position)
        if (prefix.isEmpty()) return
        val items = provider.complete(prefix)
        if (items.isEmpty()) return
        items.forEach { publisher.addItem(CompletionEntry(it, prefix.length)) }
    }
}

/** 取光标左侧的标识符片段，允许 `.`，让 `pm.request.` 这类成员路径整体参与匹配 */
private fun prefixAt(content: ContentReference, position: CharPosition): String {
    if (position.line < 0 || position.line >= content.lineCount) return ""
    val line = content.getLine(position.line)
    val column = position.column.coerceIn(0, line.length)
    var start = column
    while (start > 0 && isPrefixChar(line[start - 1])) start--
    return line.substring(start, column)
}

private fun isPrefixChar(c: Char): Boolean =
    c.isLetterOrDigit() || c == '_' || c == '$' || c == '.'

/** 内置弹窗里的补全项：替换已输入的前缀，并把光标停在 insert 文本的指定位置 */
private class CompletionEntry(
    private val completion: CodeCompletion,
    prefixLen: Int,
) : CompletionItem(completion.label, completion.description) {

    init {
        prefixLength = prefixLen
        kind = CompletionItemKind.Method
    }

    override fun performCompletion(editor: CodeEditor, content: Content, line: Int, column: Int) {
        val col = column.coerceIn(0, content.getColumnCount(line))
        val start = (col - prefixLength).coerceIn(0, col)
        val startIndex = content.getCharIndex(line, start)
        content.replace(line, start, line, col, completion.insert)
        // setSelection 接收的是行列坐标（不是字符下标），插入后按新的字符下标换算
        val end = content.getIndexer().getCharPosition(startIndex + completion.insert.length)
        val caretColumn = (end.column - completion.caretBack)
            .coerceIn(0, content.getColumnCount(end.line))
        editor.setSelection(end.line, caretColumn)
    }
}

/**
 * sora-editor 的 Compose 封装：TextMate 语法高亮 + 行号 + 等宽字体。
 *
 * [text] 由调用方持有，编辑器内部编辑通过 [onValueChange] 回传；外部修改内容时
 * 通过文本比对同步到编辑器，避免每次重组都重设文本导致光标跳动。
 * 传入 [completionProvider] 后使用 sora-editor 内置的补全弹窗展示候选项。
 */
@Composable
fun CodeEditorField(
    text: String,
    onValueChange: (String) -> Unit,
    language: CodeLanguage,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 13f,
    dark: Boolean = false,
    readOnly: Boolean = false,
    completionProvider: CodeCompletionProvider? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val loaded by produceState(initialValue = false, context) {
        CodeEditorSupport.ensureReady(context)
        value = true
    }
    if (!loaded) {
        Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.surfaceContainer))
        return
    }

    // 主题是全局状态，必须在创建编辑器前应用；applyTheme 内部按名字去重，重复调用无副作用
    CodeEditorSupport.applyTheme(dark)

    val latestOnChange by rememberUpdatedState(onValueChange)
    val editorRef = remember { mutableStateOf<CodeEditor?>(null) }

    // 换肤或换语言都重建编辑器：配色方案与语法对象只在挂载时注入（仅切换时发生）
    key(dark, language) {
        DisposableEffect(Unit) {
            onDispose {
                editorRef.value?.release()
                editorRef.value = null
            }
        }

        AndroidView(
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
            factory = { ctx ->
                CodeEditor(ctx).apply {
                    setTypefaceText(Typeface.MONOSPACE)
                    setTextSize(textSizeSp)
                    setLineSpacing(0f, 1.15f)
                    setWordwrap(true)
                    setTabWidth(2)
                    setLineNumberEnabled(true)
                    setHighlightCurrentLine(false)
                    setScrollBarEnabled(true)
                    setEditable(!readOnly)
                    val pad = with(density) { 8.dp.roundToPx() }
                    setPadding(pad, pad, pad, pad)
                    setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()))
                    val textMate = TextMateLanguage.create(language.scopeName, false)
                    setEditorLanguage(
                        if (completionProvider == null) {
                            textMate
                        } else {
                            CompletionLanguage(textMate, completionProvider)
                        }
                    )
                    setText(text)
                    subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                        latestOnChange(event.editor.text.toString())
                    }
                    editorRef.value = this
                }
            },
            update = { editor ->
                if (editor.text.toString() != text) editor.setText(text)
            },
        )
    }
}
