package me.dcnick3.baam.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TAG = "NetworkUtils"

private val Context.connectivityManager get(): ConnectivityManager {
    return getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
}

/**
 * Network Utility to observe availability or unavailability of Internet connection
 */
private fun ConnectivityManager.observeConnectivityAsFlow() = callbackFlow {
    trySend(currentConnectivityState)

    val callback = NetworkCallback { connectionState -> trySend(connectionState) }

    val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    registerNetworkCallback(networkRequest, callback)

    awaitClose {
        unregisterNetworkCallback(callback)
    }
}.distinctUntilChanged()

/**
 * Network utility to get current state of internet connection
 */
private val ConnectivityManager.currentConnectivityState: ConnectionState
    get() {
        @Suppress("DEPRECATION")
        // the deprecation doesn't make sense to us, since we only use this property to get the initial state
        // later on the callback is used as ~~the god~~ google has intended
        val connected = allNetworks.any { network ->
            getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ?: false
        }

        return if (connected) ConnectionState.Available else ConnectionState.Unavailable
    }

private fun NetworkCallback(callback: (ConnectionState) -> Unit): ConnectivityManager.NetworkCallback {
    return object : ConnectivityManager.NetworkCallback() {
        var availableNetworks = hashSetOf<Network>();

        override fun onAvailable(network: Network) {
            availableNetworks.add(network)
            Log.d(TAG, "NetworkCallback::onAvailable $network [$availableNetworks]")

            if (availableNetworks.size == 1) {
                Log.d(TAG, "a network has become available, reporting internet")
                callback(ConnectionState.Available)
            }
        }

        override fun onLost(network: Network) {
            availableNetworks.remove(network)
            Log.d(TAG, "NetworkCallback::onLost $network [$availableNetworks]")

            if (availableNetworks.isEmpty()) {
                Log.d(TAG, "no networks available anymore, reporting no internet")
                callback(ConnectionState.Unavailable)
            }
        }

        override fun onUnavailable() {
            throw UnsupportedOperationException()
        }
    }
}

@Composable
fun connectivityState(): State<ConnectionState> {
    val connectivityManager = LocalContext.current.connectivityManager
    return produceState(initialValue = connectivityManager.currentConnectivityState) {
        connectivityManager.observeConnectivityAsFlow().distinctUntilChanged().collect { value = it }
    }
}

enum class ConnectionState {
    Available,
    Unavailable,
}
