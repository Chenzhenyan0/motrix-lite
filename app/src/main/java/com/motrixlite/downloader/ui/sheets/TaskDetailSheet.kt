package com.motrixlite.downloader.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.DownloadTask
import com.motrixlite.downloader.data.model.SaveMode
import com.motrixlite.downloader.data.model.TaskState
import com.motrixlite.downloader.engine.Formatters
import com.motrixlite.downloader.ui.components.FileTypeBadge
import com.motrixlite.downloader.ui.components.InfoRow
import com.motrixlite.downloader.ui.components.SpeedChart
import com.motrixlite.downloader.ui.components.stateColor
import com.motrixlite.downloader.ui.components.stateLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: DownloadTask,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onTogglePause: () -> Unit,
    onRetry: () -> Unit,
    onOpenFile: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeBadge(task.fileName, task.mimeType, size = 56.dp)
                Spacer(Modifier.padding(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stateLabel(task.state),
                            style = MaterialTheme.typography.labelMedium,
                            color = stateColor(task.state),
                        )
                        if (task.speedBytesPerSec > 0 && task.state.isRunning) {
                            Text(
                                " · ${Formatters.speed(task.speedBytesPerSec)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SpeedChart(task.speedHistory)
            Spacer(Modifier.height(12.dp))

            when {
                task.state == TaskState.SAVING -> {
                    LinearProgressIndicator(
                        progress = { task.publishProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "正在保存到手机… ${(task.publishProgress * 100).toInt().coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                task.totalBytes > 0 -> {
                    LinearProgressIndicator(
                        progress = { task.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${(task.progressFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${Formatters.bytes(task.downloadedBytes)} / ${Formatters.bytes(task.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                task.state.isRunning -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }

                else -> Unit
            }

            Spacer(Modifier.height(16.dp))
            Card {
                Column(Modifier.padding(14.dp)) {
                    InfoRow("状态", stateLabel(task.state))
                    if (task.speedBytesPerSec > 0 && task.state.isRunning) {
                        InfoRow("当前速度", Formatters.speed(task.speedBytesPerSec))
                    }
                    if (task.totalBytes > 0 && task.downloadedBytes < task.totalBytes &&
                        task.speedBytesPerSec > 0 && task.state.isRunning
                    ) {
                        InfoRow(
                            "剩余时间",
                            Formatters.eta(task.totalBytes - task.downloadedBytes, task.speedBytesPerSec),
                        )
                    }
                    InfoRow("大小", if (task.totalBytes > 0) Formatters.bytes(task.totalBytes) else "未知")
                    InfoRow(
                        "分段下载",
                        if (task.supportsRanges) {
                            "支持 Range（${task.segments.size} 段）"
                        } else {
                            "服务器不支持 Range（单连接）"
                        },
                    )
                    InfoRow(
                        "保存位置",
                        if (task.saveMode == SaveMode.MEDIASTORE) {
                            "Download/MotrixLite"
                        } else {
                            "自定义文件夹（SAF）"
                        },
                    )
                    InfoRow("创建时间", Formatters.dateTime(task.createdAt))
                    task.completedAt?.let { completedAt ->
                        InfoRow("完成时间", Formatters.dateTime(completedAt))
                    }
                    if (!task.error.isNullOrBlank()) {
                        InfoRow("错误信息", task.error)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        task.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (task.state) {
                    TaskState.COMPLETED -> Button(onClick = onOpenFile) { Text("打开文件") }
                    TaskState.PAUSED -> Button(onClick = onTogglePause) { Text("继续下载") }
                    TaskState.FAILED -> Button(onClick = onRetry) { Text("重试") }
                    TaskState.SAVING -> OutlinedButton(onClick = onDismiss) { Text("保存中…") }
                    else -> OutlinedButton(onClick = onTogglePause) { Text("暂停") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
            if (settings.saveMode == SaveMode.SAF && task.saveMode == SaveMode.SAF) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "提示：自定义 SAF 文件夹的访问权限可能被系统回收；若保存失败，请到设置中重新选择文件夹。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
