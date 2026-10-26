package com.evan.pulse.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evan.pulse.R
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.KeyValueEditor
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.SectionTitle
import com.evan.pulse.ui.common.subPageLazyListState
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme

private val SectionTopGap = 12.dp
private val CardContentPadding = 12.dp

/** 请求方法下拉项，下标与 AppSettings.webhookMethodIndex 对齐 */
private val WebhookMethodLabels = listOf("POST", "GET")

@Composable
fun WebhookScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isGet = settings.webhookMethodIndex == 1

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .subPageOverScroll(),
        contentPadding = PaddingValues(top = LocalTopBarInset.current),
        state = subPageLazyListState()
    ) {
        item(key = "webhook_request_title") {
            SectionTitle(text = stringResource(R.string.webhook_section_request))
        }

        item(key = "webhook_request_card") {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                AppDropdown(
                    title = stringResource(R.string.webhook_method_label),
                    items = WebhookMethodLabels,
                    selectedIndex = settings.webhookMethodIndex.coerceIn(0, WebhookMethodLabels.lastIndex),
                    onSelectedIndexChange = { viewModel.setWebhookMethodIndex(it) }
                )
                Column(modifier = Modifier.padding(CardContentPadding)) {
                    var webhookUrl by remember(settings.webhookUrl) {
                        mutableStateOf(settings.webhookUrl)
                    }
                    AppTextField(
                        value = webhookUrl,
                        onValueChange = {
                            webhookUrl = it
                            viewModel.setWebhookUrl(it)
                        },
                        label = stringResource(R.string.settings_webhook_url),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppText(
                        text = stringResource(R.string.webhook_params_label),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    // 参数同样只在进入时取一次，避免持久化回写把输入中的空行收敛掉
                    var webhookParams by remember {
                        mutableStateOf(
                            settings.webhookParams.map {
                                KeyValue(it.key, it.value, it.enabled)
                            }.ifEmpty { listOf(KeyValue()) }
                        )
                    }
                    fun persistParams(next: List<KeyValue>) {
                        webhookParams = next
                        viewModel.setWebhookParams(
                            next.filter { it.key.isNotBlank() }
                                .map { KeyValueEntry(it.key, it.value, it.enabled) }
                        )
                    }
                    KeyValueEditor(
                        items = webhookParams,
                        keyLabel = stringResource(R.string.kv_param_key),
                        valueLabel = stringResource(R.string.kv_value_label),
                        onChange = { index, updated ->
                            persistParams(webhookParams.toMutableList().also { it[index] = updated })
                        },
                        onAdd = { persistParams(webhookParams + KeyValue()) },
                        onRemove = { index ->
                            persistParams(webhookParams.toMutableList().also { it.removeAt(index) })
                        }
                    )
                    AppText(
                        text = stringResource(R.string.webhook_params_hint),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        if (!isGet) {
            item(key = "webhook_body_title") {
                SectionTitle(
                    text = stringResource(R.string.webhook_section_body),
                    topPadding = SectionTopGap
                )
            }

            item(key = "webhook_body_card") {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(CardContentPadding)) {
                        AppDropdown(
                            title = stringResource(R.string.settings_webhook_format),
                            items = listOf(
                                stringResource(R.string.webhook_format_generic),
                                stringResource(R.string.webhook_format_wecom),
                                stringResource(R.string.webhook_format_dingtalk),
                                stringResource(R.string.webhook_format_feishu),
                                stringResource(R.string.webhook_format_slack)
                            ),
                            selectedIndex = settings.webhookFormatIndex.coerceIn(
                                0,
                                com.evan.pulse.domain.webhook.WebhookFormat.entries.lastIndex
                            ),
                            filled = true,
                            onSelectedIndexChange = { viewModel.setWebhookFormatIndex(it) }
                        )
                        var webhookTemplate by remember(settings.webhookTemplate) {
                            mutableStateOf(settings.webhookTemplate)
                        }
                        AppTextField(
                            value = webhookTemplate,
                            onValueChange = {
                                webhookTemplate = it
                                viewModel.setWebhookTemplate(it)
                            },
                            label = stringResource(R.string.settings_webhook_template),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                        AppText(
                            text = stringResource(R.string.settings_webhook_template_hint),
                            fontSize = 12.sp,
                            color = AppTheme.colors.onSurfaceSummary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        item(key = "webhook_headers_title") {
            SectionTitle(
                text = stringResource(R.string.webhook_section_headers),
                topPadding = SectionTopGap
            )
        }

        item(key = "webhook_headers_card") {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(CardContentPadding)) {
                    var webhookHeaders by remember {
                        mutableStateOf(
                            settings.webhookHeaders.map {
                                KeyValue(it.key, it.value, it.enabled)
                            }.ifEmpty { listOf(KeyValue()) }
                        )
                    }
                    fun persistHeaders(next: List<KeyValue>) {
                        webhookHeaders = next
                        viewModel.setWebhookHeaders(
                            next.filter { it.key.isNotBlank() }
                                .map { KeyValueEntry(it.key, it.value, it.enabled) }
                        )
                    }
                    KeyValueEditor(
                        items = webhookHeaders,
                        keyLabel = stringResource(R.string.kv_header_key),
                        valueLabel = stringResource(R.string.kv_value_label),
                        onChange = { index, updated ->
                            persistHeaders(webhookHeaders.toMutableList().also { it[index] = updated })
                        },
                        onAdd = { persistHeaders(webhookHeaders + KeyValue()) },
                        onRemove = { index ->
                            persistHeaders(webhookHeaders.toMutableList().also { it.removeAt(index) })
                        }
                    )
                    AppText(
                        text = stringResource(R.string.settings_webhook_headers_hint),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        item(key = "webhook_test_card") {
            AppCard(modifier = Modifier.fillMaxWidth().padding(top = SectionTopGap)) {
                Column(modifier = Modifier.padding(CardContentPadding)) {
                    val testState by viewModel.webhookTest.collectAsStateWithLifecycle()
                    AppTextButton(
                        text = stringResource(R.string.settings_webhook_test),
                        filled = true,
                        onClick = { viewModel.sendWebhookTest() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    testState?.let { state ->
                        AppText(
                            text = stringResource(
                                if (state == WebhookTestState.Ok) {
                                    R.string.settings_webhook_test_ok
                                } else {
                                    R.string.settings_webhook_test_failed
                                }
                            ),
                            fontSize = 12.sp,
                            color = if (state == WebhookTestState.Ok) {
                                AppTheme.colors.onSurfaceSummary
                            } else {
                                AppTheme.colors.error
                            },
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        item(key = "webhook_clearance") {
            BottomBarClearance()
        }
    }
}
