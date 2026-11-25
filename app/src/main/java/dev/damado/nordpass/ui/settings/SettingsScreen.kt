package dev.damado.nordpass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.damado.nordpass.data.settings.QrMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onQrModeChange: (QrMode) -> Unit,
    onLogout: () -> Unit,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // weight(1f) keeps long text inside the padded area instead of
                // pushing the Switch past the right margin.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                ) {
                    Text("Local QR generator")
                    Text(
                        text = "Off: use the site's verified QR image. On: draw locally (experimental).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.qrMode == QrMode.LOCAL,
                    onCheckedChange = { checked ->
                        onQrModeChange(if (checked) QrMode.LOCAL else QrMode.SERVER)
                    },
                )
            }

            Text(
                text = "Base URL: ${state.baseUrl}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (state.hasSession) {
                // Same visual grouping Android Settings uses: a thin rule
                // between the carnet options and the session action.
                HorizontalDivider()
                Button(
                    onClick = { showLogoutDialog = true },
                    enabled = !state.isLoggingOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isLoggingOut) "Logging out…" else "Log out")
                }
                Text(
                    text = "Logging out also removes any saved password from this device.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out and forget saved password?") },
            text = { Text("You will need to enter your password again on the next log in.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
