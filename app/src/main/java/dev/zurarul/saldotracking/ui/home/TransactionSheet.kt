package dev.zurarul.saldotracking.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.zurarul.saldotracking.domain.MANUAL_SOURCE
import dev.zurarul.saldotracking.domain.TxType
import dev.zurarul.saldotracking.domain.Wallet
import dev.zurarul.saldotracking.ui.theme.LocalCashflowColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionSheet(
    initial: TransactionDraft,
    onSave: (TransactionDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var amountInput by remember(initial) { mutableStateOf(initial.amountInput) }
    var type by remember(initial) { mutableStateOf(initial.type) }
    var sourceApp by remember(initial) { mutableStateOf(initial.sourceApp) }
    var title by remember(initial) { mutableStateOf(initial.title) }
    var timestampMillis by remember(initial) { mutableLongStateOf(initial.timestampMillis) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()
    val currentZdt = Instant.ofEpochMilli(timestampMillis).atZone(zone)
    val cashflow = LocalCashflowColors.current
    val sources = remember {
        listOf(MANUAL_SOURCE) + Wallet.entries.map { it.displayName }
    }
    val canSave = parseAmountInput(amountInput) != null && title.trim().isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initial.id == 0L) "Tambah Transaksi" else "Edit Transaksi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = type == TxType.INCOME,
                    onClick = { type = TxType.INCOME },
                    label = { Text("+ Masuk (Pemasukan)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = cashflow.income.copy(alpha = 0.2f),
                        selectedLabelColor = cashflow.income
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = type == TxType.EXPENSE,
                    onClick = { type = TxType.EXPENSE },
                    label = { Text("- Keluar (Pengeluaran)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = cashflow.expense.copy(alpha = 0.2f),
                        selectedLabelColor = cashflow.expense
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                label = { Text("Nominal (Rp)") },
                placeholder = { Text("25.000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Judul / Keterangan") },
                placeholder = { Text("Kopi Kenangan / Transfer Masuk") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Sumber Aplikasi",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sources.forEach { src ->
                    FilterChip(
                        selected = sourceApp == src,
                        onClick = { sourceApp = src },
                        label = { Text(src) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(currentZdt.toLocalDate().toString())
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(String.format(Locale.US, "%02d:%02d", currentZdt.hour, currentZdt.minute))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = cashflow.expense),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hapus")
                    }
                }
                Button(
                    onClick = {
                        onSave(
                            initial.copy(
                                amountInput = amountInput,
                                type = type,
                                sourceApp = sourceApp,
                                title = title.trim(),
                                timestampMillis = timestampMillis
                            )
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Simpan")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = currentZdt.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { utcMillis ->
                            val pickedDate = Instant.ofEpochMilli(utcMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            timestampMillis = pickedDate
                                .atTime(currentZdt.toLocalTime())
                                .atZone(zone)
                                .toInstant()
                                .toEpochMilli()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Batal")
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = currentZdt.hour,
            initialMinute = currentZdt.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedTime = LocalTime.of(timeState.hour, timeState.minute)
                        val pickedDate: LocalDate = currentZdt.toLocalDate()
                        timestampMillis = pickedDate
                            .atTime(pickedTime)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Batal")
                }
            },
            text = {
                TimePicker(state = timeState)
            }
        )
    }
}
