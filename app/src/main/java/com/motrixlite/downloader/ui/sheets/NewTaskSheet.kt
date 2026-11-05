package com.motrixlite.downloader.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.SaveMode
import com.motrixlite.downloader.engine.Formatters
import com.motrixlite.downloader.engine.HttpProbe
import com.motrixlite.downloader.ui.components.InfoRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    probeLink: suspend (String) -> Result<HttpProbe.Metadata>,
    onStart: (url: String, fileName: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var url by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var parsed by remember { mutableStateOf<HttpProbe.Metadata?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val trimmed = url.trim()
    val isValid = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)

    fun parse() {
        if (!isValid || parsing) return
        parsing = true
        error = null
        scope.launch {
            val result = probeLink(trimmed)
            parsing = false
            result.onSuccess { metadata ->
                parsed = metadata
                if (fileName.isBlank()) fileName = metadata.fileName
            }.onFailure { exception ->
                error = exception.message ?: "解析链接失败，请检查网络与地址"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "新建下载任务",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "仅支持 HTTP/HTTPS 直链；磁力链接与 BT 种子暂不支持。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    parsed = null
                    error = null
                },
                label = { Text("下载链接") },
                placeholder = { Text("https://example.com/file.zip") },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        clipboard.getText()?.text?.trim()?.let { text ->
                            if (text.startsWith("http://", true) || text.startsWith("https://", true)) {
                                url = text
                                parsed = null
                                error = null
                            } else {
                                error = "剪贴板内容不是 HTTP/HTTPS 链接"
                            }
                        }
                    },
                ) { Text("粘贴") }
                TextButton(
                    onClick = {
                        url = ""
                        fileName = ""
                        parsed = null
                        error = null
                    },
                    enabled = url.isNotBlank(),
                ) { Text("清空") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { parse() },
                    enabled = isValid && !parsing,
                ) {
                    if (parsing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (parsing) "解析中…" else "解析链接")
                }
                Spacer(Modifier.width(12.dp))
                if (parsed != null) {
                    Text(
                        "断点续传：" + if (parsed!!.supportsRanges) "支持" else "服务器不支持",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            parsed?.let { metadata ->
                Spacer(Modifier.height(12.dp))
                Card {
                    Column(Modifier.padding(12.dp)) {
                        InfoRow("文件名", metadata.fileName)
                        InfoRow(
                            "大小",
                            if (metadata.totalBytes > 0) Formatters.bytes(metadata.totalBytes) else "未知（不支持断点续传）",
                        )
                        InfoRow("类型", metadata.mimeType)
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("文件名（留空则使用服务器/链接中的文件名）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "下载引擎：${settings.downloadEngine.displayName}" +
                    when (settings.downloadEngine) {
                        com.motrixlite.downloader.data.model.DownloadEngine.BUILTIN -> "（支持全局限速）"
                        com.motrixlite.downloader.data.model.DownloadEngine.GOPEED -> "（GPL-3.0；REST API 暂不支持全局限速）"
                        com.motrixlite.downloader.data.model.DownloadEngine.ARIA2 -> "（GPL-2.0+；子进程 JSON-RPC）"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "保存到：" + if (settings.saveMode == SaveMode.MEDIASTORE) {
                    "Download/MotrixLite（系统下载目录）"
                } else {
                    "自定义文件夹（SAF）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "每任务连接数：${settings.connectionsPerTask} · 全局并发：${settings.maxConcurrentTasks}" +
                    if (settings.speedLimitKbPerSec > 0) {
                        " · 限速：${settings.speedLimitKbPerSec} KB/s"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (isValid) {
                            onStart(trimmed, fileName.trim().ifBlank { null })
                        } else {
                            error = "请输入有效的 HTTP/HTTPS 下载链接"
                        }
                    },
                ) { Text("开始下载") }
            }
        }
    }
}
