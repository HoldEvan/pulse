package com.evan.pulse.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evan.pulse.R
import com.evan.pulse.data.settings.AppSettings
import com.evan.pulse.ui.common.DialogActions
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.subPageOverScroll
import androidx.compose.foundation.layout.Spacer
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.theme.BarBlurStyleLabels
import com.evan.pulse.ui.theme.ColorSpecLabels
import com.evan.pulse.ui.theme.PaletteStyleLabels
import com.evan.pulse.ui.theme.ThemeKeyColors
import com.evan.pulse.ui.theme.ThemeModeLabels
import com.evan.pulse.ui.theme.isMonetMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val KeyColorLabels: List<Int> =
    listOf(R.string.theme_follow_system_color) + ThemeKeyColors.map { it.first }

private val BackGestureModeLabels: List<Int> = listOf(
    R.string.back_mode_miuix,
    R.string.back_mode_aosp
)

private val BackGestureModeSummaries: Map<Int, Int> = mapOf(
    0 to R.string.back_mode_miuix_summary,
    1 to R.string.back_mode_aosp_summary
)

@Composable
fun ThemeSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    ThemeSettingsContent(
        modifier = modifier,
        settings = settings,
        viewModel = viewModel,
    )
}

@Composable
private fun ThemeSettingsContent(
    modifier: Modifier,
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val monet = isMonetMode(settings.themeModeIndex)

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
    ) {
        SmallTitle(text = stringResource(R.string.settings_appearance))

        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = stringResource(R.string.theme_mode_label),
                summary = stringResource(R.string.theme_mode_summary),
                items = ThemeModeLabels.localizedLabels(),
                selectedIndex = settings.themeModeIndex,
                onSelectedIndexChange = { viewModel.setThemeMode(it) }
            )

            OverlayDropdownPreference(
                title = stringResource(R.string.back_gesture),
                summary = stringResource(
                    BackGestureModeSummaries[settings.backGestureModeIndex]
                        ?: R.string.back_mode_miuix_summary
                ),
                items = BackGestureModeLabels.localizedLabels(),
                selectedIndex = settings.backGestureModeIndex,
                onSelectedIndexChange = { viewModel.setBackGestureMode(it) }
            )

            SwitchPreference(
                title = stringResource(R.string.pure_black_label),
                summary = stringResource(R.string.pure_black_summary),
                checked = settings.pureBlackInDark,
                onCheckedChange = { viewModel.setPureBlackInDark(it) }
            )
        }

        SmallTitle(text = stringResource(R.string.settings_blur_section))

        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.blur_effect_label),
                summary = stringResource(R.string.blur_effect_summary),
                checked = settings.blurEnabled,
                onCheckedChange = { viewModel.setBlurEnabled(it) }
            )

            if (settings.blurEnabled) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.blur_style_label),
                    summary = stringResource(R.string.blur_style_summary),
                    items = BarBlurStyleLabels.localizedLabels(),
                    selectedIndex = settings.barBlurStyleIndex,
                    onSelectedIndexChange = { viewModel.setBarBlurStyle(it) }
                )
            }
        }

        if (monet) {
            SmallTitle(text = stringResource(R.string.theme_key_color_section))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.theme_key_color_label),
                        summary = stringResource(R.string.theme_key_color_summary),
                        items = KeyColorLabels.localizedLabels(),
                        selectedIndex = settings.keyColorIndex,
                        onSelectedIndexChange = { viewModel.setKeyColor(it) }
                    )

                    OverlayDropdownPreference(
                        title = stringResource(R.string.palette_label),
                        summary = stringResource(R.string.palette_summary),
                        items = PaletteStyleLabels.localizedLabels(),
                        selectedIndex = settings.paletteStyleIndex,
                        onSelectedIndexChange = { viewModel.setPaletteStyle(it) }
                    )

                    OverlayDropdownPreference(
                        title = stringResource(R.string.spec_label),
                        summary = stringResource(R.string.spec_summary),
                        items = ColorSpecLabels.localizedLabels(),
                        selectedIndex = settings.colorSpecIndex,
                        onSelectedIndexChange = { viewModel.setColorSpec(it) }
                    )
                }
            }
        }

        SmallTitle(text = stringResource(R.string.settings_bottom_bar))

        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.floating_bottom_bar_label),
                summary = stringResource(R.string.floating_bottom_bar_summary),
                checked = settings.floatingBottomBar,
                onCheckedChange = { viewModel.setFloatingBottomBar(it) }
            )

            if (settings.floatingBottomBar) {
                SwitchPreference(
                    title = stringResource(R.string.liquid_glass_label),
                    summary = stringResource(R.string.liquid_glass_summary),
                    checked = settings.liquidGlassBottomBar,
                    onCheckedChange = { viewModel.setLiquidGlassBottomBar(it) }
                )
            }

            OverlayDropdownPreference(
                title = stringResource(R.string.bottom_bar_style_label),
                summary = stringResource(R.string.bottom_bar_style_summary),
                items = listOf(
                    stringResource(R.string.bottom_bar_style_icon_only),
                    stringResource(R.string.bottom_bar_style_icon_text)
                ),
                selectedIndex = if (settings.bottomBarLabelVisible) 1 else 0,
                onSelectedIndexChange = { viewModel.setBottomBarLabelVisible(it == 1) }
            )
        }

        SmallTitle(text = stringResource(R.string.settings_ui_scale_section))

        // 拖动时用本地草稿即时反馈，松手或手动输入后才持久化
        var scaleDraft by remember(settings.uiScalePercent) {
            mutableFloatStateOf(settings.uiScalePercent.toFloat())
        }
        var showScaleDialog by remember { mutableStateOf(false) }
        var scaleInput by remember(showScaleDialog) {
            mutableStateOf(settings.uiScalePercent.toString())
        }
        var scaleInvalid by remember { mutableStateOf(false) }

        Card(modifier = Modifier.fillMaxWidth()) {
            SliderPreference(
                value = scaleDraft,
                onValueChange = { scaleDraft = it },
                title = stringResource(R.string.ui_scale_label),
                summary = stringResource(R.string.ui_scale_summary),
                // 右上角实时值：点按整行弹出输入框，可精确填写百分比
                valueText = stringResource(R.string.ui_scale_value, scaleDraft.roundToInt()),
                onClick = {
                    scaleInput = scaleDraft.roundToInt().toString()
                    scaleInvalid = false
                    showScaleDialog = true
                },
                valueRange = 80f..120f,
                steps = 3,
                showKeyPoints = true,
                onValueChangeFinished = {
                    viewModel.setUiScalePercent(scaleDraft.roundToInt())
                }
            )
        }

        if (showScaleDialog) {
            OverlayDialog(
                show = true,
                title = stringResource(R.string.settings_ui_scale_section),
                onDismissRequest = { showScaleDialog = false }
            ) {
                Column {
                    TextField(
                        value = scaleInput,
                        onValueChange = {
                            scaleInput = it.filter { ch -> ch.isDigit() }.take(3)
                            scaleInvalid = false
                        },
                        label = stringResource(R.string.ui_scale_input_label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    if (scaleInvalid) {
                        Text(
                            text = stringResource(R.string.ui_scale_invalid),
                            modifier = Modifier.padding(top = 4.dp),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                    DialogActions(
                        onCancel = { showScaleDialog = false },
                        confirmText = stringResource(R.string.action_save),
                        onConfirm = {
                            val parsed = scaleInput.toIntOrNull()
                            if (parsed != null && parsed in 80..120) {
                                scaleDraft = parsed.toFloat()
                                viewModel.setUiScalePercent(parsed)
                                showScaleDialog = false
                            } else {
                                scaleInvalid = true
                            }
                        }
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.theme_footer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
    }
}
