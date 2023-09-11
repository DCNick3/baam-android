package me.dcnick3.baam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ErrorBarState(private val coroutineScope: CoroutineScope) {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var clearError: Job? = null
    private val mutex = Mutex()

    companion object {
        const val clearTimeout = 5000L
    }

    fun setError(error: String) {
        coroutineScope.launch {
            mutex.withLock {
                clearError?.cancelAndJoin()
                _error.value = error
                clearError = coroutineScope.launch {
                    delay(clearTimeout)
                    _error.value = null
                }
            }
        }
    }
}

@Composable
fun ErrorBar(state: ErrorBarState) {
    val error by state.error.collectAsState()
    val errorCopy = error

    if (errorCopy != null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF44336)),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    errorCopy,
                    fontSize = 5.em,
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorBarPreview() {
    val coroutineScope = rememberCoroutineScope()
    val state = remember { ErrorBarState(coroutineScope) }

    LaunchedEffect(key1 = Unit, block = {
        state.setError("HTTP Error: 404")
        delay(1000)
        state.setError("QR Code is unacceptable")
    })

    ErrorBar(state)
}