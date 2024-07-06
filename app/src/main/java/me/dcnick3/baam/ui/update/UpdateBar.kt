package me.dcnick3.baam.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import me.dcnick3.baam.api.AppVersion

@Composable
fun UpdateBar(navController: NavController, state: AppVersion?) {
    if (state != null) {
        ConstraintLayout(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary
//                    Color(0xFF8BC34A)
                ),
        ) {
            // Create references for the composables to constrain
            val (title, subtext, button) = createRefs()

            FilledTonalButton(
                onClick = {
                    navController.navigate("update")
                },
                // Assign reference "button" to the Button composable
                // and constrain it to the top of the ConstraintLayout
                modifier = Modifier.constrainAs(button) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)

                    end.linkTo(parent.end, margin = 8.dp)
                }
            ) {
                Text("Update")
            }

            Text(
                "Update available!",
                fontSize = 5.em,
                modifier = Modifier.constrainAs(title) {
                    width = Dimension.fillToConstraints

                    top.linkTo(parent.top, margin = 16.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(button.start, margin = 8.dp)
                },
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onPrimary,
            )

            Text(
                "Update to get stability improvements, improving the user's experience",
                modifier = Modifier.constrainAs(subtext) {
                    width = Dimension.fillToConstraints

                    top.linkTo(title.bottom, margin = 16.dp)
                    bottom.linkTo(parent.bottom, margin = 16.dp)

                    start.linkTo(parent.start, margin = 16.dp)

                    end.linkTo(button.start, margin = 8.dp)
                },
                lineHeight = 1.2.em,
                fontSize = 3.em,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UpdateBarPreview() {
    val state = remember {
        MutableStateFlow<AppVersion?>(null)
    }
    val nav = rememberNavController()
    val stateV by state.collectAsState()

    LaunchedEffect(key1 = Unit, block = {
//        delay(1000)
        state.value = AppVersion(1, "1.0", "https://url", 100, "https://apk", "Very nice changelog")
    })

    Box(modifier = Modifier.fillMaxSize()) {
        Card {
            Text(text = "Hello, world!")
        }

        UpdateBar(nav, stateV)
    }
}