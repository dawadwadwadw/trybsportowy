package com.trybsportowy.ui.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.trybsportowy.R
import com.trybsportowy.TrybsportowyApplication
import com.trybsportowy.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class SyncStatus(
    val secretInvalid: Boolean,
    val running: Boolean,
    val attemptStatus: String?,
    val createdAt: Long?,
    val summary: String?,
    val nowMs: Long
)

/**
 * Single 14sp secondary-text row reflecting sync state (CLAUDE.md §11.1).
 * Text only — no icons/emojis. Clickable only when the token is invalid.
 */
@Composable
fun SyncStatusLine(
    app: TrybsportowyApplication,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val status by produceState<SyncStatus?>(initialValue = null) {
        SyncScheduler.observeStatus(context).collectLatest { workInfo ->
            while (true) {
                val attempt = withContext(Dispatchers.IO) {
                    app.database.syncAttemptDao.mostRecent()
                }
                value = SyncStatus(
                    secretInvalid = app.secretsStore.isSecretInvalid(),
                    running = workInfo?.state == WorkInfo.State.RUNNING,
                    attemptStatus = attempt?.status,
                    createdAt = attempt?.createdAt,
                    summary = attempt?.responseSummary,
                    nowMs = System.currentTimeMillis()
                )
                delay(30_000L)   // refresh the "przed chwilą" / clock boundary
            }
        }
    }

    val s = status ?: return
    val fiveMinMs = 5 * 60 * 1000L

    val (text, clickable) = when {
        s.secretInvalid || s.attemptStatus == "auth_failed" ->
            stringResource(R.string.sync_status_token_invalid) to true

        s.running ->
            stringResource(R.string.sync_status_running) to false

        s.attemptStatus == null ->
            stringResource(R.string.sync_status_none) to false

        s.attemptStatus == "success" && s.createdAt != null &&
            s.nowMs - s.createdAt < fiveMinMs ->
            stringResource(R.string.sync_status_just_now) to false

        s.attemptStatus == "success" && s.createdAt != null -> {
            val hhmm = Instant.ofEpochMilli(s.createdAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            stringResource(R.string.sync_status_last, hhmm) to false
        }

        s.attemptStatus == "failed" ->
            stringResource(R.string.sync_status_failed_server, s.summary ?: "?") to false

        else -> // "pending": an attempt that didn't complete -> will retry
            stringResource(R.string.sync_status_failed_network) to false
    }

    Text(
        text = text,
        fontSize = 14.sp,
        color = if (clickable) {
            MaterialTheme.colorScheme.error
        } else {
            Color(0xFF9E9E9E)
        },
        modifier = modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable { onOpenSettings() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}
