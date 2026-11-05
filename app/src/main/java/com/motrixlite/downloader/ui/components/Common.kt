package com.motrixlite.downloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motrixlite.downloader.data.model.TaskState
import java.util.Locale

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FileTypeBadge(fileName: String, mimeType: String, size: Dp = 44.dp) {
    val extension = fileName.substringAfterLast('.', "").uppercase(Locale.US).take(4).ifBlank { "FILE" }
    val color = when {
        mimeType.startsWith("video") -> Color(0xFFF97316)
        mimeType.startsWith("audio") -> Color(0xFF8B5CF6)
        mimeType.startsWith("image") -> Color(0xFF10B981)
        mimeType.startsWith("text") -> Color(0xFF64748B)
        mimeType == "application/pdf" -> Color(0xFFEF4444)
        mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("7z") ||
            mimeType.contains("gzip") || mimeType.contains("tar") -> Color(0xFFEAB308)
        mimeType.contains("android.package") -> Color(0xFF22C55E)
        else -> Color(0xFF3B82F6)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = extension,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun StateLabel(state: TaskState) {
    Text(
        text = stateLabel(state),
        style = MaterialTheme.typography.labelMedium,
        color = stateColor(state),
    )
}

fun stateLabel(state: TaskState): String = when (state) {
    TaskState.QUEUED -> "排队中"
    TaskState.CONNECTING -> "正在连接…"
    TaskState.DOWNLOADING -> "下载中"
    TaskState.PAUSED -> "已暂停"
    TaskState.WAITING_FOR_WIFI -> "等待 Wi-Fi"
    TaskState.RETRYING -> "正在重试"
    TaskState.SAVING -> "正在保存"
    TaskState.COMPLETED -> "已完成"
    TaskState.FAILED -> "下载失败"
}

@Composable
fun stateColor(state: TaskState): Color = when (state) {
    TaskState.COMPLETED -> Color(0xFF22C55E)
    TaskState.FAILED -> MaterialTheme.colorScheme.error
    TaskState.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
    TaskState.WAITING_FOR_WIFI -> Color(0xFFF59E0B)
    TaskState.SAVING -> Color(0xFF38BDF8)
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun SpeedChart(history: List<Long>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            if (history.size < 2) return@Canvas
            val maxValue = (history.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
            val stepX = size.width / (history.size - 1).toFloat()
            val path = Path()
            history.forEachIndexed { index, value ->
                val x = index * stepX
                val y = size.height - (value / maxValue) * (size.height * 0.85f) - size.height * 0.075f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fillPath = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                fillPath,
                Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            drawPath(
                path,
                lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = Offset(size.width, size.height - (history.last() / maxValue) * (size.height * 0.85f) - size.height * 0.075f),
            )
        }
    }
}

@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

@Composable
fun HorizontalRowSpacer() {
    Spacer(Modifier.width(8.dp))
}

@Composable
fun SectionVerticalSpacer() {
    Spacer(Modifier.height(12.dp))
}
