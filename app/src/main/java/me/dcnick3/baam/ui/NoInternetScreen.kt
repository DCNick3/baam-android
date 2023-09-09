package me.dcnick3.baam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.SignalWifiConnectedNoInternet4
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

@Composable
fun NoInternetScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.SignalWifiConnectedNoInternet4,
            contentDescription = "Not Connected",
            modifier = Modifier.size(128.dp)
        )
        Text(
            text = "Can't connect to the internet",
            textAlign = TextAlign.Center,
            fontSize = 4.5.em,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, device = "spec:width=200dp,height=400dp")
@Composable
private fun Preview_NoInternetScreen() {
    NoInternetScreen()
}