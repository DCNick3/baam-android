package me.dcnick3.baam.ui.web

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.dcnick3.baam.api.baamBaseUrl
import me.dcnick3.baam.ui.theme.BaamTheme
import me.dcnick3.baam.ui.web.impl.AccompanistWebViewClient
import me.dcnick3.baam.ui.web.impl.WebView
import me.dcnick3.baam.ui.web.impl.rememberWebViewNavigator
import me.dcnick3.baam.ui.web.impl.rememberWebViewState
import me.dcnick3.baam.viewmodel.ApiViewModel

private const val TAG = "AuthScreen"

private class AuthWevViewClient(val vm: ApiViewModel) : AccompanistWebViewClient() {
    private val _cookie = MutableStateFlow<String?>(null)
    val cookie get() = _cookie.asStateFlow()

    override fun onPageFinished(view: WebView, url: String?) {
        if (url?.startsWith(baamBaseUrl) == true) {
            val cookieManager = CookieManager.getInstance()
            val cookie = cookieManager.getCookie(baamBaseUrl)
            Log.i(TAG, "Got baam cookies!")
            _cookie.value = cookie
            vm.setAuthCookie(cookie)
        }

        Log.i(TAG, "onPageFinished: $url")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthScreen(vm: ApiViewModel = hiltViewModel()) {
    val state = rememberWebViewState(baamBaseUrl)
    val navigator = rememberWebViewNavigator()
    val webClient = remember { AuthWevViewClient(vm) }
    val cookie by webClient.cookie.collectAsState()

    if (cookie == null) {
        WebView(
            modifier = Modifier.fillMaxHeight(),
            state = state,
            navigator = navigator,
            onCreated = { webView ->
                webView.settings.javaScriptEnabled = true
            },
            client = webClient
        )
    } else {
        Validating()
    }

}

@Composable
private fun Validating() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Validating the auth cookie...",
            textAlign = TextAlign.Center,
            fontSize = 4.em,
            modifier = Modifier.padding(16.dp)
        )
        CircularProgressIndicator(
            modifier = Modifier.width(64.dp),
        )
    }
}


@Preview(showBackground = true)
@Composable
private fun Preview_Validating() {
    BaamTheme {
        Validating()
    }
}