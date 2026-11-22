package dev.zurarul.saldotracking.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.zurarul.saldotracking.domain.LogStatus
import dev.zurarul.saldotracking.domain.NotificationLog
import dev.zurarul.saldotracking.ui.home.TransactionDraft
import dev.zurarul.saldotracking.ui.home.TransactionSheet
import dev.zurarul.saldotracking.ui.home.timeLabel
import dev.zurarul.saldotracking.ui.theme.LocalCashflowColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun InspectorScreen(
    logs: List<NotificationLog>,
    onPromote: (NotificationLog, TransactionDraft) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var activePromote by remember { mutableStateOf<Pair<NotificationLog, TransactionDraft>?>(null) }
    var promotedIds by remember { mutableStateOf(emptySet<Long>()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cashflow = LocalCashflowColors.current
    val now = Instant.now()
    val zone = ZoneId.systemDefault()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Notification Inspector",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onBack) {
                Text("Tutup")
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum ada log notifikasi yang terekam (maksimal 100 notifikasi terakhir disimpan secara lokal).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    val badgeColor = when (log.status) {
                        LogStatus.CAPTURED -> cashflow.income
                        LogStatus.IGNORED_UNPARSED -> cashflow.expense
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = badgeColor.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = badgeLabel(log.status),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = log.sourceApp,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = timeLabel(log.postedAtMillis, now, zone),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (log.title.isNotBlank()) {
                                Text(
                                    text = log.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (log.text.isNotBlank()) {
                                Text(
                                    text = log.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (log.canPromote(promotedIds)) {
                                OutlinedButton(
                                    onClick = { activePromote = log to log.toDraft() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("+ Catat sebagai Transaksi")
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    activePromote?.let { (log, draft) ->
        TransactionSheet(
            initial = draft,
            onSave = { updated ->
                onPromote(log, updated)
                promotedIds = promotedIds + log.id
                activePromote = null
                scope.launch {
                    snackbarHostState.showSnackbar("Transaksi berhasil dicatat")
                }
            },
            onDelete = null,
            onDismiss = { activePromote = null }
        )
    }
}
