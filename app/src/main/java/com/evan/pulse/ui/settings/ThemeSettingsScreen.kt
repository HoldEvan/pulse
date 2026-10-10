package com.evan.pulse.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evan.pulse.R
import com.evan.pulse.data.settings.AppSettings
import com.evan.pulse.ui.l10n.localizedLabels
import com.evan.pulse.ui.theme.ColorSpecLabels
import com.evan.pulse.ui.theme.PaletteStyleLabels
import com.evan.pulse.ui.theme.ThemeKeyColors
import com.evan.pulse.ui.theme.ThemeModeLabels
import com.evan.pulse.ui.theme.isMonetMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val KeyColorLabels: List<Int> =
    listOf(R.string.theme_follow_system_color) + ThemeKeyColors.map { it.first }

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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
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
