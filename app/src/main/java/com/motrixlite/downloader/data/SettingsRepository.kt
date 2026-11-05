package com.motrixlite.downloader.data

import android.content.Context
import com.motrixlite.downloader.data.model.AppSettings
import com.motrixlite.downloader.data.model.DownloadEngine
import com.motrixlite.downloader.data.model.SaveMode
import com.motrixlite.downloader.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户设置（SharedPreferences + StateFlow）。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(this) {
            val updated = coerce(transform(_settings.value))
            if (updated == _settings.value) return
            _settings.value = updated
            persist(updated)
        }
    }

    fun setMaxConcurrentTasks(value: Int) = update { it.copy(maxConcurrentTasks = value.coerceIn(1, 5)) }

    fun setConnections(value: Int) = update { it.copy(connectionsPerTask = value.coerceIn(1, 16)) }

    fun setSpeedLimitKb(value: Int) = update { it.copy(speedLimitKbPerSec = value.coerceIn(0, 102_400)) }

    fun setWifiOnly(value: Boolean) = update { it.copy(wifiOnly = value) }

    fun setAutoResume(value: Boolean) = update { it.copy(autoResumeUnfinished = value) }

    fun setNotifyOnComplete(value: Boolean) = update { it.copy(notifyOnComplete = value) }

    fun setThemeMode(value: ThemeMode) = update { it.copy(themeMode = value) }

    fun setDownloadEngine(value: DownloadEngine) = update { it.copy(downloadEngine = value) }

    fun setSaveMode(value: SaveMode) = update { it.copy(saveMode = value) }

    fun setSafTreeUri(uri: String?) = update { it.copy(safTreeUri = uri, saveMode = if (uri.isNullOrBlank()) it.saveMode else SaveMode.SAF) }

    fun markNotificationPermissionAsked() = update { it.copy(notificationPermissionAsked = true) }

    fun setHideSetupCard(value: Boolean) = update { it.copy(hideSetupCard = value) }

    private fun coerce(settings: AppSettings): AppSettings = settings.copy(
        maxConcurrentTasks = settings.maxConcurrentTasks.coerceIn(1, 5),
        connectionsPerTask = settings.connectionsPerTask.coerceIn(1, 16),
        speedLimitKbPerSec = settings.speedLimitKbPerSec.coerceIn(0, 102_400),
    )

    private fun load(): AppSettings = AppSettings(
        maxConcurrentTasks = prefs.getInt(KEY_MAX_CONCURRENT, 3).coerceIn(1, 5),
        connectionsPerTask = prefs.getInt(KEY_CONNECTIONS, 8).coerceIn(1, 16),
        speedLimitKbPerSec = prefs.getInt(KEY_SPEED_LIMIT, 0).coerceIn(0, 102_400),
        wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
        autoResumeUnfinished = prefs.getBoolean(KEY_AUTO_RESUME, true),
        notifyOnComplete = prefs.getBoolean(KEY_NOTIFY_COMPLETE, true),
        saveMode = runCatching {
            SaveMode.valueOf(prefs.getString(KEY_SAVE_MODE, SaveMode.MEDIASTORE.name)!!)
        }.getOrDefault(SaveMode.MEDIASTORE),
        safTreeUri = prefs.getString(KEY_SAF_TREE, null),
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM),
        notificationPermissionAsked = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false),
        hideSetupCard = prefs.getBoolean(KEY_HIDE_SETUP_CARD, false),
        downloadEngine = runCatching {
            DownloadEngine.valueOf(prefs.getString(KEY_DOWNLOAD_ENGINE, DownloadEngine.BUILTIN.name)!!)
        }.getOrDefault(DownloadEngine.BUILTIN),
    )

    private fun persist(settings: AppSettings) {
        prefs.edit()
            .putInt(KEY_MAX_CONCURRENT, settings.maxConcurrentTasks)
            .putInt(KEY_CONNECTIONS, settings.connectionsPerTask)
            .putInt(KEY_SPEED_LIMIT, settings.speedLimitKbPerSec)
            .putBoolean(KEY_WIFI_ONLY, settings.wifiOnly)
            .putBoolean(KEY_AUTO_RESUME, settings.autoResumeUnfinished)
            .putBoolean(KEY_NOTIFY_COMPLETE, settings.notifyOnComplete)
            .putString(KEY_SAVE_MODE, settings.saveMode.name)
            .putString(KEY_SAF_TREE, settings.safTreeUri)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putBoolean(KEY_NOTIFICATION_ASKED, settings.notificationPermissionAsked)
            .putBoolean(KEY_HIDE_SETUP_CARD, settings.hideSetupCard)
            .putString(KEY_DOWNLOAD_ENGINE, settings.downloadEngine.name)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "motrix_lite_settings"
        private const val KEY_MAX_CONCURRENT = "max_concurrent"
        private const val KEY_CONNECTIONS = "connections"
        private const val KEY_SPEED_LIMIT = "speed_limit"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_AUTO_RESUME = "auto_resume"
        private const val KEY_NOTIFY_COMPLETE = "notify_complete"
        private const val KEY_SAVE_MODE = "save_mode"
        private const val KEY_SAF_TREE = "saf_tree"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_NOTIFICATION_ASKED = "notification_asked"
        private const val KEY_HIDE_SETUP_CARD = "hide_setup_card"
        private const val KEY_DOWNLOAD_ENGINE = "download_engine"
    }
}
