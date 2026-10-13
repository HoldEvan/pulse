package com.evan.pulse.ui.http
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.subPageOverScroll
import androidx.compose.foundation.layout.Spacer
import com.evan.pulse.ui.common.subPageScrollState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
/**
 * HTTP 脚本内置 API 文档页。
 *
 * 与脚本编辑器是两级页面：编辑器右上角的文档入口 push 进这里，
 * [target] 0 为前置脚本、1 为后置脚本；前置脚本没有响应，因此不展示 pm.response 分组。
 */
@Composable
fun ScriptDocsScreen(
    target: Int,
    modifier: Modifier = Modifier,
) {
    val isPre = target == 0
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
    ) {
        Text(text = stringResource(R.string.script_docs_intro))
        Text(
            text = stringResource(R.string.script_docs_props_note),
            modifier = Modifier.padding(top = 8.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp
        )

        DocSection(
            title = stringResource(R.string.script_docs_group_request),
            entries = ScriptApiCatalog.tokens.filter { it.path.startsWith("pm.request") }
        )
        DocSection(
            title = stringResource(R.string.script_docs_group_variables),
            entries = ScriptApiCatalog.tokens.filter {
                it.path.startsWith("pm.variables") ||
                    it.path.startsWith("pm.environment") ||
                    it.path.startsWith("pm.globals")
            }
        )
        DocSection(
            title = stringResource(R.string.script_docs_group_log),
            entries = ScriptApiCatalog.tokens.filter {
                it.path.startsWith("pm.console") ||
                    it.path == "pm.log" ||
                    it.path == "pm.test" ||
                    it.path.startsWith("pm.iteration")
            }
        )
        if (!isPre) {
            DocSection(
                title = stringResource(R.string.script_docs_group_response),
                entries = ScriptApiCatalog.tokens.filter { it.path.startsWith("pm.response") }
            )
        }

        Text(
            text = stringResource(R.string.script_docs_examples_title),
            modifier = Modifier.padding(top = 16.dp),
            color = MiuixTheme.colorScheme.primary,
            fontSize = 14.sp
        )
        MonospaceBlock(
            stringResource(
                if (isPre) R.string.script_docs_example_pre else R.string.script_docs_example_post
            ),
            modifier = Modifier.padding(bottom = 24.dp),
            selectable = true
        )
    }
}

/** 文档分组：组名 + 该组下的全部 API 条目 */
@Composable
private fun DocSection(title: String, entries: List<ScriptApiToken>) {
    if (entries.isEmpty()) return
    Text(
        text = title,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        color = MiuixTheme.colorScheme.primary,
        fontSize = 14.sp
    )
    entries.forEach { token ->
        // 长按可选择 API 签名与说明，便于直接复制到脚本里
        SelectionContainer {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = token.path,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                if (token.detailText().isNotEmpty()) {
                    Text(
                        text = token.detailText(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}
