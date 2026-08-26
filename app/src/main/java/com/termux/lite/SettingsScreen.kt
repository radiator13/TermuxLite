package com.termux.lite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    theme: TerminalTheme,
    fontSize: Int,
    fontId: String,
    extraKeys: Boolean,
    keepScreenOn: Boolean,
    onTheme: (TerminalTheme) -> Unit,
    onFontSize: (Int) -> Unit,
    onFont: (TerminalFont) -> Unit,
    onExtraKeys: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenUrl: ((String) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bgColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                color = theme.fgColor,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Close",
                color = theme.accentColor,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            SectionTitle(theme, "Theme")
            ThemeGrid(current = theme, onTheme = onTheme)

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(theme, "Terminal")
            LabeledValue(theme, "Font", TermFonts.byId(fontId).label)
            FontGrid(theme = theme, currentId = fontId, onFont = onFont)
            Spacer(modifier = Modifier.height(12.dp))
            LabeledValue(theme, "Font size", "${fontSize}sp")
            ThemedSlider(
                theme = theme,
                value = fontSize.toFloat(),
                range = Prefs.MIN_TEXT_SIZE.toFloat()..Prefs.MAX_TEXT_SIZE.toFloat(),
                steps = Prefs.MAX_TEXT_SIZE - Prefs.MIN_TEXT_SIZE - 1,
                onChange = { onFontSize(it.toInt()) }
            )
            ToggleRow(theme, "Extra keys", extraKeys, onExtraKeys)
            ToggleRow(theme, "Keep screen on", keepScreenOn, onKeepScreenOn)

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(theme, "About")
            LabeledValue(theme, "TermuxLite", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            LabeledValue(theme, "Tagline", "Termux, but lite")
            LabeledValue(theme, "Architecture", "arm64-v8a (aarch64)")
            LabeledValue(theme, "UI Framework", "100% Jetpack Compose")
            LabeledValue(theme, "Codebase", "~4.0k SLOC")
            LabeledValue(theme, "License", "GNU GPLv3")

            if (onOpenUrl != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.keyColor)
                        .clickable { onOpenUrl("https://github.com/radiator13/TermuxLite") }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Source Code",
                        color = theme.fgColor,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "radiator13/TermuxLite ↗",
                        color = theme.accentColor,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(theme: TerminalTheme, text: String) {
    Text(
        text = text,
        color = theme.accentColor,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun LabeledValue(theme: TerminalTheme, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = theme.fgColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = theme.fgColor.copy(alpha = 0.85f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun FontGrid(
    theme: TerminalTheme,
    currentId: String,
    onFont: (TerminalFont) -> Unit
) {
    val context = LocalContext.current
    val rows = TermFonts.all.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { item ->
                    val selected = item.id == currentId
                    val family = FontFamily(
                        typeface = TermFonts.typeface(context, item.id)
                    )
                    val shape = RoundedCornerShape(6.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(theme.keyColor)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) theme.accentColor else theme.fgColor.copy(alpha = 0.35f),
                                shape = shape
                            )
                            .clickable { onFont(item) }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = item.label,
                            color = theme.fgColor,
                            fontSize = 12.sp,
                            fontFamily = family,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = item.sample,
                            color = theme.fgColor.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontFamily = family,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemeGrid(current: TerminalTheme, onTheme: (TerminalTheme) -> Unit) {
    val rows = TermThemes.all.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { item ->
                    ThemeSwatch(
                        theme = item,
                        selected = item.id == current.id,
                        onClick = { onTheme(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    theme: TerminalTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(theme.bgColor)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) theme.accentColor else theme.fgColor.copy(alpha = 0.35f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            theme.ansi.take(6).forEach { c ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color(c), RoundedCornerShape(2.dp))
                )
            }
        }
        Text(
            text = theme.label,
            color = theme.fgColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ThemedSlider(
    theme: TerminalTheme,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps.coerceAtLeast(0),
        colors = SliderDefaults.colors(
            thumbColor = theme.accentColor,
            activeTrackColor = theme.accentColor,
            inactiveTrackColor = theme.keyActiveColor
        )
    )
}

@Composable
private fun ToggleRow(
    theme: TerminalTheme,
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = theme.fgColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = theme.accentColor,
                checkedTrackColor = theme.accentColor.copy(alpha = 0.5f)
            )
        )
    }
}
