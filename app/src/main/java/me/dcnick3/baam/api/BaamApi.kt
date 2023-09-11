package me.dcnick3.baam.api

import android.util.Log
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parseClientCookiesHeader
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

private const val TAG = "BaamApi"

@Serializable
data class AttendanceSession(
    val sessionCode: String,
    val title: String,
    val startDate: String,
)

sealed class BaamError {
    /**
     * Represents server (50x) and client (40x) errors.
     */
    data class HttpError(val code: HttpStatusCode) : BaamError()

    object AuthNeeded : BaamError()

    /**
     * Represent IOExceptions and connectivity issues.
     */
    data class NetworkError(val error: IOException) : BaamError()
}

fun parseCookies(cookie: String): List<Cookie> {
    val asMap = parseClientCookiesHeader(cookie)

    return asMap.map {
        // we don't care about the expiration date, because we will set it on every app launch
        Cookie(it.key, it.value, domain = baamDomain, path = "/", secure = true)
    }.toList()
}

class BaamCookieStorage : CookiesStorage {
    private var baamCookies: List<Cookie> = emptyList()

    fun setCookies(cookies: List<Cookie>) {
        baamCookies = cookies
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        // Ignore new cookies
    }

    override fun close() {}

    override suspend fun get(requestUrl: Url): List<Cookie> {
        if (requestUrl.host != baamDomain) {
            return emptyList()
        }
        return baamCookies
    }

}

typealias ApiResult<R> = Result<R, BaamError>

const val baamDomain = "baam.duckdns.org"
val baamBaseUrl = "https://$baamDomain/"

class BaamApi internal constructor() {
    val cookies = BaamCookieStorage()

    private val client = HttpClient {
        defaultRequest {
            url(baamBaseUrl)
        }
        install(Logging) {
            logger = Logger.SIMPLE
        }
        install(ContentNegotiation) {
            json(Json { isLenient = false; ignoreUnknownKeys = false })
        }
        install(HttpCookies) {
            storage = cookies
        }
        followRedirects = false
    }

    private suspend inline fun <reified R> handleCall(makeRequest: (HttpClient) -> HttpResponse): ApiResult<R> {
        val response = try {
            makeRequest(client)
        } catch (e: IOException) {
            return Err(BaamError.NetworkError(e))
        }

        if (response.status == HttpStatusCode.Found &&
            response.headers["Location"]?.startsWith("https://sso.university.innopolis.ru/adfs/oauth2/authorize/") == true
        ) {
            return Err(BaamError.AuthNeeded)
        }
        if (!response.status.isSuccess()) {
            Log.e(TAG, "HTTP error: ${response.status}\n${response.bodyAsText()}")
            return Err(BaamError.HttpError(response.status))
        }
        return Ok(response.body())
    }

    suspend fun getSessions(): ApiResult<List<AttendanceSession>> {
        return handleCall { client ->
            client.get("api/AttendanceSession/")
        }
    }

    suspend fun submitChallenge(code: String, challenge: String): ApiResult<String> {
        val result: ApiResult<JsonPrimitive>? = withTimeoutOrNull(1000) {
            handleCall { client ->
                client.post("api/AttendanceSession/$code/submitChallenge") {
                    contentType(ContentType.Application.Json)
                    setBody(JsonPrimitive(challenge))
                }
            }
        }
        return (result ?: Err(BaamError.NetworkError(IOException("Timeout"))))
            .map {
                assert(it.isString)
                it.content
            }
    }
}

val baamApi = BaamApi()