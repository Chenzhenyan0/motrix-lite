package com.motrixlite.downloader.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.motrixlite.downloader.R
import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.DownloadTask
import com.motrixlite.downloader.data.model.SaveMode
import com.motrixlite.downloader.data.model.TaskState
import com.motrixlite.downloader.engine.Formatters
import com.motrixlite.downloader.network.NetworkMonitor
import com.motrixlite.downloader.ui.components.TaskCard
import com.motrixlite.downloader.ui.components.stateLabel
import com.motrixlite.downloader.ui.screens.SettingsContent
import com.motrixlite.downloader.ui.sheets.NewTaskSheet
import com.motrixlite.downloader.ui.sheets.TaskDetailSheet

private val FILTER_LABELS = listOf("全部", "未完成", "已完成", "失败")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val network by vm.networkStatus.collectAsStateWithLifecycle()
    val notificationGranted by vm.notificationGranted.collectAsStateWithLifecycle()
    val batteryOptimized by vm.batteryOptimized.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var showNewTask by rememberSaveable { mutableStateOf(false) }
    var detailTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteFileToo by rememberSaveable { mutableStateOf(true) }
    var topMenuExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onNotificationPermissionResult(granted) }

    LaunchedEffect(Unit) {
        vm.uiEvents.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is UiEvent.NeedNotificationPermission -> {
                    vm.markNotificationPermissionAsked()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshSystemState()
    }

    val detailTask = tasks.firstOrNull { it.id == detailTaskId }
    val pendingDeleteTask = tasks.firstOrNull { it.id == pendingDeleteId }
    val activeCount = tasks.count { it.state == TaskState.QUEUED || it.state.isRunning }
    val subtitle = when {
        !network.online -> "网络未连接 · 共 ${tasks.size} 个任务"
        network.wifi -> "Wi-Fi · 共 ${tasks.size} 个任务"
        network.cellular -> "移动网络 · 共 ${tasks.size} 个任务"
        else -> "共 ${tasks.size} 个任务"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Column {
                        Text(
                            text = "Motrix Lite",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = {
                                if (activeCount > 0) vm.pauseAll() else vm.resumeAll()
                            },
                        ) {
                            if (activeCount > 0) {
                                Icon(painterResource(R.drawable.ic_pause), contentDescription = "全部暂停")
                            } else {
                                Icon(
                                    painterResource(R.drawable.ic_download),
                                    contentDescription = "全部继续",
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { topMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = topMenuExpanded,
                                onDismissRequest = { topMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("清理已完成任务（保留文件）") },
                                    onClick = {
                                        topMenuExpanded = false
                                        vm.clearCompletedTasks()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("任务") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showNewTask = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("新建任务") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                selectedTab == 0 -> TaskListContent(
                    vm = vm,
                    tasks = tasks,
                    settings = settings,
                    network = network,
                    notificationGranted = notificationGranted,
                    batteryOptimized = batteryOptimized,
                    filterIndex = filterIndex,
                    onFilterChange = { filterIndex = it },
                    onTaskClick = { detailTaskId = it.id },
                    onAddNew = { showNewTask = true },
                )

                else -> SettingsContent(
                    vm = vm,
                    settings = settings,
                    notificationGranted = notificationGranted,
                    batteryOptimized = batteryOptimized,
                    network = network,
                )
            }
        }
    }

    if (showNewTask) {
        NewTaskSheet(
            settings = settings,
            onDismiss = { showNewTask = false },
            probeLink = { url -> vm.probeLink(url) },
            onStart = { url, fileName ->
                vm.startDownload(url, fileName)
                showNewTask = false
            },
        )
    }

    if (detailTask != null) {
        TaskDetailSheet(
            task = detailTask,
            settings = settings,
            onDismiss = { detailTaskId = null },
            onTogglePause = { vm.togglePause(detailTask) },
            onRetry = { vm.retry(detailTask) },
            onOpenFile = { vm.openFile(detailTask) },
            onDelete = {
                pendingDeleteId = detailTask.id
                deleteFileToo = detailTask.destinationUri != null
                detailTaskId = null
            },
        )
    }

    if (pendingDeleteTask != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除任务") },
            text = {
                Column {
                    Text("确定删除「${pendingDeleteTask.fileName}」吗？")
                    if (pendingDeleteTask.destinationUri != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deleteFileToo,
                                onCheckedChange = { deleteFileToo = it },
                            )
                            Text("同时从手机中删除已保存的文件")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteTask(pendingDeleteTask, deleteFileToo && pendingDeleteTask.destinationUri != null)
                        pendingDeleteId = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TaskListContent(
    vm: MainViewModel,
    tasks: List<DownloadTask>,
    settings: AppSettings,
    network: NetworkMonitor.Status,
    notificationGranted: Boolean,
    batteryOptimized: Boolean,
    filterIndex: Int,
    onFilterChange: (Int) -> Unit,
    onTaskClick: (DownloadTask) -> Unit,
    onAddNew: () -> Unit,
) {
    val filtered = tasks
        .filter { task ->
            when (filterIndex) {
                1 -> !task.state.isFinished && task.state != TaskState.FAILED
                2 -> task.state == TaskState.COMPLETED
                3 -> task.state == TaskState.FAILED
                else -> true
            }
        }
        .sortedByDescending { it.createdAt }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!notificationGranted) {
            item(key = "notification_banner") {
                NotificationDeniedBanner(onOpenSettings = { vm.openNotificationSettings() })
            }
        }
        if (batteryOptimized && !settings.hideSetupCard) {
            item(key = "setup_card") {
                BackgroundSetupCard(
                    onBatterySettings = { vm.openBatteryOptimizationSettings() },
                    onHyperOsSettings = { vm.openHyperOsBackgroundSettings() },
                    onDismiss = { vm.hideSetupCard() },
                )
            }
        }
        if (!network.online) {
            item(key = "offline_banner") {
                Text(
                    "当前网络未连接，下载任务会在网络恢复后自动重试（失败任务需手动重试）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FILTER_LABELS.forEachIndexed { index, label ->
                    FilterChip(
                        selected = filterIndex == index,
                        onClick = { onFilterChange(index) },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                EmptyTaskState(onAdd = onAddNew)
            }
        } else {
            items(filtered, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onClick = { onTaskClick(task) },
                    onTogglePause = { vm.togglePause(task) },
                    onRetry = { vm.retry(task) },
                    onOpenFile = { vm.openFile(task) },
                    onDelete = {
                        vm.deleteTask(task, deleteFile = task.destinationUri != null)
                    },
                )
            }
        }
        item(key = "footer") {
            Text(
                text = "仅支持 HTTP/HTTPS 直链 · 保存位置：" +
                    if (settings.saveMode == SaveMode.MEDIASTORE) {
                        "Download/MotrixLite"
                    } else {
                        "自定义文件夹"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyTaskState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_download),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(16.dp))
        Text("还没有下载任务", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "点击右下角「新建任务」，添加 HTTP/HTTPS 直链开始下载",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "当前版本暂不支持磁力链接 / BT 种子",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun NotificationDeniedBanner(onOpenSettings: () -> Unit) {
    com.motrixlite.downloader.ui.components.NoticeBanner(
        text = "通知权限未开启：后台下载会继续，但锁屏/下拉通知栏中的进度与快捷操作不可见。",
        icon = painterResource(R.drawable.ic_download),
        actionLabel = "去开启",
        onAction = onOpenSettings,
    )
}

@Composable
private fun BackgroundSetupCard(
    onBatterySettings: () -> Unit,
    onHyperOsSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "后台稳定运行建议（HyperOS）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "1. 电池优化设为「不限制」\n2. 允许「自启动」并关闭省电限制\n3. 在多任务界面下拉本应用卡片并锁定\n4. 允许通知，方便查看进度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onBatterySettings) { Text("电池优化") }
                TextButton(onClick = onHyperOsSettings) { Text("自启动/省电") }
                TextButton(onClick = onDismiss) { Text("不再提示") }
            }
        }
    }
}
