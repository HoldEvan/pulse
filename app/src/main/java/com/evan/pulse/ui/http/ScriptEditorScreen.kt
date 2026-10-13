package com.evan.pulse.ui.http
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.ui.common.subPageOverScroll
import androidx.compose.foundation.layout.Spacer
import com.evan.pulse.ui.common.subPageScrollState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
private val editorFontSize = 13.sp
private val editorLineHeight = 20.sp
private val gutterGap = 12.dp

/**
 * HTTP 脚本的二级代码编辑页。
 *
 * [target] 为 0 编辑前置脚本、1 编辑后置脚本，与 [HttpViewModel] 的持久状态双向绑定。
 * 支持行号、等宽字体与内置 pm API 补全：输入词元时在顶部弹出候选，点选后按
 * [ScriptApiToken.cursorBack] 把光标停到圆括号内。
 */
@Composable
fun ScriptEditorScreen(
    target: Int,
    modifier: Modifier = Modifier,
    onOpenScriptDocs: () -> Unit,
    viewModel: HttpViewModel = viewModel(),
) {
    val isPre = target == 0
    val placeholder = stringResource(if (isPre) R.string.script_pre_label else R.string.script_post_label)
    var value by remember(target) {
        mutableStateOf(TextFieldValue(if (isPre) viewModel.preScript else viewModel.postScript))
    }
    val focusRequester = remember { FocusRequester() }
    fun commit(next: TextFieldValue) {
        value = next
        if (isPre) viewModel.preScript = next.text else viewModel.postScript = next.text
    }

    val caret = value.selection.start.coerceIn(0, value.text.length)
    val token = value.text.take(caret)
        .takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '.' || it == '$' }
    val suggestions = remember(token) { ScriptApiCatalog.match(token) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(if (isPre) R.string.script_pre_title else R.string.script_post_title),
                modifier = Modifier.weight(1f),
                fontSize = 15.sp
            )
            IconButton(onClick = onOpenScriptDocs, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Article,
                    contentDescription = stringResource(R.string.cd_script_docs)
                )
            }
        }
        if (suggestions.isNotEmpty()) {
            SuggestionPanel(
                suggestions = suggestions,
                onPick = { picked ->
                    val start = caret - token.length
                    val newText = value.text.substring(0, start) + picked.insert + value.text.substring(caret)
                    val newCaret = (start + picked.insert.length - picked.cursorBack).coerceIn(0, newText.length)
                    commit(TextFieldValue(newText, TextRange(newCaret)))
                    focusRequester.requestFocus()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ScriptCodeArea(
                value = value,
                onValueChange = { commit(it) },
                placeholder = placeholder,
                focusRequester = focusRequester
            )
        }
    }

}

@Composable
private fun ScriptCodeArea(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
) {
    val editorStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = editorFontSize,
        lineHeight = editorLineHeight,
        color = MiuixTheme.colorScheme.onSurface
    )
    val numberStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = editorFontSize,
        lineHeight = editorLineHeight,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // 逻辑行的起始偏移，用于决定行号列宽与行号总数；编辑器不折行，所以逻辑行数等于视觉行数。
    val lineStarts = remember(value.text) {
        val text = value.text
        val starts = ArrayList<Int>()
        starts.add(0)
        var from = 0
        while (true) {
            val nl = text.indexOf('\n', from)
            if (nl < 0) break
            starts.add(nl + 1)
            from = nl + 1
        }
        starts
    }

    // 行号列宽随行数自适应：取最长行号的实测宽度，避免 1 行号时留出大片空白，
    // 3 行号以上也不被裁掉。上下限防止极端行数把编辑区挤没。
    val gutterWidth = remember(lineStarts.size, numberStyle) {
        val widest = measurer.measure(
            text = AnnotatedString(lineStarts.size.toString()),
            style = numberStyle
        ).size.width
        (with(density) { widest.toDp() } + gutterGap).coerceIn(14.dp, 80.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val measured = layout ?: return@drawBehind
                    for (line in 0 until measured.lineCount) {
                        val number = measurer.measure(
                            text = AnnotatedString((line + 1).toString()),
                            style = numberStyle
                        )
                        // drawTextLayoutResult 把 topLeft 当作文本框左上角，纵向只需按基线差换算；
                        // 不能再加 getLineTop(line)，否则第 2 行起整体下移，行号越往后偏得越多
                        val top = measured.getLineBaseline(line) - number.firstBaseline
                        drawText(textLayoutResult = number, topLeft = Offset(0f, top))
                    }
                    // 提示文字和行号画在同一个 drawBehind 里，共用同一坐标系与基线，
                    // 因此和第 1 行文字、光标严格对齐；放在滚动区里会跟着横向滚动一起跑
                    if (value.text.isEmpty()) {
                        val hint = measurer.measure(
                            text = AnnotatedString(placeholder),
                            style = numberStyle
                        )
                        drawText(
                            textLayoutResult = hint,
                            topLeft = Offset(
                                x = gutterWidth.toPx(),
                                y = measured.getLineBaseline(0) - hint.firstBaseline
                            )
                        )
                    }
                }
        ) {
            // 关闭自动折行、改为横向滚动：每个源码行只占一个视觉行，行号与文字行一一对应；
            // 滚动区从行号列右侧开始，行号列固定不动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = gutterWidth)
                    .horizontalScroll(rememberScrollState())
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = editorStyle,
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.onSurface),
                    onTextLayout = { layout = it },
                    modifier = Modifier
                        .wrapContentWidth()
                        .focusRequester(focusRequester),
                    // 极简装饰框：不给文本加内边距或垂直居中，
                    // 文本框左上角就是外层坐标系原点，行号、提示文字与光标共用同一基线
                    decorationBox = { inner ->
                        Box { inner() }
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestionPanel(
    suggestions: List<ScriptApiToken>,
    onPick: (ScriptApiToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
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
                    Text(
                        text = token.path,
                        fontFamily = FontFamily.Monospace,
                        fontSize = editorFontSize
                    )
                    if (token.detailText().isNotEmpty()) {
                        Text(
                            text = token.detailText(),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

