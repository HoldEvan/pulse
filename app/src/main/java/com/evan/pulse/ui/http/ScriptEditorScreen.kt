package com.evan.pulse.ui.http

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
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
import com.evan.pulse.ui.design.CodeCompletion
import com.evan.pulse.ui.design.CodeCompletionProvider
import com.evan.pulse.ui.design.CodeEditorField
import com.evan.pulse.ui.design.CodeLanguage
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete

private val editorFontSize = 13.sp

/**
 * HTTP 脚本的二级代码编辑页。
 *
 * [target] 为 0 编辑前置脚本、1 编辑后置脚本，与 [HttpViewModel] 的持久状态双向绑定。
 * 编辑器使用 sora-editor 提供 TextMate 语法高亮，代码提示走编辑器内置的补全弹窗，
 * 候选来自 [ScriptApiCatalog]。
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
                onClick = { commit("") },
                modifier = Modifier.size(36.dp)
            ) {
                AppIcon(
                    imageVector = MiuixIcons.Light.Delete,
                    contentDescription = stringResource(R.string.cd_clear_script)
                )
            }
        }
        AppCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            CodeEditorField(
                text = text,
                onValueChange = { commit(it) },
                language = CodeLanguage.JAVASCRIPT,
                completionProvider = provider,
                textSizeSp = editorFontSize.value,
                dark = AppTheme.colors.background.luminance() < 0.5f,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}