package me.dcnick3.baam.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import me.dcnick3.baam.ui.camera.ScannerScreen
import me.dcnick3.baam.ui.camera.NoPermissionScreen
import me.dcnick3.baam.ui.theme.BaamTheme
import me.dcnick3.baam.utils.ConnectionState
import me.dcnick3.baam.utils.connectivityState
import me.dcnick3.baam.viewmodel.ApiViewModel
import me.dcnick3.baam.ui.web.AuthScreen
import me.dcnick3.baam.ui.web.SuccessfulMark
import me.dcnick3.baam.viewmodel.AuthState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BaamTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "scan") {
                        composable("scan") { ScanScreen(navController) }
                        composable(
                            "success/{code}",
                            arguments = listOf(navArgument("code") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val code = backStackEntry.arguments?.getString("code")
                            if (code == null) {
                                navController.popBackStack()
                            } else {
                                SuccessfulMark(code)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(navController: NavController, vm: ApiViewModel = viewModel()) {
    val cameraPermissionState: PermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val connection by connectivityState()

    LaunchedEffect(key1 = Unit) {
        if (!cameraPermissionState.status.isGranted && !cameraPermissionState.status.shouldShowRationale) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    val authState by vm.authState.collectAsState()

    if (cameraPermissionState.status.isGranted) {
        if (connection == ConnectionState.Available) {
            if (authState == AuthState.NotAuthenticated) {
                AuthScreen()
            } else {
                ScannerScreen(navController)
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

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BaamTheme {
        Greeting("Android")
    }
}