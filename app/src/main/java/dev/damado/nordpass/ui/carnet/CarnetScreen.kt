package dev.damado.nordpass.ui.carnet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarnetScreen(
    state: CarnetUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onNeedLogin: (String) -> Unit,
    onAutoRefreshEnabled: (Boolean) -> Unit,
    onInitialCheckComplete: () -> Unit,
) {
    val context = LocalContext.current

    // Maximum brightness helps scanners read the QR. Keep the system bars
    // visible: the QR only occupies half the screen, so hiding them gains
    // little and removes useful navigation/status information.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val attributes = window?.attributes
        val previousBrightness = attributes?.screenBrightness
        if (window != null && attributes != null) {
            attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = attributes
        }

        onDispose {
            if (window != null && attributes != null && previousBrightness != null) {
                attributes.screenBrightness = previousBrightness
                window.attributes = attributes
            }
        }
    }

    // Auto-refresh only while this screen is in the foreground. No alarms,
    // WorkManager or background timers.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onAutoRefreshEnabled(true)
                Lifecycle.Event.ON_PAUSE -> onAutoRefreshEnabled(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onAutoRefreshEnabled(false)
        }
    }

    // Release the system splash only after the first session check has both
    // resolved and, when login is needed, actually started the navigation.
    LaunchedEffect(state.isLoading, state.needLoginReason) {
        if (!state.isLoading && state.needLoginReason == null) onInitialCheckComplete()
    }

    LaunchedEffect(state.needLoginReason) {
        val reason = state.needLoginReason
        if (reason != null) {
            // Keep the reason set until this entry is popped, otherwise the
            // next recomposition falls through to the generic Retry state.
            onNeedLogin(reason)
            onInitialCheckComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eurofitness Estació del Nord") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh QR")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                // Login navigation is already in flight; showing the retry
                // state here would flash for a frame before the form appears.
                state.needLoginReason != null -> Unit

                state.bitmap != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val member = state.member
                    if (member?.name != null || member?.photo != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 16.dp),
                        ) {
                            member.photo?.let { photo ->
                                Image(
                                    bitmap = photo.asImageBitmap(),
                                    contentDescription = "Member photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape),
                                )
                            }
                            member.name?.let { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                    Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = "Carnet QR code",
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.height(16.dp))
                    state.remainingFraction?.let { fraction ->
                        // Material's indicator has a fixed intrinsic 240 dp
                        // width, so constrain it through a 50%-width parent.
                        Box(Modifier.fillMaxWidth(0.5f)) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = "Refreshes automatically before this code expires.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = state.message ?: "No carnet loaded.",
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRefresh) { Text("Retry") }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
