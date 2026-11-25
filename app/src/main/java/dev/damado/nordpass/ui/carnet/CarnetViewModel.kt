package dev.damado.nordpass.ui.carnet

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.damado.nordpass.data.network.CarnetMember
import dev.damado.nordpass.data.repository.CarnetRepository
import dev.damado.nordpass.data.repository.QrRenderResult
import dev.damado.nordpass.data.session.SessionManager
import dev.damado.nordpass.data.session.TokenOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** ~60 fps so the linear indicator moves about one pixel per frame. */
private const val PROGRESS_TICK_MS = 16L

data class CarnetUiState(
    val isLoading: Boolean = true,
    val bitmap: Bitmap? = null,
    val message: String? = null,
    val needLoginReason: String? = null,
    val member: CarnetMember? = null,
    /** 1f = full bar, 0f = token about to be replaced. */
    val remainingFraction: Float? = null,
)

class CarnetViewModel(
    private val session: SessionManager,
    private val carnet: CarnetRepository,
) : ViewModel() {

    var uiState by mutableStateOf(CarnetUiState())
        private set

    private var refreshInProgress = false
    private var pendingRefresh = false
    private var pendingShowLoading = false
    private var autoRefreshEnabled = false
    private var validityMs = 0L
    private var tokenReceivedAtMs = 0L
    private var autoRefreshJob: Job? = null
    private var progressJob: Job? = null

    init {
        refresh()
    }

    /** Manual refresh shows the loading indicator. */
    fun refresh() = refresh(showLoading = true)

    private fun refresh(showLoading: Boolean) {
        // Never silently drop a refresh (e.g. a resume check arriving while a
        // manual refresh is in flight). Replay it when this call finishes.
        if (refreshInProgress) {
            pendingRefresh = true
            pendingShowLoading = pendingShowLoading || showLoading
            return
        }
        cancelTimers()
        refreshInProgress = true
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = showLoading, message = null, needLoginReason = null)
            try {
                when (val outcome = session.freshToken()) {
                    is TokenOutcome.Token -> {
                        validityMs = outcome.validitySeconds * 1_000L
                        // The lifetime starts with the API response, not with
                        // however long QR rendering takes afterwards.
                        tokenReceivedAtMs = SystemClock.elapsedRealtime()
                        when (val rendered = carnet.render(outcome.url)) {
                            is QrRenderResult.Success -> {
                                uiState = uiState.copy(
                                    isLoading = false,
                                    bitmap = rendered.bitmap,
                                    member = outcome.member,
                                    message = null,
                                    remainingFraction = 1f,
                                )
                                checkExpiryAndSchedule()
                            }
                            is QrRenderResult.Failure -> {
                                validityMs = 0L
                                uiState = uiState.copy(
                                    isLoading = false,
                                    bitmap = null,
                                    member = null,
                                    message = rendered.message,
                                    remainingFraction = null,
                                )
                            }
                        }
                    }
                    is TokenOutcome.NeedLogin -> {
                        validityMs = 0L
                        uiState = uiState.copy(
                            isLoading = false,
                            bitmap = null,
                            member = null,
                            needLoginReason = outcome.reason,
                            remainingFraction = null,
                        )
                    }
                    is TokenOutcome.Error -> {
                        validityMs = 0L
                        uiState = uiState.copy(
                            isLoading = false,
                            bitmap = null,
                            member = null,
                            message = outcome.message,
                            remainingFraction = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Also guarantees the initial check resolves, so the system
                // splash is never held hostage by an unexpected error.
                validityMs = 0L
                uiState = uiState.copy(
                    isLoading = false,
                    bitmap = null,
                    member = null,
                    message = "Unexpected error: ${e.message ?: "unknown"}",
                    remainingFraction = null,
                )
            } finally {
                refreshInProgress = false
                if (pendingRefresh) {
                    val nextShowLoading = pendingShowLoading
                    pendingRefresh = false
                    pendingShowLoading = false
                    refresh(nextShowLoading)
                }
            }
        }
    }

    /** Called from the screen's lifecycle so no timer runs in the background. */
    fun setAutoRefreshEnabled(enabled: Boolean) {
        autoRefreshEnabled = enabled
        if (enabled) {
            // Runs on every ON_RESUME: background return, first composition
            // and coming back from Settings all take the same path.
            checkExpiryAndSchedule()
        } else {
            cancelTimers()
        }
    }

    private fun checkExpiryAndSchedule() {
        cancelTimers()
        if (!autoRefreshEnabled || uiState.needLoginReason != null) return

        if (uiState.bitmap == null) {
            // Q6: a previous refresh left no QR. Returning to the foreground
            // retries once; failure simply restores the retry state.
            if (!uiState.isLoading && uiState.message != null) {
                refresh(showLoading = true)
            }
            return
        }
        if (validityMs <= 0L) return

        // Q1/Q8: age is measured with monotonic elapsedRealtime() from the
        // moment the API returned the token. Deep sleep and background time
        // count towards expiry; wall-clock changes do not.
        val ageMs = SystemClock.elapsedRealtime() - tokenReceivedAtMs
        val remainingMs = QrExpiry.timeUntilRefresh(validityMs, ageMs)

        if (remainingMs == 0L) {
            // Q2: keep the old QR visible until the replacement is ready.
            // Q7: refresh() queues this if another refresh is still running.
            refresh(showLoading = false)
            return
        }

        autoRefreshJob = viewModelScope.launch {
            delay(remainingMs)
            refresh(showLoading = false)
        }
        startProgress(remainingMs)
    }

    private fun startProgress(remainingMs: Long) {
        val fullDurationMs = QrExpiry.fullDurationMs(validityMs)
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val start = SystemClock.elapsedRealtime()
            val initial = (remainingMs.toFloat() / fullDurationMs.toFloat()).coerceIn(0f, 1f)
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - start
                val fraction = (initial - elapsed.toFloat() / fullDurationMs.toFloat())
                    .coerceIn(0f, 1f)
                uiState = uiState.copy(remainingFraction = fraction)
                if (fraction <= 0f) break
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    private fun cancelTimers() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        cancelTimers()
        super.onCleared()
    }
}
