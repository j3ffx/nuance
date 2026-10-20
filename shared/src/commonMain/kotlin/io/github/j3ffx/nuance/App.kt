package io.github.j3ffx.nuance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.j3ffx.nuance.resources.Res
import io.github.j3ffx.nuance.resources.app_name
import io.github.j3ffx.nuance.resources.placeholder_tagline
import org.jetbrains.compose.resources.stringResource

/** The whole app, shared by Android and iOS. A placeholder until the camera screen exists. */
@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(Res.string.placeholder_tagline), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
