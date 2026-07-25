package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AudioPlayerControls(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onRewind5s: () -> Unit,
    onForward5s: () -> Unit,
    isLooping: Boolean,
    onLoopToggle: (Boolean) -> Unit,
    volumeMultiplier: Float,
    onVolumeChange: (Float) -> Unit,
    fadeInMs: Int,
    onFadeInChange: (Int) -> Unit,
    fadeOutMs: Int,
    onFadeOutChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedEffects by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_player_controls_card"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main playback bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Loop toggle
                IconToggleButton(
                    checked = isLooping,
                    onCheckedChange = onLoopToggle,
                    modifier = Modifier.testTag("loop_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Loop,
                        contentDescription = "تكرار التشغيل",
                        tint = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Rewind 5s
                IconButton(
                    onClick = onRewind5s,
                    modifier = Modifier.testTag("rewind_5s_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "إرجاع 5 ثوانٍ"
                    )
                }

                // Play/Pause Big Button
                FilledIconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("play_pause_button"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل معاينة الصوت",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Forward 5s
                IconButton(
                    onClick = onForward5s,
                    modifier = Modifier.testTag("forward_5s_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "تقديم 5 ثوانٍ"
                    )
                }

                // Advanced Effects toggle
                IconButton(
                    onClick = { showAdvancedEffects = !showAdvancedEffects },
                    modifier = Modifier.testTag("effects_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "مؤثرات إضافية",
                        tint = if (showAdvancedEffects) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Advanced audio effects panel (Fade in / Fade out / Volume)
            AnimatedVisibility(visible = showAdvancedEffects) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    // Volume Boost
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "مستوى الصوت: ${(volumeMultiplier * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(130.dp)
                        )
                        Slider(
                            value = volumeMultiplier,
                            onValueChange = onVolumeChange,
                            valueRange = 0.2f..2.5f,
                            modifier = Modifier.weight(1f).testTag("volume_slider"),
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    // Fade In
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تلاشي بالدخول: ${fadeInMs / 1000f}s",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(130.dp)
                        )
                        Slider(
                            value = fadeInMs.toFloat(),
                            onValueChange = { onFadeInChange(it.toInt()) },
                            valueRange = 0f..5000f,
                            modifier = Modifier.weight(1f).testTag("fade_in_slider")
                        )
                    }

                    // Fade Out
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تلاشي بالخروج: ${fadeOutMs / 1000f}s",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(130.dp)
                        )
                        Slider(
                            value = fadeOutMs.toFloat(),
                            onValueChange = { onFadeOutChange(it.toInt()) },
                            valueRange = 0f..5000f,
                            modifier = Modifier.weight(1f).testTag("fade_out_slider")
                        )
                    }
                }
            }
        }
    }
}
