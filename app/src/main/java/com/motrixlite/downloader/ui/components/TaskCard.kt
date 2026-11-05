package com.motrixlite.downloader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.motrixlite.downloader.R
import com.motrixlite.downloader.data.model.DownloadTask
import com.motrixlite.downloader.data.model.TaskState
import com.motrixlite.downloader.engine.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCard(
    task: DownloadTask,
    onClick: () -> Unit,
    onTogglePause: () -> Unit,
    onRetry: () -> Unit,
    onOpenFile: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeBadge(task.fileName, task.mimeType)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StateLabel(task.state)
                        if (task.engine != com.motrixlite.downloader.data.model.DownloadEngine.BUILTIN) {
                            Text(
                                text = " · ${task.engine.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                            )
                        }
                        val detail = detailLine(task)
                        if (detail != null) {
                            Text(
                                text = " · $detail",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("查看详情") },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            },
                        )
                        if (task.state == TaskState.COMPLETED && task.destinationUri != null) {
                            DropdownMenuItem(
                                text = { Text("打开文件") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenFile()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("删除任务") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TaskProgress(task)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sizeLine(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TaskActionButton(task, onTogglePause, onRetry, onOpenFile)
            }
        }
    }
}

@Composable
private fun TaskProgress(task: DownloadTask) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(6.dp)
        .clip(RoundedCornerShape(3.dp))
    when {
        task.state == TaskState.SAVING -> {
            LinearProgressIndicator(
                progress = { task.publishProgress.coerceIn(0f, 1f) },
                modifier = modifier,
                color = Color(0xFF38BDF8),
            )
        }

        task.state == TaskState.COMPLETED -> {
            LinearProgressIndicator(
                progress = { 1f },
                modifier = modifier,
                color = Color(0xFF22C55E),
            )
        }

        task.totalBytes > 0 -> {
            LinearProgressIndicator(
                progress = { task.progressFraction },
                modifier = modifier,
            )
        }

        task.state.isRunning || task.state == TaskState.RETRYING -> {
            LinearProgressIndicator(modifier = modifier)
        }

        else -> {
            LinearProgressIndicator(progress = { 0f }, modifier = modifier)
        }
    }
}

@Composable
private fun TaskActionButton(
    task: DownloadTask,
    onTogglePause: () -> Unit,
    onRetry: () -> Unit,
    onOpenFile: () -> Unit,
) {
    when (task.state) {
        TaskState.QUEUED,
        TaskState.CONNECTING,
        TaskState.DOWNLOADING,
        TaskState.RETRYING,
        TaskState.WAITING_FOR_WIFI,
        -> IconButton(onClick = onTogglePause) {
            Icon(painterResource(R.drawable.ic_pause), contentDescription = "暂停")
        }

        TaskState.PAUSED -> IconButton(onClick = onTogglePause) {
            Icon(Icons.Default.PlayArrow, contentDescription = "继续")
        }

        TaskState.FAILED -> IconButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = "重试")
        }

        TaskState.COMPLETED -> IconButton(onClick = onOpenFile) {
            Icon(painterResource(R.drawable.ic_folder), contentDescription = "打开文件")
        }

        TaskState.SAVING -> CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
        )
    }
}

private fun detailLine(task: DownloadTask): String? = when (task.state) {
    TaskState.DOWNLOADING -> if (task.speedBytesPerSec > 0) Formatters.speed(task.speedBytesPerSec) else null
    TaskState.CONNECTING -> "解析链接"
    TaskState.RETRYING -> task.error
    TaskState.WAITING_FOR_WIFI -> "Wi-Fi 恢复后自动继续"
    TaskState.SAVING -> "${(task.publishProgress * 100).toInt().coerceIn(0, 100)}%"
    TaskState.COMPLETED -> task.completedAt?.let { Formatters.dateTime(it) }
    TaskState.FAILED -> task.error
    else -> null
}

private fun sizeLine(task: DownloadTask): String = when {
    task.state == TaskState.SAVING -> "正在保存到手机…"
    task.state == TaskState.COMPLETED -> Formatters.bytes(
        if (task.totalBytes > 0) task.totalBytes else task.downloadedBytes,
    )

    task.totalBytes > 0 -> "${Formatters.bytes(task.downloadedBytes)} / ${Formatters.bytes(task.totalBytes)}"
    task.downloadedBytes > 0 -> Formatters.bytes(task.downloadedBytes)
    else -> "等待获取文件信息…"
}
