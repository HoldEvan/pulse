package com.evan.pulse.ui.http

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.CodeCompletion
import com.evan.pulse.ui.design.CodeCompletionProvider
import com.evan.pulse.ui.design.CodeEditorController
import com.evan.pulse.ui.design.CodeEditorField
import com.evan.pulse.ui.design.CodeLanguage
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.squircle.squircleBackground

private val editorFontSize = 13.sp

/** 编辑器底部工具栏的按键：标签 -> 插入内容。Tab 插入两个空格，与编辑器 tab 宽度一致 */
private val editorKeys: List<Pair<String, String>> = listOf(
    "Tab" to "  ",
    "(" to "(",
    ")" to ")",
    "{" to "{",
    "}" to "}",
    "[" to "[",
    "]" to "]",
    "<" to "<",
    ">" to ">",
    "\"" to "\"",
    "'" to "'",
    "`" to "`",
    ":" to ":",
    ";" to ";",
    "=" to "=",
    "+" to "+",
    "-" to "-",
    "*" to "*",
    "/" to "/",
    "\\" to "\\",
    "," to ",",
    "." to ".",
    "!" to "!",
    "?" to "?",
    "&" to "&",
    "|" to "|",
    "$" to "$",
    "_" to "_",
    "@" to "@",
    "#" to "#",
)

/**
 * HTTP 脚本的二级代码编辑页。
 *
 * [target] 为 0 编辑前置脚本、1 编辑后置脚本，与 [HttpViewModel] 的持久状态双向绑定。
 * 编辑器占满整页并使用 sora-editor 的 TextMate 语法高亮，代码提示走编辑器内置的
 * 补全弹窗，候选来自 [ScriptApiCatalog]；底部工具栏提供常用符号与 Tab。
 */
@Composable
fun ScriptEditorScreen(
    target: Int,
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
        CodeCompletionProvider { prefix ->
            ScriptApiCatalog.match(prefix).map { token ->
                CodeCompletion(
                    label = token.path,
                    description = localizedDetails[token.path] ?: token.detail,
                    insert = token.insert,
                    caretBack = token.cursorBack,
                )
            }
        }
    }
    val controller = remember { CodeEditorController() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 8.dp, top = LocalTopBarInset.current + 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        CodeEditorField(
            text = text,
            onValueChange = { commit(it) },
            language = CodeLanguage.JAVASCRIPT,
            completionProvider = provider,
            controller = controller,
            textSizeSp = editorFontSize.value,
            dark = AppTheme.colors.background.luminance() < 0.5f,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        EditorToolbar(
            onInsert = { controller.insert(it) },
            onClear = { commit("") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
private fun EditorToolbar(
    onInsert: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .squircleBackground(AppTheme.colors.surfaceVariant, 12.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(editorKeys) { key ->
                ToolbarKey(label = key.first, onClick = { onInsert(key.second) })
            }
        }
        AppIconButton(
            onClick = onClear,
            modifier = Modifier.size(36.dp)
        ) {
            AppIcon(
                imageVector = MiuixIcons.Light.Delete,
                contentDescription = stringResource(R.string.cd_clear_script)
            )
        }
    }
}

@Composable
private fun ToolbarKey(label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = AppTheme.colors.onSurface
        )
    }
}