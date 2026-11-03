package com.evan.pulse.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evan.pulse.R
import com.evan.pulse.data.log.AppLog
import com.evan.pulse.data.log.LogEntry
import com.evan.pulse.data.log.LogLevel
import com.evan.pulse.data.log.formatLogTime
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.SectionTitle
import com.evan.pulse.ui.common.subPageLazyListState
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppPreferenceGroup
import com.evan.pulse.ui.design.AppRadioRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTheme
import top.yukonga.miuix.kmp.squircle.squircleBackground

/** 五个级别的说明文案，下标与 [LogLevel] 顺序一致 */
private val LevelSummaries: List<Int> = listOf(
    R.string.log_level_verbose_summary,
    R.string.log_level_debug_summary,
    R.string.log_level_info_summary,
    R.string.log_level_warn_summary,
    R.string.log_level_error_summary
)

/** 级别徽章配色：越严重越偏红，与其它页面的状态色保持同一套观感 */
private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.VERBOSE -> Color(0xFF616161)
    LogLevel.DEBUG -> Color(0xFF1565C0)
    LogLevel.INFO -> Color(0xFF2E7D32)
    LogLevel.WARN -> Color(0xFFEF6C00)
    LogLevel.ERROR -> Color(0xFFC62828)
}

/**
 * 日志设置二级页：上半部分选级别并说明优先级关系，下半部分是内存日志的实时预览。
 *
 * 预览直接订阅 [AppLog.entries]，级别一改就只剩该级别及以上的记录，
 * 不必退出页面。
 */
@Composable
fun LogSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    val currentLevel = LogLevel.fromIndex(settings.logLevelIndex)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll(),
        contentPadding = PaddingValues(top = LocalTopBarInset.current),
        state = subPageLazyListState()
    ) {
        item(key = "level_title") {
            SectionTitle(text = stringResource(R.string.log_level_section))
        }
        item(key = "level_card") {
            AppPreferenceGroup(modifier = Modifier.fillMaxWidth()) {
                // 说明放在选项之前：先讲清优先级关系，再让用户选
                item {
                    AppText(
                        text = stringResource(R.string.log_level_hint),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
                LogLevel.entries.forEach { level ->
                    item {
                        AppRadioRow(
                            title = level.name,
                            selected = level == currentLevel,
                            onClick = { viewModel.setLogLevel(LogLevel.indexOf(level)) },
                            summary = stringResource(LevelSummaries[LogLevel.indexOf(level)])
                        )
                    }
                }
            }
        }
        item(key = "live_title") {
            AppText(
                text = stringResource(R.string.log_live_section),
                modifier = Modifier.padding(top = 12.dp, start = 12.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        item(key = "live_card") {
            AppCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                LiveLogSection(
                    entries = entries,
                    currentLevel = currentLevel,
                    onClear = { AppLog.clear() }
                )
            }
        }
        item(key = "clearance") {
            BottomBarClearance()
        }
    }
}

/** 实时日志区：固定高度、内部滚动，末尾有新记录时自动跟随 */
@Composable
private fun LiveLogSection(
    entries: List<LogEntry>,
    currentLevel: LogLevel,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isEmpty()) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val lastVisible = visible.lastOrNull()?.index ?: -1
        // 只在已经贴近底部时跟随：用户往上翻看历史时不打断
        if (lastVisible < 0 || lastVisible >= entries.size - 2) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AppText(
                text = stringResource(R.string.log_live_count, currentLevel.name, entries.size),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppTextButton(
                text = stringResource(R.string.log_clear),
                onClick = onClear,
                enabled = entries.isNotEmpty()
            )
        }
        if (entries.isEmpty()) {
            AppText(
                text = stringResource(R.string.log_live_empty),
                modifier = Modifier.padding(12.dp),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries) { entry -> LogRow(entry) }
            }
        }
    }
}

/** 单条日志：时间 + 级别徽章 + TAG 一行，消息与异常堆栈另起 */
@Composable
private fun LogRow(entry: LogEntry) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                text = formatLogTime(entry.time),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppText(
                text = entry.level.name,
                color = Color.White,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .squircleBackground(levelColor(entry.level), 4.dp)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                fontSize = 9.sp
            )
            AppText(
                text = entry.tag,
                modifier = Modifier.padding(start = 6.dp),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = AppTheme.colors.onSurfaceSummary
            )
        }
        AppText(
            text = entry.message,
            modifier = Modifier.padding(top = 1.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        entry.detail?.let { stack ->
            AppText(
                text = stack,
                modifier = Modifier.padding(top = 1.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = AppTheme.colors.error
            )
        }
    }
}
