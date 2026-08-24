package com.lumenconnection.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumenconnection.music.Graph
import com.lumenconnection.music.R
import com.lumenconnection.music.config.DensityMode
import com.lumenconnection.music.config.LanguageMode
import com.lumenconnection.music.config.ThemeMode
import com.lumenconnection.music.ui.theme.AllPalettes
import com.lumenconnection.music.ui.theme.LumenText
import com.lumenconnection.music.ui.theme.LumenTheme
import com.lumenconnection.music.ui.theme.paletteById
import kotlinx.coroutines.launch

/**
 * Raiz da UI. Na fase 0 mostra o painel de Aparência, que já exercita todo o
 * design system portado do desktop; na fase 1 ele vira a SettingsScreen e o
 * AppRoot passa a hospedar a navegação (bottom nav + drawer).
 */
@Composable
fun AppRoot() {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    val settings = Graph.settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val palette by settings.palette.collectAsStateWithLifecycle(initialValue = "lumen")
    val mode by settings.mode.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
    val density by settings.density.collectAsStateWithLifecycle(initialValue = DensityMode.Comfortable)
    val reduceMotion by settings.reduceMotion.collectAsStateWithLifecycle(initialValue = false)
    val language by settings.language.collectAsStateWithLifecycle(initialValue = LanguageMode.System)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.app)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(dimens.windowMargin),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingLg),
    ) {
        Text(stringResource(R.string.settings_appearance), style = LumenText.title)

        LumenCard {
            Text(stringResource(R.string.settings_palette), style = LumenText.micro)
            Row(
                modifier = Modifier.padding(top = dimens.spacingSm),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                AllPalettes.forEach { def ->
                    PaletteSwatch(
                        selected = def.id == palette,
                        onClick = { scope.launch { settings.setPalette(def.id) } },
                        accent = def.dark.accent,
                    )
                }
            }
            Text(
                paletteById(palette).label,
                style = LumenText.bodySm,
                modifier = Modifier.padding(top = dimens.spacingSm),
            )
        }

        LumenCard {
            Text(stringResource(R.string.settings_mode), style = LumenText.micro)
            ChipRow(
                options = listOf(
                    ThemeMode.System to stringResource(R.string.mode_system),
                    ThemeMode.Dark to stringResource(R.string.mode_dark),
                    ThemeMode.Light to stringResource(R.string.mode_light),
                    ThemeMode.HighContrast to stringResource(R.string.mode_high_contrast),
                ),
                selected = mode,
                onSelect = { chosen ->
                    scope.launch {
                        // Guarda de qual base o HC deve ser derivado, como o
                        // parâmetro hcFromLight do buildTokens do desktop.
                        if (chosen == ThemeMode.HighContrast) {
                            settings.setHcFromLight(mode == ThemeMode.Light)
                        }
                        settings.setMode(chosen)
                    }
                },
            )
        }

        LumenCard {
            Text(stringResource(R.string.settings_density), style = LumenText.micro)
            ChipRow(
                options = listOf(
                    DensityMode.Comfortable to stringResource(R.string.density_comfortable),
                    DensityMode.Compact to stringResource(R.string.density_compact),
                ),
                selected = density,
                onSelect = { scope.launch { settings.setDensity(it) } },
            )
        }

        LumenCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_reduce_motion), style = LumenText.body)
                    Text(stringResource(R.string.settings_reduce_motion_hint), style = LumenText.bodySm)
                }
                Switch(
                    checked = reduceMotion,
                    onCheckedChange = { scope.launch { settings.setReduceMotion(it) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccent,
                        checkedTrackColor = colors.accent,
                    ),
                )
            }
        }

        LumenCard {
            Text(stringResource(R.string.settings_language), style = LumenText.micro)
            ChipRow(
                options = listOf(
                    LanguageMode.System to stringResource(R.string.language_system),
                    LanguageMode.PtBr to "Português (BR)",
                    LanguageMode.En to "English",
                ),
                selected = language,
                onSelect = { chosen ->
                    scope.launch {
                        settings.setLanguage(chosen)
                        // Trocar de idioma exige recriar a Activity para os
                        // recursos serem relidos — equivalente Android do
                        // retranslate ao vivo do desktop.
                        (context as? android.app.Activity)?.recreate()
                    }
                },
            )
        }
    }
}

@Composable
private fun LumenCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusCard))
            .background(colors.card)
            .border(dimens.borderWidth, colors.border, RoundedCornerShape(dimens.radiusCard))
            .padding(dimens.spacing),
        content = content,
    )
}

@Composable
private fun PaletteSwatch(selected: Boolean, accent: Color, onClick: () -> Unit) {
    val colors = LumenTheme.colors
    Box(
        modifier = Modifier
            .size(if (selected) 34.dp else 28.dp)
            .clip(CircleShape)
            .background(accent)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) colors.text else colors.border,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = LumenTheme.colors
    val dimens = LumenTheme.dimens
    FlowRow(
        modifier = Modifier.padding(top = dimens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Text(
                text = label,
                style = LumenText.body.copy(
                    color = if (isSelected) colors.onAccent else colors.text,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radiusWidget))
                    .background(if (isSelected) colors.accent else colors.input)
                    .border(
                        dimens.borderWidth,
                        if (isSelected) colors.accent else colors.border,
                        RoundedCornerShape(dimens.radiusWidget),
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = dimens.btnPadH, vertical = dimens.btnPadV),
            )
        }
    }
}
