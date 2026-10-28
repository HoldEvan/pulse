package com.evan.pulse.ui.http

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.CodeEditorController
import com.evan.pulse.ui.design.CodeEditorField
import com.evan.pulse.ui.design.CodeLanguage
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
private val editorFontSize = 13.sp

/**
 * HTTP 脚本的二级代码编辑页。
 *
 * [target] 为 0 编辑前置脚本、1 编辑后置脚本，与 [HttpViewModel] 的持久状态双向绑定。
 * 编辑器使用 sora-editor 提供 TextMate 语法高亮，并保留内置 pm API 补全：输入词元时
 * 在顶部弹出候选，点选后按 [ScriptApiToken.cursorBack] 把光标停到圆括号内。
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

    var caret by remember(target) { mutableIntStateOf(text.length) }
    val controller = remember(target) { CodeEditorController() }
    val token = remember(text, caret) {
        text.take(caret.coerceIn(0, text.length))
            .takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '.' || it == '$' }
    }
    val suggestions = remember(token) { ScriptApiCatalog.match(token) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 8.dp, top = LocalTopBarInset.current + 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = stringResource(if (isPre) R.string.script_pre_title else R.string.script_post_title),
                modifier = Modifier.weight(1f),
                fontSize = 15.sp
            )
            AppIconButton(
                onClick = {
                    commit("")
                    caret = 0
                },
                modifier = Modifier.size(36.dp)
            ) {
                AppIcon(
                    imageVector = MiuixIcons.Light.Delete,
                    contentDescription = stringResource(R.string.cd_clear_script)
                )
            }
        }
        if (suggestions.isNotEmpty()) {
            SuggestionPanel(
                suggestions = suggestions,
                onPick = { picked ->
                    val end = caret.coerceIn(0, text.length)
                    val start = (end - token.length).coerceAtLeast(0)
                    controller.replaceRange(start, end, picked.insert, picked.cursorBack)
                    commit(text.substring(0, start) + picked.insert + text.substring(end))
                    caret = (start + picked.insert.length - picked.cursorBack)
                        .coerceIn(0, text.length)
                    controller.requestFocus()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
        AppCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CodeEditorField(
                text = text,
                onValueChange = { commit(it) },
                onCursorChange = { caret = it },
                language = CodeLanguage.JAVASCRIPT,
                controller = controller,
                textSizeSp = editorFontSize.value,
                dark = AppTheme.colors.background.luminance() < 0.5f,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SuggestionPanel(
    suggestions: List<ScriptApiToken>,
    onPick: (ScriptApiToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
        ) {
            suggestions.forEach { token ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(token) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    AppText(
                        text = token.path,
                        fontFamily = FontFamily.Monospace,
                        fontSize = editorFontSize
                    )
                    if (token.detailText().isNotEmpty()) {
                        AppText(
                            text = token.detailText(),
                            color = AppTheme.colors.onSurfaceSummary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}