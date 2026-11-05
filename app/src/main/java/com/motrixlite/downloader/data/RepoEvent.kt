package com.motrixlite.downloader.data

import com.motrixlite.downloader.data.model.DownloadTask

sealed interface RepoEvent {
    data class TaskCompleted(val task: DownloadTask) : RepoEvent
    data class TaskFailed(val task: DownloadTask, val message: String) : RepoEvent
    data class TaskAdded(val task: DownloadTask) : RepoEvent
    data class Message(val text: String) : RepoEvent
}
