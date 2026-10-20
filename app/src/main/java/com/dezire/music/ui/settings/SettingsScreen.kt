package com.dezire.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.data.ThemeMode
import com.dezire.music.ui.components.SectionHeader
import com.dezire.music.ui.theme.DezireRed
import com.dezire.music.ui.theme.dezireColors

/*
 * Settings screen, opened from the Library header. Persisted appearance
 * options and library maintenance.
 */
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val metadataState by settingsViewModel.metadataState.collectAsStateWithLifecycle()
    val colors = dezireColors()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
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
                    text = "Settings",
                    color = colors.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(48.dp))
            }
        }

        item {
            SectionHeader(title = "Appearance")
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(vertical = 4.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    ThemeOptionRow(
                        label = modeLabel(mode),
                        selected = themeMode == mode,
                        onClick = { settingsViewModel.setThemeMode(mode) }
                    )
                }
            }
        }

        item {
            SectionHeader(title = "Playback")
        }

        item {
            PlaybackSettingsSection(
                settingsViewModel = settingsViewModel,
                onOpenEqualizer = onOpenEqualizer
            )
        }

        item {
            SectionHeader(title = "Library")
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rescan library",
                        color = colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Re-query MediaStore for new or removed tracks.",
                        color = colors.contentSecondary,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = onRescan,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DezireRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("Rescan")
                }
            }
        }

        item {
            SectionHeader(title = "Metadata")
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Last.fm metadata",
                        color = colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = metadataStatusText(
                            configured = settingsViewModel.lastFmConfigured,
                            state = metadataState
                        ),
                        color = colors.contentSecondary,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = settingsViewModel::refreshMetadata,
                    enabled = settingsViewModel.lastFmConfigured &&
                        metadataState != MetadataRefreshUiState.Refreshing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DezireRed,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (metadataState == MetadataRefreshUiState.Refreshing) {
                            "Refreshing…"
                        } else {
                            "Refresh"
                        }
                    )
                }
            }
        }

        item {
            SectionHeader(title = "About")
        }

        item {
            Text(
                text = "Dezire 1.0.0 — a local music player.\n" +
                    "Powered by MediaStore and Media3 ExoPlayer.\n" +
                    "Metadata enriched by Last.fm (key built in automatically).",
                color = colors.contentSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = dezireColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = DezireRed,
                unselectedColor = colors.onBackground
            )
        )
        Text(
            text = label,
            color = colors.onSurface,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

private fun modeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.DARK -> "Dark"
    ThemeMode.LIGHT -> "Light"
}

private fun metadataStatusText(
    configured: Boolean,
    state: MetadataRefreshUiState
): String = when {
    !configured -> "Not configured for this build."
    state is MetadataRefreshUiState.Refreshing -> "Updating artist info and tags…"
    state is MetadataRefreshUiState.Done -> state.message
    else -> "Artist bios, genres and MBIDs are fetched automatically."
}
@Composable
private fun PlaybackSettingsSection(
    settingsViewModel: SettingsViewModel,
    onOpenEqualizer: () -> Unit
) {
    val colors = dezireColors()
    val gaplessEnabled by settingsViewModel.gaplessPlaybackEnabled.collectAsStateWithLifecycle()
    val crossfadeEnabled by settingsViewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDuration by settingsViewModel.crossfadeDurationSeconds.collectAsStateWithLifecycle()
    val volumeNormalizationEnabled by settingsViewModel.volumeNormalizationEnabled.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp)
    ) {
        SwitchSettingRow(
            title = "Gapless playback",
            description = "Play consecutive tracks without unnecessary silence.",
            checked = gaplessEnabled,
            onCheckedChange = settingsViewModel::setGaplessPlaybackEnabled
        )
        CrossfadeSettingRow(
            enabled = crossfadeEnabled,
            durationSeconds = crossfadeDuration,
            onEnabledChange = settingsViewModel::setCrossfadeEnabled,
            onDurationChange = settingsViewModel::setCrossfadeDurationSeconds
        )
        SwitchSettingRow(
            title = "Volume normalization",
            description = "Reduce perceived loudness differences between tracks.",
            checked = volumeNormalizationEnabled,
            onCheckedChange = settingsViewModel::setVolumeNormalizationEnabled
        )
        EqualizerSettingRow(onClick = onOpenEqualizer)
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = dezireColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = colors.contentSecondary,
                fontSize = 12.sp
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DezireRed,
                uncheckedThumbColor = colors.onBackground,
                uncheckedTrackColor = colors.track
            )
        )
    }
}

@Composable
private fun CrossfadeSettingRow(
    enabled: Boolean,
    durationSeconds: Int,
    onEnabledChange: (Boolean) -> Unit,
    onDurationChange: (Int) -> Unit
) {
    val colors = dezireColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Crossfade",
                    color = colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Smoothly blend the end of one track into the next.",
                    color = colors.contentSecondary,
                    fontSize = 12.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = DezireRed,
                    uncheckedThumbColor = colors.onBackground,
                    uncheckedTrackColor = colors.track
                )
            )
        }
        if (enabled) {
            Spacer(Modifier.size(12.dp))
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Duration",
                        color = colors.onSurface,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "$durationSeconds s",
                        color = DezireRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                androidx.compose.material3.Slider(
                    value = durationSeconds.toFloat(),
                    onValueChange = { onDurationChange(it.toInt()) },
                    valueRange = 1f..12f,
                    steps = 10,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = DezireRed,
                        activeTrackColor = DezireRed,
                        inactiveTrackColor = colors.track
                    )
                )
            }
        }
    }
}

@Composable
private fun EqualizerSettingRow(onClick: () -> Unit) {
    val colors = dezireColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                text = "Adjust frequency bands and sound output.",
                color = colors.contentSecondary,
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = "Equalizer",
            tint = colors.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}
