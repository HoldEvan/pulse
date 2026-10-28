package com.evan.pulse.ui.http

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.domain.script.JsFormatter
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppScaffold
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.CodeCompletion
import com.evan.pulse.ui.design.CodeCompletionProvider
import com.evan.pulse.ui.design.CodeEditorController
import com.evan.pulse.ui.design.CodeEditorField
import com.evan.pulse.ui.design.CodeLanguage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import io.github.rosemoe.sora.widget.SymbolInputView
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward

private val editorFontSize = 13.sp

/** 底部符号栏每行的高度；两行共占 [symbolRowHeight] × 2 */
private val symbolRowHeight = 44.dp

/**
 * 底部符号栏的按键：左为按钮文字，右为插入内容。
 *
 * Tab 交给 [SymbolInputView] 处理（按缩进或跳到下一个 snippet 占位），其余按键
 * 通过编辑器的 insertText 插入，自动继承撤销栈与选区替换行为。
 */
private val symbolRowLabels = arrayOf(
    arrayOf("Tab", "↵", "{", "}", "(", ")", "[", "]", "<", ">", "\"", "'", ";", ":", "="),
    arrayOf("+", "-", "*", "/", "\\", "|", "&", "!", "?", ",", ".", "$", "_", "@", "#")
)

private val symbolRowValues = arrayOf(
    arrayOf("\t", "\n", "{", "}", "(", ")", "[", "]", "<", ">", "\"", "'", ";", ":", "="),
    arrayOf("+", "-", "*", "/", "\\", "|", "&", "!", "?", ",", ".", "$", "_", "@", "#")
)

/**
 * HTTP 脚本的二级代码编辑页。
 *
 * 不使用统一的 [com.evan.pulse.ui.SubPageScreen]：编辑区需要整屏铺满，顶栏改为一行的
 * 轻量操作条（返回、标题、撤销/重做、文档），底部用 sora-editor 自带的
 * [SymbolInputView] 提供常用符号与 Tab。
 *
 * [target] 为 0 编辑前置脚本、1 编辑后置脚本，与 [HttpViewModel] 的持久状态双向绑定。
 */
@Composable
fun ScriptEditorScreen(
    target: Int,
    onBack: () -> Unit,
    onOpenDocs: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HttpViewModel = viewModel(),
) {
    val isPre = target == 0
    var text by remember(target) {
        mutableStateOf(if (isPre) viewModel.preScript else viewModel.postScript)
    }
    fun commit(next: String) {
        text = next
        if (isPre) viewModel.preScript = next else viewModel.postScript = next
    }

    val localizedDetails = ScriptApiCatalog.tokens
        .filter { it.detailRes != 0 }
        .associate { it.path to stringResource(it.detailRes) }
    val provider = remember(localizedDetails) {
        CodeCompletionProvider { prefix, source ->
            ScriptApiCatalog.match(prefix, source).map { token ->
                CodeCompletion(
                    label = token.path,
                    description = localizedDetails[token.path] ?: token.detail,
                    insert = token.insert,
                    caretBack = token.cursorBack,
                    isMethod = token.isMethod,
                )
            }
        }
    }
    val controller = remember { CodeEditorController() }
    val dark = AppTheme.colors.background.luminance() < 0.5f
    val view = LocalView.current

    fun hideKeyboard() {
        val manager = view.context
            .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(view.windowToken, 0)
        controller.clearFocus()
    }

    // 页面被移除（系统返回键）也要收键盘，否则输入法会留在上一层
    DisposableEffect(Unit) {
        onDispose { hideKeyboard() }
    }

    AppScaffold(
        modifier = modifier,
        topBar = {
            EditorTopBar(
                title = stringResource(if (isPre) R.string.script_tab_pre else R.string.script_tab_post),
                canUndo = controller.canUndo,
                canRedo = controller.canRedo,
                onBack = {
                    hideKeyboard()
                    onBack()
                },
                onUndo = { controller.undo() },
                onRedo = { controller.redo() },
                onFormat = {
                    val formatted = JsFormatter.format(text)
                    if (formatted.isNotEmpty() && formatted != text) commit(formatted)
                },
                onOpenDocs = {
                    hideKeyboard()
                    onOpenDocs()
                }
            )
        },
        bottomBar = {
            SymbolBar(controller = controller)
        }
    ) { padding ->
        CodeEditorField(
            text = text,
            onValueChange = { commit(it) },
            language = CodeLanguage.JAVASCRIPT,
            completionProvider = provider,
            controller = controller,
            textSizeSp = editorFontSize.value,
            dark = dark,
            background = AppTheme.colors.surface,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

/** 单行顶栏：返回、标题、撤销/重做、格式化、脚本文档；不使用会随滚动收缩的大标题 */
@Composable
private fun EditorTopBar(
    title: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: () -> Unit,
    onOpenDocs: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AppIconButton(onClick = onBack) {
            AppIcon(
                imageVector = MiuixIcons.Light.ChevronBackward,
                contentDescription = stringResource(R.string.cd_back)
            )
        }
        AppText(
            text = title,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AppIconButton(onClick = onUndo, enabled = canUndo) {
            AppIcon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.cd_undo)
            )
        }
        AppIconButton(onClick = onRedo, enabled = canRedo) {
            AppIcon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = stringResource(R.string.cd_redo)
            )
        }
        AppIconButton(onClick = onFormat) {
            AppIcon(
                imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                contentDescription = stringResource(R.string.cd_format_script)
            )
        }
        AppIconButton(onClick = onOpenDocs) {
            AppIcon(
                imageVector = Icons.AutoMirrored.Filled.Article,
                contentDescription = stringResource(R.string.cd_script_docs)
            )
        }
    }
}

/**
 * sora-editor 自带的符号输入栏：横向滚动，颜色跟随当前主题。
 *
 * 键盘弹起时用 IME 高度的底部内边距把整条栏顶到键盘上方，而不是被键盘盖住；
 * 右侧固定显示光标行列号，方便定位。
 */
@Composable
private fun SymbolBar(controller: CodeEditorController, modifier: Modifier = Modifier) {
    if (!controller.isAttached) return
    val textColor = AppTheme.colors.onSurface.toArgb()
    val backgroundColor = AppTheme.colors.surfaceVariant.toArgb()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 底色铺在键盘内边距之前：键盘是圆角的，角落露出的这块区域要和工具条同色
            .background(AppTheme.colors.surfaceVariant)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .height(symbolRowHeight * 2f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            factory = { context ->
                val metrics = context.resources.displayMetrics
                val rowHeight = (symbolRowHeight.value * metrics.density).toInt()
                // 默认 Button 的 minWidth/padding 会在按键之间留很大空隙，这里收紧
                val sidePadding = (3 * metrics.density).toInt()
                // 两行放进同一个滚动容器：横向滑动时上下两行始终一起移动
                val rows = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    for (index in symbolRowLabels.indices) {
                        addView(
                            SymbolInputView(context).apply {
                                addSymbols(symbolRowLabels[index], symbolRowValues[index])
                                forEachButton { button ->
                                    button.minimumWidth = 0
                                    button.minimumHeight = 0
                                    button.setPadding(sidePadding, 0, sidePadding, 0)
                                }
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    rowHeight
                                )
                            }
                        )
                    }
                }
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(rows)
                }
            },
            update = { view ->
                val rows = view.getChildAt(0) as? LinearLayout ?: return@AndroidView
                for (index in 0 until rows.childCount) {
                    val symbolView = rows.getChildAt(index) as? SymbolInputView ?: continue
                    symbolView.bindEditor(controller.editor)
                    symbolView.setTextColor(textColor)
                    symbolView.setBackgroundColor(backgroundColor)
                }
            }
        )
        AppText(
            text = "${controller.cursorLine}:${controller.cursorColumn}",
            modifier = Modifier.padding(horizontal = 10.dp),
            fontSize = 12.sp,
            color = AppTheme.colors.onSurfaceSummary,
            maxLines = 1
        )
    }
}
