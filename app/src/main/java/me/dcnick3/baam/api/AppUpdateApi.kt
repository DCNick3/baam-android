package me.dcnick3.baam.api

import android.os.Parcelable
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.dcnick3.baam.BuildConfig
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppUpdateApi"

inline fun <T, R> T.runCatchingCancellable(block: T.() -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: InterruptedException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

data class VersionId(
    val major: Int,
    val minor: Int,
    val build: Int,
    val variantType: String,
    val variantNumber: Int,
) : Comparable<VersionId> {

    override fun compareTo(other: VersionId): Int {
        var diff = major.compareTo(other.major)
        if (diff != 0) {
            return diff
        }
        diff = minor.compareTo(other.minor)
        if (diff != 0) {
            return diff
        }
        diff = build.compareTo(other.build)
        if (diff != 0) {
            return diff
        }
        diff = variantWeight(variantType).compareTo(variantWeight(other.variantType))
        if (diff != 0) {
            return diff
        }
        return variantNumber.compareTo(other.variantNumber)
    }

    private fun variantWeight(variantType: String) = when (variantType.lowercase(Locale.ROOT)) {
        "a", "alpha" -> 1
        "b", "beta" -> 2
        "rc" -> 4
        "" -> 8
        else -> 0
    }
}

val VersionId.isStable: Boolean
    get() = variantType.isEmpty()

fun VersionId(versionName: String): VersionId {
    val parts = versionName.substringBeforeLast('-').split('.')
    val variant = versionName.substringAfterLast('-', "")
    return VersionId(
        major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
        minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        build = parts.getOrNull(2)?.toIntOrNull() ?: 0,
        variantType = variant.filter(Char::isLetter),
        variantNumber = variant.filter(Char::isDigit).toIntOrNull() ?: 0,
    )
}

@Parcelize
data class AppVersion(
    val id: Long,
    val name: String,
    val url: String,
    val apkSize: Long,
    val apkUrl: String,
    val description: String,
) : Parcelable {
    @IgnoredOnParcel
    val versionId = VersionId(name)
}

@Serializable
data class GithubReleaseAsset(
    val size: Long,
    val content_type: String,
    val browser_download_url: String
)

@Serializable
data class GithubRelease(
    val id: Long,
    val html_url: String,
    val name: String,
    val assets: List<GithubReleaseAsset>,
    val body: String
)

const val githubLocation = "DCNick3/baam-android"
private const val CONTENT_TYPE_APK = "application/vnd.android.package-archive"


@Singleton
class AppUpdateRepository @Inject internal constructor() {
    private val client = HttpClient {
        defaultRequest {
        }
        install(Logging) {
            logger = Logger.SIMPLE
        }
        install(ContentNegotiation) {
            json(Json { isLenient = false; ignoreUnknownKeys = true })
        }
        install(HttpCookies) {
        }
        followRedirects = false
    }

    val availableUpdateState = MutableStateFlow<AppVersion?>(null)
    val updateCheckedState = MutableStateFlow(false)

    private suspend fun getAvailableVersions(): List<AppVersion> {
        val response =
            client.get("https://api.github.com/repos/${githubLocation}/releases?page=1&per_page=10")

        return response.body<Array<GithubRelease>>()
            .mapNotNull { release ->
                release.assets.find { asset ->
                    asset.content_type == CONTENT_TYPE_APK
                }?.let { asset ->
                    AppVersion(
                        id = release.id,
                        url = release.html_url,
                        name = release.name.removePrefix("v"),
                        apkSize = asset.size,
                        apkUrl = asset.browser_download_url,
                        description = release.body
                    )
                }
            }
    }

    suspend fun fetchUpdate() = withContext(Dispatchers.Default) {
        if (!isUpdateSupported()) {
            updateCheckedState.value = true
        }

        runCatchingCancellable {
            val currentVersion = VersionId(BuildConfig.VERSION_NAME)
            var available = getAvailableVersions()
                .sortedBy { it.versionId }
            if (currentVersion.isStable) {
                available = available.filter { it.versionId.isStable }
            }
            available.maxByOrNull { it.versionId }
                ?.takeIf { it.versionId > currentVersion }
        }.onSuccess {
            Log.i(TAG, "Update check result: ${it}")
            availableUpdateState.value = it
        }.onFailure {
            Log.e(TAG, "Checking for update has failed", it)
        }

        updateCheckedState.value = true
    }

    fun isUpdateSupported(): Boolean {
        return true
//        return BuildConfig.DEBUG
    }

    suspend fun getCurrentVersionChangelog(): String? {
        val currentVersion = VersionId(BuildConfig.VERSION_NAME)
        val available = getAvailableVersions()
        return available.find { x -> x.versionId == currentVersion }?.description
    }
}