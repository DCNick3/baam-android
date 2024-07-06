package me.dcnick3.baam.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.Intent
import android.os.Environment
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.dcnick3.baam.R
import me.dcnick3.baam.api.AppUpdateRepository
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext


class Event<T>(
    private val data: T,
) {
    private var isConsumed = false

    suspend fun consume(collector: suspend T.() -> Unit) {
        if (!isConsumed) {
            isConsumed = true
            collector(data)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Event<*>

        if (data != other.data) return false
        return isConsumed == other.isConsumed
    }

    override fun hashCode(): Int {
        var result = data?.hashCode() ?: 0
        result = 31 * result + isConsumed.hashCode()
        return result
    }

    override fun toString(): String {
        return "Event(data=$data, isConsumed=$isConsumed)"
    }
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    application: Application
): AndroidViewModel(application) {
    @JvmField
    protected val loadingCounter = MutableStateFlow(0)

    @JvmField
    protected val errorEvent = MutableStateFlow<Event<Throwable>?>(null)

    val onError: StateFlow<Event<Throwable>?>
        get() = errorEvent

    val isLoading: StateFlow<Boolean> = loadingCounter.map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, loadingCounter.value > 0)

    val nextVersion = repository.availableUpdateState.asStateFlow()
    val downloadProgress = MutableStateFlow(-1f)
    val downloadState = MutableStateFlow(DownloadManager.STATUS_PENDING)
    val installIntent = MutableStateFlow<Intent?>(null)
    val onDownloadDone = MutableStateFlow<Event<Intent>?>(null)

    private val downloadManager = application.getSystemService(Application.DOWNLOAD_SERVICE) as DownloadManager
    private val appName = application.getString(R.string.app_name)

    private fun createErrorHandler() = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
        if (throwable !is CancellationException) {
            errorEvent.value = Event(throwable)
        }
    }

    protected fun launchLoadingJob(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job = viewModelScope.launch(context + createErrorHandler(), start) {
        loadingCounter.value += 1
        try {
            block()
        } finally {
            loadingCounter.value -= 1
        }
    }

    init {
        if (nextVersion.value == null) {
            launchLoadingJob(Dispatchers.Default) {
                repository.fetchUpdate()
            }
        }
    }

    fun startDownload() {
        launchLoadingJob(Dispatchers.Default) {
            val version = nextVersion.value ?: throw IllegalStateException("No version available")
            val url = version.apkUrl.toUri()
            val request = DownloadManager.Request(url)
                .setTitle("$appName v${version.name}")
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, url.lastPathSegment)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive")
            val downloadId = downloadManager.enqueue(request)
            observeDownload(downloadId)
        }
    }

    fun onDownloadComplete(intent: Intent) {
        launchLoadingJob(Dispatchers.Default) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L)
            if (downloadId == 0L) {
                return@launchLoadingJob
            }
            @Suppress("DEPRECATION")
            val installerIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            installerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            installerIntent.setDataAndType(
                downloadManager.getUriForDownloadedFile(downloadId),
                downloadManager.getMimeTypeForDownloadedFile(downloadId),
            )
            installerIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            installIntent.value = installerIntent
            onDownloadDone.value = Event(installerIntent)
        }
    }

    private suspend fun observeDownload(id: Long) {
        val query = DownloadManager.Query()
        query.setFilterById(id)
        while (coroutineContext.isActive) {
            downloadManager.query(query).use { cursor ->
                if (cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    )
                    val bytesTotal = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    )
                    downloadProgress.value = bytesDownloaded.toFloat() / bytesTotal
                    val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    downloadState.value = state
                    if (state == DownloadManager.STATUS_SUCCESSFUL || state == DownloadManager.STATUS_FAILED) {
                        return
                    }
                }
            }
            delay(100)
        }
    }
}