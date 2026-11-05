package com.motrixlite.downloader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.motrixlite.downloader.BuildConfig
import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.SaveMode
import com.motrixlite.downloader.data.model.ThemeMode
import com.motrixlite.downloader.engine.Formatters
import com.motrixlite.downloader.network.NetworkMonitor
import com.motrixlite.downloader.ui.MainViewModel
import com.motrixlite.downloader.ui.components.InfoRow
import com.motrixlite.downloader.ui.components.SectionCard
import com.motrixlite.downloader.ui.components.SettingRow
import kotlin.math.roundToInt

@Composable
fun SettingsContent(
    vm: MainViewModel,
    settings: AppSettings,
    notificationGranted: Boolean,
    batteryOptimized: Boolean,
    network: NetworkMonitor.Status,
) {
    val context = LocalContext.current
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) vm.setSafFolder(uri)
    }
    val folderName = remember(settings.safTreeUri) {
        settings.safTreeUri?.let { raw ->
            runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(raw))?.name
            }.getOrNull()
        } ?: "未选择"
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("下载设置") {
                SliderSetting(
                    title = "同时下载任务数",
                    valueText = "${settings.maxConcurrentTasks} 个",
                    value = settings.maxConcurrentTasks.toFloat(),
                    valueRange = 1f..5f,
                    steps = 3,
                    onValueChange = { vm.setMaxConcurrentTasks(it.roundToInt()) },
                )
                SliderSetting(
                    title = "每个任务连接数",
                    valueText = "${settings.connectionsPerTask} 个连接",
                    value = settings.connectionsPerTask.toFloat(),
                    valueRange = 1f..16f,
                    steps = 14,
                    onValueChange = { vm.setConnections(it.roundToInt()) },
                )
                SliderSetting(
                    title = "全局限速",
                    valueText = if (settings.speedLimitKbPerSec <= 0) {
                        "不限速"
                    } else {
                        "${Formatters.bytes(settings.speedLimitKbPerSec.toLong() * 1024)}/s"
                    },
                    value = settings.speedLimitKbPerSec.toFloat(),
                    valueRange = 0f..20480f,
                    steps = 39,
                    onValueChange = { vm.setSpeedLimitKb((it / 512f).roundToInt() * 512) },
                )
                SettingRow(
                    title = "仅 Wi-Fi 下载",
                    subtitle = "移动网络下自动暂停，连回 Wi-Fi 后自动继续（当前网络：" +
                        when {
                            !network.online -> "未连接"
                            network.wifi -> "Wi-Fi"
                            network.cellular -> "移动网络"
                            else -> "其他"
                        } + "）",
                    trailing = {
                        Switch(
                            checked = settings.wifiOnly,
                            onCheckedChange = { vm.setWifiOnly(it) },
                        )
                    },
                )
                SettingRow(
                    title = "重启后自动恢复未完成任务",
                    subtitle = "关闭后，上次未完成的任务会保持暂停状态",
                    trailing = {
                        Switch(
                            checked = settings.autoResumeUnfinished,
                            onCheckedChange = { vm.setAutoResume(it) },
                        )
                    },
                )
            }
        }

        item {
            SectionCard("保存位置") {
                RadioOptionRow(
                    title = "系统下载目录（推荐）",
                    subtitle = "Download/MotrixLite，文件管理器中可直接找到，无需存储权限",
                    selected = settings.saveMode == SaveMode.MEDIASTORE,
                    onClick = { vm.setSaveMode(SaveMode.MEDIASTORE) },
                )
                RadioOptionRow(
                    title = "自定义文件夹（SAF）",
                    subtitle = "当前：$folderName",
                    selected = settings.saveMode == SaveMode.SAF,
                    onClick = { vm.setSaveMode(SaveMode.SAF) },
                )
                if (settings.saveMode == SaveMode.SAF) {
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { folderLauncher.launch(null) }) { Text("选择文件夹") }
                    if (settings.safTreeUri.isNullOrBlank()) {
                        Text(
                            "尚未选择文件夹：下载完成后会因保存失败而暂停，请先选择。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        item {
            SectionCard("通知与后台运行") {
                SettingRow(
                    title = "通知权限",
                    subtitle = if (notificationGranted) {
                        "已开启，可显示后台下载进度"
                    } else {
                        "未开启：下载仍会继续，但通知栏进度不可见（降级运行）"
                    },
                    trailing = {
                        TextButton(onClick = { vm.openNotificationSettings() }) {
                            Text(if (notificationGranted) "管理" else "开启")
                        }
                    },
                )
                SettingRow(
                    title = "完成/失败提醒",
                    subtitle = "下载完成后发送通知",
                    trailing = {
                        Switch(
                            checked = settings.notifyOnComplete,
                            onCheckedChange = { vm.setNotifyOnComplete(it) },
                        )
                    },
                )
                SettingRow(
                    title = "电池优化",
                    subtitle = if (!batteryOptimized) {
                        "已忽略电池优化，后台下载更稳定"
                    } else {
                        "未设置：HyperOS 可能在息屏后限制 / 杀死下载进程"
                    },
                    trailing = {
                        TextButton(onClick = { vm.openBatteryOptimizationSettings() }) { Text("去设置") }
                    },
                )
                SettingRow(
                    title = "HyperOS 自启动 / 省电策略",
                    subtitle = "MIUI/HyperOS 无公开接口，需要手动允许后台运行",
                    trailing = {
                        TextButton(onClick = { vm.openHyperOsBackgroundSettings() }) { Text("去设置") }
                    },
                )
                if (!network.wifi && network.cellular) {
                    SettingRow(
                        title = "当前使用移动网络",
                        subtitle = "大文件下载建议切换到 Wi-Fi",
                        trailing = {
                            TextButton(onClick = { vm.openWifiSettings() }) { Text("Wi-Fi 设置") }
                        },
                    )
                }
                Text(
                    "说明：Android 15 对 dataSync 类型前台服务有 6 小时/24 小时限制，触发限制时任务会自动暂停，重新打开应用即可继续。HyperOS 的「省电策略」「自启动」「后台弹出界面」等开关无法通过代码直接修改，需要按上方入口手动配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            SectionCard("外观") {
                ThemeMode.entries.forEach { mode ->
                    RadioOptionRow(
                        title = when (mode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色模式"
                            ThemeMode.DARK -> "深色模式"
                        },
                        subtitle = null,
                        selected = settings.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                    )
                }
            }
        }

        item {
            SectionCard("关于") {
                InfoRow("版本", BuildConfig.VERSION_NAME)
                InfoRow("下载内核", "自研 OkHttp 分段下载（未集成 aria2）")
                InfoRow("支持协议", "HTTP / HTTPS（磁力、BT 暂不支持）")
                InfoRow("应用性质", "参考 Motrix 产品思路的独立 Android 实现")
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "开源合规说明",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• 本应用是独立的 Android 工程，不包含 Motrix（Electron 桌面应用）代码，也不分发 aria2 可执行文件。\n" +
                                "• Motrix 项目采用 MIT 许可证，本应用仅参考其交互与功能思路。\n" +
                                "• aria2 采用 GPLv2 许可证；本应用选择自研下载内核，因此不触发 GPLv2 的分发义务。若后续集成 aria2，需要随包提供完整源码与许可证说明。\n" +
                                "• 本应用使用 Kotlin、Jetpack Compose、OkHttp（Apache-2.0）等开源组件。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RadioOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
