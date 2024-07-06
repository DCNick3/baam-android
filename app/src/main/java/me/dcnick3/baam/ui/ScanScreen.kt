package me.dcnick3.baam.ui

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import me.dcnick3.baam.ui.camera.NoPermissionScreen
import me.dcnick3.baam.ui.camera.ScannerScreen
import me.dcnick3.baam.ui.update.UpdateBar
import me.dcnick3.baam.ui.web.AuthScreen
import me.dcnick3.baam.utils.ConnectionState
import me.dcnick3.baam.utils.connectivityState
import me.dcnick3.baam.viewmodel.ApiViewModel
import me.dcnick3.baam.viewmodel.AuthState


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(navController: NavController, vm: ApiViewModel = hiltViewModel()) {
    val cameraPermissionState: PermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val connection by connectivityState()

    LaunchedEffect(key1 = Unit) {
        if (!cameraPermissionState.status.isGranted && !cameraPermissionState.status.shouldShowRationale) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    val authState by vm.authState.collectAsState()
    val availableUpdate by vm.updateRepository.availableUpdateState.collectAsState()

    if (cameraPermissionState.status.isGranted) {
        if (connection == ConnectionState.Available) {
            Column(modifier = Modifier.fillMaxHeight()) {
                UpdateBar(navController, availableUpdate)
                if (authState == AuthState.NotAuthenticated) {
                    AuthScreen()
                } else {
                    ScannerScreen(navController)
                }
            }
        } else {
            NoInternetScreen()
        }
    } else {
        // In this screen you should notify the user that the permission
        // is required and maybe offer a button to start another
        // camera permission request
        NoPermissionScreen(cameraPermissionState::launchPermissionRequest)
    }

}