package com.evan.pulse.ui.design

import android.content.Context
import android.graphics.Typeface
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
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
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
 * 编辑器的命令式入口：用于从外部插入文本、移动光标或请求焦点。
 *
 * Compose 侧的文本同步仍走 [CodeEditorField] 的 value 参数，这里只暴露
 * 那些无法用状态表达的操作（替换指定区间、聚焦）。
 */
class CodeEditorController {
    internal var editor: CodeEditor? = null

    /** 用 [replacement] 覆盖 [start]..[end) 区间，光标停在末尾往前 [caretBack] 个字符处 */
    fun replaceRange(start: Int, end: Int, replacement: String, caretBack: Int = 0) {
        val target = editor ?: return
        target.text.replace(start, end, replacement)
        val caret = (start + replacement.length - caretBack).coerceIn(0, target.text.length)
        target.setSelection(caret, caret)
    }

    fun requestFocus() {
        editor?.requestFocus()
    }
}

/**
 * sora-editor 的 Compose 封装：TextMate 语法高亮 + 行号 + 等宽字体。
 *
 * [text] 由调用方持有，编辑器内部编辑通过 [onValueChange] 回传；外部修改内容时
 * 通过文本比对同步到编辑器，避免每次重组都重设文本导致光标跳动。
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
    controller: CodeEditorController? = null,
    onCursorChange: ((Int) -> Unit)? = null,
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
    val latestOnCursorChange by rememberUpdatedState(onCursorChange)
    val editorRef = remember { mutableStateOf<CodeEditor?>(null) }

    // 换肤时重建编辑器：配色方案不主动通知重绘，重新挂载最省心（仅切换主题时发生）
    key(dark) {
        DisposableEffect(Unit) {
            onDispose {
                controller?.editor = null
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
                    setEditorLanguage(TextMateLanguage.create(language.scopeName, false))
                    setText(text)
                    subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                        latestOnChange(event.editor.text.toString())
                    }
                    subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
                        latestOnCursorChange?.invoke(event.left.index)
                    }
                    editorRef.value = this
                    controller?.editor = this
                }
            },
            update = { editor ->
                if (editor.text.toString() != text) editor.setText(text)
            },
        )
    }
}
