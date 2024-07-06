package me.dcnick3.baam.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.http.HttpStatusCode
import java9.util.concurrent.CompletableFuture
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.dcnick3.baam.api.AppUpdateRepository
import me.dcnick3.baam.api.BaamError
import me.dcnick3.baam.api.ParsedChallenge
import me.dcnick3.baam.api.baamApi
import me.dcnick3.baam.api.baamBaseUrl
import me.dcnick3.baam.api.baamDomain
import me.dcnick3.baam.api.parseCookies
import me.dcnick3.baam.utils.await
import javax.inject.Inject
import kotlin.properties.ReadOnlyProperty

private const val TAG = "ApiViewModel"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataRepository(private val dataStore: DataStore<Preferences>) {
    private val cookieKey: Preferences.Key<String> = stringPreferencesKey("cookie")

    suspend fun setCookie(cookie: String?) {
        dataStore.edit {
            if (cookie == null) {
                it.remove(cookieKey)
            } else {
                it[cookieKey] = cookie
            }
        }
    }

    suspend fun getCookie(): String? {
        return dataStore.data.first()[cookieKey]
    }
}

private val Context.dataRepository: DataRepository by
ReadOnlyProperty<Context, DataRepository> { context, _ ->
    DataRepository(context.dataStore)
}

enum class AuthState {
    Authenticated,
    NotAuthenticated,
}

sealed class ChallengeResult {
    data class Success(val code: String) : ChallengeResult()
    data object Unacceptable : ChallengeResult()
    data class Error(val error: String) : ChallengeResult()
}

sealed class Command {
    data object CheckAuth : Command()
    data class SetCookie(val cookie: String) : Command()
    data class SubmitChallenge(
        val challenge: ParsedChallenge,
        val fut: CompletableFuture<ChallengeResult>
    ) : Command()
}

@HiltViewModel
class ApiViewModel @Inject constructor(
    val updateRepository: AppUpdateRepository,
    application: Application
) : AndroidViewModel(application) {
    private val commandChannel = Channel<Command>(2)

    private val context get() = getApplication<Application>().applicationContext

    init {
        viewModelScope.launch {
            commandChannel.send(Command.CheckAuth)

            commandChannel.receiveAsFlow().collect { cmd ->
                when (cmd) {
                    // TODO: actually, I think we can check the auth validity 100% offline
                    // we use JWT so we just need to check the signature and the expiration date
                    // but it's easier to just check it online for now
                    is Command.CheckAuth -> {
                        val cookies = context.dataRepository.getCookie()
                        if (cookies == null) {
                            _authState.value = AuthState.NotAuthenticated
                            return@collect
                        }
                        checkAuth(cookies)
                    }

                    is Command.SetCookie -> {
                        checkAuth(cmd.cookie)
                    }

                    is Command.SubmitChallenge -> {
                        val challenge = cmd.challenge
                        if (challenge.url.host != baamDomain) {
                            Log.w(TAG, "Invalid challenge URL: ${challenge.url}")
                        }
                        val feedback = when (val result =
                            baamApi.submitChallenge(challenge.code, challenge.challenge)) {
                            is Ok -> {
                                ChallengeResult.Success(result.value)
                            }

                            is Err -> {
                                when (val error = result.error) {
                                    BaamError.HttpError(HttpStatusCode.Forbidden) -> {
                                        ChallengeResult.Unacceptable
                                    }

                                    BaamError.AuthNeeded -> {
                                        onInvalidAuth()
                                        ChallengeResult.Error("Auth needed")
                                    }

                                    BaamError.HttpError(HttpStatusCode.NotFound) -> {
                                        ChallengeResult.Error("Session not found")
                                    }

                                    is BaamError.HttpError -> {
                                        ChallengeResult.Error("HTTP Error: ${error.code}")
                                    }

                                    is BaamError.NetworkError -> {
                                        ChallengeResult.Error("Network error: ${error.error}")
                                    }
                                }
                            }
                        }

                        cmd.fut.complete(feedback)
                    }
                }
            }
        }
        viewModelScope.launch {
            updateRepository.fetchUpdate()
        }
    }

    private suspend fun onInvalidAuth() {
        Log.i(TAG, "Auth invalid, clearing cookie")
        _authState.value = AuthState.NotAuthenticated
        context.dataRepository.setCookie(null)
    }

    private suspend fun checkAuth(cookies: String) {
        baamApi.cookies.setCookies(parseCookies(cookies))

        when (val result = baamApi.getSessions()) {
            is Ok -> {
                Log.i(TAG, "Auth valid, storing cookie")
                _authState.value = AuthState.Authenticated
                context.dataRepository.setCookie(cookies)
                CookieManager.getInstance().setCookie(baamBaseUrl, cookies)
            }

            Err(BaamError.AuthNeeded) -> {
                onInvalidAuth()
            }

            is Err -> {
                Log.e(TAG, "Error while checking auth: ${result.error}")
                onInvalidAuth()
            }
        }
    }

    private val _authState = MutableStateFlow(null as AuthState?)
    val authState = _authState.asStateFlow()

    fun setAuthCookie(cookie: String) {
        viewModelScope.launch {
            commandChannel.send(Command.SetCookie(cookie))
        }
    }

    suspend fun submitChallenge(challenge: ParsedChallenge): ChallengeResult {
        val fut = CompletableFuture<ChallengeResult>()
        viewModelScope.launch {
            commandChannel.send(Command.SubmitChallenge(challenge, fut))
        }
        return fut.await()
    }
}