package com.dezire.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.playback.EqualizerManager
import com.dezire.music.ui.theme.DezireRed
import com.dezire.music.ui.theme.dezireColors

@Composable
fun EqualizerScreen(
    equalizerManager: EqualizerManager,
    onBack: () -> Unit
) {
    val colors = dezireColors()
    val state by equalizerManager.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.onBackground
                )
            }
            Text(
                text = "Equalizer",
                color = colors.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(48.dp))
        }

        if (!state.supported) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Equalizer not available",
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "The Equalizer API is not supported on this device or audio session.",
                    color = colors.contentSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(colors.surface, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Equalizer",
                                color = colors.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (state.enabled) "Enabled" else "Disabled",
                                color = colors.contentSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = { equalizerManager.setEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DezireRed,
                                uncheckedThumbColor = colors.onBackground,
                                uncheckedTrackColor = colors.track
                            )
                        )
                    }
                }

                if (state.presets.isNotEmpty()) {
                    item {
                        PresetSection(equalizerManager = equalizerManager, state = state, colors = colors)
                    }
                }

                item {
                    BandSection(equalizerManager = equalizerManager, state = state, colors = colors)
                }

                item {
                    Button(
                        onClick = { equalizerManager.reset() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DezireRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetSection(
    equalizerManager: EqualizerManager,
    state: com.dezire.music.playback.EqualizerState,
    colors: com.dezire.music.ui.theme.DezireThemeColors
) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.presets.firstOrNull { it.index == state.currentPreset }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Preset",
                color = colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = current?.name ?: "Custom",
                color = colors.contentSecondary,
                fontSize = 12.sp
            )
        }
        TextButton(onClick = { expanded = true }) {
            Text("Select", color = DezireRed)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name, color = colors.onSurface) },
                    onClick = {
                        equalizerManager.usePreset(preset.index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun BandSection(
    equalizerManager: EqualizerManager,
    state: com.dezire.music.playback.EqualizerState,
    colors: com.dezire.music.ui.theme.DezireThemeColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Frequency Bands",
            color = colors.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))
        state.bands.forEach { band ->
            BandSlider(equalizerManager, band, colors)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BandSlider(
    equalizerManager: EqualizerManager,
    band: com.dezire.music.playback.EqualizerBand,
    colors: com.dezire.music.ui.theme.DezireThemeColors
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val center = if (band.centerFreqHz >= 1000) {
                "${band.centerFreqHz / 1000} kHz"
            } else {
                "${band.centerFreqHz} Hz"
            }
            Text(text = center, color = colors.contentSecondary, fontSize = 11.sp)
            Text(text = "${band.currentGain / 100} dB", color = DezireRed, fontSize = 11.sp)
        }
        Slider(
            value = band.currentGain.toFloat(),
            onValueChange = { equalizerManager.setBandLevel(band.index, it.toInt().toShort()) },
            valueRange = band.minGain.toFloat()..band.maxGain.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = DezireRed,
                activeTrackColor = DezireRed,
                inactiveTrackColor = colors.track
            )
        )
    }
}
