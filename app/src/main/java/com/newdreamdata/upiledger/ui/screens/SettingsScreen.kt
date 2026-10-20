package com.newdreamdata.upiledger.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.newdreamdata.upiledger.ui.LedgerViewModel
import com.newdreamdata.upiledger.util.Money
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: LedgerViewModel, bottomPadding: PaddingValues) {
    val ctx = LocalContext.current
    val smsAuto by vm.smsAuto.collectAsState()
    val month by vm.month.collectAsState()
    val preview by vm.importPreview.collectAsState()
    var smsGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        smsGranted = res[Manifest.permission.RECEIVE_SMS] == true
        if (smsGranted) vm.setSmsAuto(true)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.previewImport(it) } }
    val monthTag = month.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    val exportMonth = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.exportCsv(it, allMonths = false) } }
    val exportAll = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.exportCsv(it, allMonths = true) } }

    Column(Modifier.fillMaxSize().padding(bottomPadding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Automatic capture", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Log payments from bank SMS", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (smsGranted) "Debit alerts are logged as they arrive. Known UPI IDs are categorised silently; new ones get a notification."
                    else "Needs the SMS permission. Messages are read on this phone only and never leave it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = smsAuto && smsGranted, onCheckedChange = { on ->
                if (on && !smsGranted) {
                    val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
                    if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                    permLauncher.launch(perms.toTypedArray())
                } else vm.setSmsAuto(on)
            })
        }
        Text("Tip: share a payment screenshot from GPay or Amazon Pay and pick “Log in UPI Ledger” in the share sheet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Bank statement import", style = MaterialTheme.typography.titleMedium)
        Text("Open a CSV statement from your bank or GPay's transaction export. Debits are added; anything already logged (same reference, or same amount and UPI ID at the same time) is skipped.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import CSV statement") }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Export", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { exportMonth.launch("upi-expenses-$monthTag.csv") }, modifier = Modifier.weight(1f)) { Text("This month") }
            OutlinedButton(onClick = { exportAll.launch("upi-expenses-all.csv") }, modifier = Modifier.weight(1f)) { Text("Everything") }
        }
        Spacer(Modifier.height(8.dp))
        Text("Data stays on this phone in a local database (included in Android backups). CSV export is your portable copy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.cancelImport() },
            title = { Text(if (p.headerFound) "Import ${p.debitRows} debit rows?" else "Couldn't read this statement") },
            text = {
                if (!p.headerFound) Text("No header row with a date and a debit/amount column was found. Export the statement as CSV from your bank's site or app and try again.")
                else Column {
                    Text("${p.debitRows} debits totalling ${Money.format(p.rows.sumOf { it.amountPaise })}. ${p.creditRowsIgnored} credit rows ignored.")
                    Spacer(Modifier.height(8.dp))
                    Text("Rows with a known UPI ID are categorised from your registry; the rest are marked for review.", style = MaterialTheme.typography.bodySmall)
                    val sample = p.rows.take(3)
                    if (sample.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        sample.forEach { Text("• ${Money.format(it.amountPaise)} → ${it.payee.ifBlank { it.upiId.ifBlank { it.note } }}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = { if (p.headerFound && p.debitRows > 0) Button(onClick = { vm.confirmImport() }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { vm.cancelImport() }) { Text(if (p.headerFound) "Cancel" else "Close") } }
        )
    }
}
