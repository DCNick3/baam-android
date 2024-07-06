package me.dcnick3.baam.ui.update

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import me.dcnick3.baam.R
import me.dcnick3.baam.api.AppVersion
import me.dcnick3.baam.viewmodel.AppUpdateViewModel
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun UpdateScreen(navController: NavController, vm: AppUpdateViewModel = hiltViewModel()) {
    val nextVersion by vm.nextVersion.collectAsState()
    if (nextVersion == null) {
        Text(
            text = "No updates available, how did you get here?",
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        return
    }

    val version = nextVersion!!

    val downloadState by vm.downloadState.collectAsState()
    val downloadProgress by vm.downloadProgress.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val installIntent by vm.installIntent.collectAsState()

    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    LaunchedEffect(key1 = "a") {
        scope.launch {
            vm.onDownloadDone.collect {
                it?.consume {
                    context.startActivity(this)
                }
            }
        }
    }

    DisposableEffect(context) {
        val receiver = UpdateDownloadReceiver(vm)

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val storagePermissionState: PermissionState =
        rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) {
            if (it) {
                vm.startDownload()
            } else {
                openInBrowser(context, version)
            }
        }

    VersionDescriptionWithButtons(
        version = version,
        progressBar = {
            InstallProgress(progress = { downloadProgress }, state = downloadState)
        },
        onUpdate = {
            val intent = vm.installIntent.value;
            if (intent != null) {
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !storagePermissionState.status.isGranted) {
                    storagePermissionState.launchPermissionRequest()
                } else {
                    vm.startDownload()
                }
            }
        },
        onCancel = { navController.popBackStack() },
        updateButtonEnabled = !isLoading,
        updateButtonWillInstall = installIntent != null
    )
}

private class UpdateDownloadReceiver(
    private val viewModel: AppUpdateViewModel,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                viewModel.onDownloadComplete(intent)
            }
        }
    }
}

fun openInBrowser(context: Context, version: AppVersion) {
    val intent = Intent(Intent.ACTION_VIEW, version.url.toUri())
    context.startActivity(Intent.createChooser(intent, "Open in browser"))
}

enum class FileSize(private val multiplier: Int) {

    BYTES(1), KILOBYTES(1024), MEGABYTES(1024 * 1024);

    fun convert(amount: Long, target: FileSize): Long = amount * multiplier / target.multiplier

    fun format(context: Context, amount: Long): String {
        val bytes = amount * multiplier
        val units = context.getString(R.string.text_file_sizes).split('|')
        if (bytes <= 0) {
            return "0 ${units.first()}"
        }
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        return buildString {
            append(
                DecimalFormat("#,##0.#").format(
                    bytes / 1024.0.pow(digitGroups.toDouble()),
                ),
            )
            val unit = units.getOrNull(digitGroups)
            if (unit != null) {
                append(' ')
                append(unit)
            }
        }
    }
}

@Composable
private fun VersionDescriptionWithButtons(version: AppVersion,
                                          progressBar: @Composable () -> Unit,
                                          onUpdate: () -> Unit,
                                          onCancel: () -> Unit,
                                          updateButtonEnabled: Boolean,
                                          updateButtonWillInstall: Boolean,
) {
    Column {
        VersionDescription(version, progressBar)
        Spacer(modifier = Modifier.weight(1f))

        Row {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onUpdate,
                modifier = Modifier.padding(16.dp),
                enabled = updateButtonEnabled
            ) {
                Text(if (updateButtonWillInstall) {
                    "Install"
                } else {
                    "Update"
                })
            }
        }
    }
}

@Preview
@Composable
private fun VersionDescriptionWithButtonsPreview() {
    val version = AppVersion(12, "1.0.0", "https://example.com", 1337 * 1024, "https://example.com", "A very nice chagelog:\n\n- Blobbed the shlob\n- Glibbed the flib\n- General system stability improvements to enhance the user's experience")

    VersionDescriptionWithButtons(
        version = version,
        progressBar = {},
        onUpdate = {},
        onCancel = {},
        updateButtonEnabled = true,
        updateButtonWillInstall = false
    )
}
@Composable
private fun VersionDescription(version: AppVersion, progressBar: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_app_update),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "A new version of the app is available",
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            textAlign = TextAlign.Center,
            fontSize = 5.em
        )

        progressBar()

        val text = "New version: ${version.name}\n" +
                "Size: ${FileSize.BYTES.format(LocalContext.current, version.apkSize)}\n\n" +
                version.description

        MarkdownText(markdown = text)
    }
}

@Preview
@Composable
private fun VersionDescriptionPreview() {
    val version = AppVersion(12, "1.0.0", "https://example.com", 1337 * 1024, "https://example.com", "A very nice chagelog:\n\n- Blobbed the shlob\n- Glibbed the flib\n- General system stability improvements to enhance the user's experience")

    VersionDescription(version, progressBar = {})
}

@Composable
private fun InstallProgress(progress: () -> Float, state: Int) {
    when (state) {
        DownloadManager.STATUS_RUNNING -> {
            LinearProgressIndicator(
                progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
        DownloadManager.STATUS_FAILED -> {
            Text(
                "Download failed!",
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.error
            )
        }
        DownloadManager.STATUS_PAUSED -> {
            Text(
                "Download is paused",
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.error
            )
        }
        DownloadManager.STATUS_SUCCESSFUL -> {
            // do not show anything if we have completed
        }
    }
}

@Preview
@Composable
private fun InstallProgressPreview1() {
    InstallProgress(progress = { 0.5f }, state = DownloadManager.STATUS_RUNNING)
}

@Preview
@Composable
private fun InstallProgressPreview2() {
    InstallProgress(progress = { 0.5f }, state = DownloadManager.STATUS_FAILED)
}