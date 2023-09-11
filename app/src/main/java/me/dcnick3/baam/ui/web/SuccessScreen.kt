package me.dcnick3.baam.ui.web

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import me.dcnick3.baam.api.baamBaseUrl
import me.dcnick3.baam.ui.web.impl.WebView
import me.dcnick3.baam.ui.web.impl.rememberWebViewNavigator
import me.dcnick3.baam.ui.web.impl.rememberWebViewState

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SuccessfulMark(code: String) {
    val state = rememberWebViewState("${baamBaseUrl}MarkedSuccessfully/$code")
    val navigator = rememberWebViewNavigator()

    WebView(
        state = state,
        navigator = navigator,
        onCreated = { webView ->
            webView.settings.javaScriptEnabled = true
        }
    )
}