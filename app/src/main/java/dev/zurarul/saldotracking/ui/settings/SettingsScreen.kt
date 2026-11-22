package dev.zurarul.saldotracking.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zurarul.saldotracking.data.BackupData
import dev.zurarul.saldotracking.data.BackupFormatException
import dev.zurarul.saldotracking.data.ThemeMode
import dev.zurarul.saldotracking.domain.Wallet
import dev.zurarul.saldotracking.domain.formatRupiah
import dev.zurarul.saldotracking.ui.theme.LocalCashflowColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val accessGranted by viewModel.accessGranted.collectAsStateWithLifecycle()
    val batteryExempt by viewModel.batteryExempt.collectAsStateWithLifecycle()
    val listenerConnected by viewModel.listenerConnected.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val cashflow = LocalCashflowColors.current

    var showInspector by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<BackupData?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.setKeepAlive(context, true)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val json = viewModel.exportJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("Gagal membuka file")
                    }
                }
                val msg = if (result.isSuccess) "Backup berhasil disimpan" else "Gagal menyimpan backup"
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val rawJson = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader(Charsets.UTF_8).readText()
                        } ?: throw BackupFormatException("File kosong")
                    }
                    pendingImport = viewModel.prepareImport(rawJson)
                } catch (_: BackupFormatException) {
                    snackbarHostState.showSnackbar("File backup tidak valid")
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar("File backup tidak valid")
                }
            }
        }
    }

    if (showInspector) {
        InspectorScreen(
            logs = logs,
            onPromote = { log, draft ->
                viewModel.promoteLog(log, draft)
            },
            onBack = { showInspector = false }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 1. Status & Setup OPPO A17 / Android
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Status & Setup (OPPO A17 / ColorOS)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    val active = accessGranted || listenerConnected
                    Text(
                        text = if (active) "Status Listener: Auto-Track ON" else "Status Listener: Auto-Track PAUSED",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) cashflow.income else cashflow.expense
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { SetupActions.openNotificationAccess(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (accessGranted) "Akses Notifikasi (ON)" else "Buka Akses Notifikasi")
                        }
                        OutlinedButton(
                            onClick = { SetupActions.requestIgnoreBatteryOptimizations(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (batteryExempt) "Baterai (Bebas)" else "Izin Baterai")
                        }
                    }

                    OutlinedButton(
                        onClick = { SetupActions.openAppDetails(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Buka Info Aplikasi (App Info)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keep-Alive Notification",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Notifikasi senyap agar ColorOS tidak mematikan Auto-Track di latar belakang",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.keepAliveEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setKeepAlive(context, enabled)
                                }
                            }
                        )
                    }

                    Text(
                        text = "Panduan OPPO A17 / ColorOS 12.1:\n" +
                            "• Settings › Battery › App battery management › Saldo Tracking → aktifkan Allow background activity + Allow auto launch\n" +
                            "• Buka Recents › ketuk ⋮ di atas Saldo Tracking › pilih Lock\n" +
                            "• Android 13+: jika Akses Notifikasi dikunci, buka App Info › ⋮ › Allow restricted settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Aplikasi Terpantau
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Aplikasi Terpantau",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Wallet.entries.forEach { wallet ->
                        val isEnabled = wallet !in settings.disabledWallets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = wallet.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = wallet.packages.joinToString(", "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.setWalletEnabled(wallet, checked)
                                }
                            )
                        }
                    }
                }
            }

            // 3. Notification Inspector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showInspector = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Inspector",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lihat ${logs.size} notifikasi terakhir (Captured, Promo, Non-Saldo, Unparsed) & catat manual 1-tap",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Buka ›",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // 4. Tema
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Tema",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ThemeMode.SYSTEM to "Ikuti Sistem",
                            ThemeMode.DARK to "Gelap",
                            ThemeMode.LIGHT to "Terang"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 5. Backup Lokal
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Backup Lokal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Simpan atau pulihkan seluruh riwayat transaksi dan saldo awal sebagai file JSON di perangkat Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                                exportLauncher.launch("saldo-backup-$dateStr.json")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Import JSON")
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    pendingImport?.let { data ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Pulihkan Backup JSON?") },
            text = {
                Text(
                    "Backup berisi ${data.transactions.size} transaksi dan base balance ${formatRupiah(data.baseBalanceIdr)}. Data saat ini akan digantikan oleh isi backup."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.applyImport(data) {
                            pendingImport = null
                            scope.launch {
                                snackbarHostState.showSnackbar("Backup berhasil dipulihkan")
                            }
                        }
                    }
                ) {
                    Text("Pulihkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
