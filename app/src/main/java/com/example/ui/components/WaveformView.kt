package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioEditMode
import com.example.ui.theme.WaveformActiveColor
import com.example.ui.theme.WaveformCutColor
import com.example.ui.theme.WaveformInactiveColor
import kotlin.math.abs

@Composable
fun WaveformView(
    waveformAmplitudes: FloatArray,
    totalDurationMs: Long,
    startMs: Long,
    endMs: Long,
    currentPlaybackMs: Long,
    editMode: AudioEditMode,
    onRangeChanged: (Long, Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = WaveformActiveColor
    val cutColor = WaveformCutColor
    val inactiveColor = WaveformInactiveColor
    val playbackHeadColor = Color.Yellow
    val handleColor = MaterialTheme.colorScheme.primary

    var canvasWidth by remember { mutableStateOf(1f) }
    var draggingHandle by remember { mutableStateOf<HandleType?>(null) }

    val safeDuration = if (totalDurationMs > 0) totalDurationMs else 1L

    val startFraction = (startMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val endFraction = (endMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val playbackFraction = (currentPlaybackMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
            .testTag("waveform_editor_container")
    ) {
        // Time badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeBadge(label = "البداية", timeMs = startMs, color = handleColor)
            
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (editMode == AudioEditMode.TRIM) activeColor.copy(alpha = 0.2f) else cutColor.copy(alpha = 0.2f),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                val selectedMs = if (editMode == AudioEditMode.TRIM) (endMs - startMs).coerceAtLeast(0) else (totalDurationMs - (endMs - startMs)).coerceAtLeast(0)
                Text(
                    text = "${if (editMode == AudioEditMode.TRIM) "المدة المتبقية" else "المدة بعد الحذف"}: ${formatTime(selectedMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (editMode == AudioEditMode.TRIM) activeColor else cutColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            TimeBadge(label = "النهاية", timeMs = endMs, color = handleColor)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(waveformAmplitudes, startMs, endMs, totalDurationMs) {
                    detectTapGestures { offset ->
                        if (canvasWidth > 0) {
                            val tapFraction = (offset.x / canvasWidth).coerceIn(0f, 1f)
                            val targetMs = (tapFraction * safeDuration).toLong()
                            onSeekTo(targetMs)
                        }
                    }
                }
                .pointerInput(waveformAmplitudes, startMs, endMs, totalDurationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val x = offset.x
                            val startX = startFraction * canvasWidth
                            val endX = endFraction * canvasWidth

                            draggingHandle = when {
                                abs(x - startX) < 60f -> HandleType.START
                                abs(x - endX) < 60f -> HandleType.END
                                x in startX..endX -> HandleType.START
                                else -> null
                            }
                        },
                        onDragEnd = { draggingHandle = null },
                        onDragCancel = { draggingHandle = null },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (canvasWidth > 0 && draggingHandle != null) {
                                val deltaFraction = dragAmount.x / canvasWidth
                                val deltaMs = (deltaFraction * safeDuration).toLong()

                                when (draggingHandle) {
                                    HandleType.START -> {
                                        val newStart = (startMs + deltaMs).coerceIn(0L, endMs - 500L)
                                        onRangeChanged(newStart, endMs)
                                    }
                                    HandleType.END -> {
                                        val newEnd = (endMs + deltaMs).coerceIn(startMs + 500L, safeDuration)
                                        onRangeChanged(startMs, newEnd)
                                    }
                                    null -> {}
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                canvasWidth = size.width
                val canvasHeight = size.height
                val barCount = waveformAmplitudes.size
                if (barCount == 0) return@Canvas

                val spacing = 3.dp.toPx()
                val totalBarSpace = size.width - (spacing * (barCount - 1))
                val barWidth = maxOf(2f, totalBarSpace / barCount)

                val startX = startFraction * size.width
                val endX = endFraction * size.width

                // 1. Draw Waveform Bars
                for (i in 0 until barCount) {
                    val x = i * (barWidth + spacing)
                    val amp = waveformAmplitudes[i]
                    val barHeight = maxOf(6f, amp * (canvasHeight * 0.75f))
                    val yTop = (canvasHeight - barHeight) / 2f

                    val isInSelectedRange = x in startX..endX

                    val barColor = when (editMode) {
                        AudioEditMode.TRIM -> {
                            if (isInSelectedRange) activeColor else inactiveColor.copy(alpha = 0.4f)
                        }
                        AudioEditMode.CUT_OUT -> {
                            if (isInSelectedRange) cutColor else activeColor
                        }
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, yTop),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }

                // 2. Draw Region Overlay Box
                if (editMode == AudioEditMode.TRIM) {
                    // Dim outside
                    drawRect(
                        color = Color.Black.copy(alpha = 0.35f),
                        topLeft = Offset(0f, 0f),
                        size = Size(startX, canvasHeight)
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.35f),
                        topLeft = Offset(endX, 0f),
                        size = Size(size.width - endX, canvasHeight)
                    )
                    // Highlight active
                    drawRect(
                        color = activeColor.copy(alpha = 0.12f),
                        topLeft = Offset(startX, 0f),
                        size = Size(endX - startX, canvasHeight)
                    )
                } else {
                    // Cut out region in red
                    drawRect(
                        color = cutColor.copy(alpha = 0.25f),
                        topLeft = Offset(startX, 0f),
                        size = Size(endX - startX, canvasHeight)
                    )
                }

                // 3. Draw Start Handle Line & Knob
                drawLine(
                    color = handleColor,
                    start = Offset(startX, 0f),
                    end = Offset(startX, canvasHeight),
                    strokeWidth = 4.dp.toPx()
                )
                drawCircle(
                    color = handleColor,
                    radius = 12.dp.toPx(),
                    center = Offset(startX, 16.dp.toPx())
                )

                // 4. Draw End Handle Line & Knob
                drawLine(
                    color = handleColor,
                    start = Offset(endX, 0f),
                    end = Offset(endX, canvasHeight),
                    strokeWidth = 4.dp.toPx()
                )
                drawCircle(
                    color = handleColor,
                    radius = 12.dp.toPx(),
                    center = Offset(endX, canvasHeight - 16.dp.toPx())
                )

                // 5. Draw Playback Head
                val playbackX = playbackFraction * size.width
                drawLine(
                    color = playbackHeadColor,
                    start = Offset(playbackX, 0f),
                    end = Offset(playbackX, canvasHeight),
                    strokeWidth = 3.dp.toPx()
                )
                drawCircle(
                    color = playbackHeadColor,
                    radius = 6.dp.toPx(),
                    center = Offset(playbackX, canvasHeight / 2f)
                )
            }
        }
    }
}

@Composable
private fun TimeBadge(label: String, timeMs: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = formatTime(timeMs),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

private enum class HandleType { START, END }

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000) / 100
    return String.format("%02d:%02d.%d", minutes, seconds, millis)
}
