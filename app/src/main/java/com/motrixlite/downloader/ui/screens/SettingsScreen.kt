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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motrixlite.downloader.BuildConfig
import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.DownloadEngine
import com.motrixlite.downloader.data.model.SaveMode
import com.motrixlite.downloader.data.model.ThemeMode
import com.motrixlite.downloader.engine.Formatters
import com.motrixlite.downloader.engine.native.EngineAvailability
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
    val engineAvailability by vm.engineAvailability.collectAsStateWithLifecycle()
    var showLicenseDialog by remember { mutableStateOf(false) }
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
            SectionCard("下载引擎（仅影响新建任务）") {
                RadioOptionRow(
                    title = "内置引擎（OkHttp）",
                    subtitle = "稳定、体积小；支持全局限速、断点续传、多连接",
                    selected = settings.downloadEngine == DownloadEngine.BUILTIN,
                    onClick = { vm.setDownloadEngine(DownloadEngine.BUILTIN) },
                )
                RadioOptionRow(
                    title = "Gopeed（GPL-3.0）" + if (engineAvailability.gopeed) "" else " · 当前 APK 不可用",
                    subtitle = if (engineAvailability.gopeed) {
                        "Go 内核（AAR ${engineAvailability.gopeedClass ?: ""}），多协议能力；当前 REST API 不支持全局限速"
                    } else {
                        "未打包 libgopeed.aar，需按 native/gopeed 脚本构建后重新打包"
                    },
                    selected = settings.downloadEngine == DownloadEngine.GOPEED,
                    enabled = engineAvailability.gopeed,
                    onClick = { vm.setDownloadEngine(DownloadEngine.GOPEED) },
                )
                RadioOptionRow(
                    title = "aria2（GPL-2.0+）" + if (engineAvailability.aria2) "" else " · 当前 APK 不可用",
                    subtitle = if (engineAvailability.aria2) {
                        "子进程 + JSON-RPC；支持全局限速、多连接、断点续传"
                    } else {
                        "未打包 libaria2c.so，需按 native/aria2 脚本获取后重新打包"
                    },
                    selected = settings.downloadEngine == DownloadEngine.ARIA2,
                    enabled = engineAvailability.aria2,
                    onClick = { vm.setDownloadEngine(DownloadEngine.ARIA2) },
                )
                Text(
                    "切换只影响之后新建的任务；已有任务保持创建时的引擎，不受影响。两个原生内核会显著增大 APK 体积。" +
                        "Gopeed/aria2 为 GPL 组件，本应用整体按 GPLv3 开源（详见「关于 → 开源许可」）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

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
                InfoRow("内置引擎", "自研 OkHttp 分段下载")
                InfoRow("Gopeed", if (engineAvailability.gopeed) "已打包（AAR）" else "未打包")
                InfoRow("aria2", if (engineAvailability.aria2) "已打包（arm64）" else "未打包")
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
                            "开源许可 / 源码获取（GPLv3）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "本应用包含 Gopeed（GPL-3.0）与 aria2（GPL-2.0-or-later）组件，因此整体按 GNU GPLv3 分发；" +
                                "完整源码、构建脚本与许可证文本见公开仓库与 Release 源码归档。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { showLicenseDialog = true }) {
                            Text("查看完整许可说明")
                        }
                    }
                }
            }
        }
    }

    if (showLicenseDialog) {
        LicenseDialog(
            onDismiss = { showLicenseDialog = false },
            onOpenSource = { vm.openSourceCode() },
        )
    }
}

@Composable
private fun LicenseDialog(
    onDismiss: () -> Unit,
    onOpenSource: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开源许可与源码获取") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "本应用（Motrix Lite）整体按 GNU General Public License v3.0 发布。\n\n" +
                        "包含的 GPL 组件：\n" +
                        "• Gopeed v1.9.3 — GPL-3.0，源码：https://github.com/GopeedLab/gopeed\n" +
                        "• aria2 1.37.0（arm64 预编译二进制）— GPL-2.0-or-later（含 OpenSSL 例外），源码：https://github.com/aria2/aria2\n\n" +
                        "对应源码与构建脚本：\n" +
                        "https://github.com/Chenzhenyan0/motrix-lite\n" +
                        "（仓库 native/ 目录包含 AAR 构建脚本与 aria2 获取/校验脚本；Release 页面附源码归档与许可证文件。）\n\n" +
                        "许可证全文（GPLv3、GPLv2、aria2 COPYING、Gopeed LICENSE、OpenSSL 例外）已打包在 APK 的 assets/licenses/ 中，" +
                        "并随 Release 源码归档提供。若需要物理介质源码，请通过仓库 Issue 联系，我们提供至少三年有效的书面要约。\n\n" +
                        "本应用为独立实现，非 Motrix 官方产品，也未使用 Motrix 源代码。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSource) { Text("打开源码仓库") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
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
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
