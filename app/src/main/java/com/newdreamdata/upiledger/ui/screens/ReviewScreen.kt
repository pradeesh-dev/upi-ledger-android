package com.newdreamdata.upiledger.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.newdreamdata.upiledger.data.PayApp
import com.newdreamdata.upiledger.ui.Draft
import com.newdreamdata.upiledger.ui.LedgerViewModel
import com.newdreamdata.upiledger.ui.Pill
import com.newdreamdata.upiledger.util.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: LedgerViewModel, draft: Draft) {
    val categories by vm.categories.collectAsState()
    var newCategory by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == 0L) "New expense" else "Edit expense") },
                navigationIcon = { IconButton(onClick = { vm.discard() }) { Icon(Icons.Default.Close, "Discard") } }
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().imePadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.discard() }, modifier = Modifier.weight(1f)) { Text("Discard") }
                Button(onClick = { vm.save() }, modifier = Modifier.weight(2f), enabled = !draft.busy) { Text(if (draft.id == 0L) "Save expense" else "Save changes") }
            }
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Preview + status
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                draft.imageUri?.let { uri -> Thumb(uri) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (draft.busy) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(draft.status, style = MaterialTheme.typography.bodySmall)
                    } else if (draft.status.isNotBlank()) Text(draft.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    draft.matchLabel?.let { label ->
                        val isNew = label.startsWith("New") || label.startsWith("No ")
                        if (isNew) Pill(label, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary)
                        else Pill(label, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    draft.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (draft.duplicate) Text("Looks like this payment is already logged (same transaction ID, or same amount to the same UPI ID within 2 minutes).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            OutlinedTextField(
                value = draft.amountText, onValueChange = { v -> vm.update { it.copy(amountText = v.filter { c -> c.isDigit() || c == '.' }) } },
                label = { Text("Amount") }, prefix = { Text("₹") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.payee, onValueChange = { v -> vm.update { it.copy(payee = v) } },
                label = { Text("Paid to") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            OutlinedTextField(
                value = draft.upiId, onValueChange = { v -> vm.updateAndMatch { it.copy(upiId = v.trim().lowercase()) } },
                label = { Text("UPI ID") }, placeholder = { Text("name@bank") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            )

            // Category
            if (!newCategory) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = draft.category, onValueChange = {}, readOnly = true, label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        placeholder = { Text("Choose a category") }
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { vm.update { it.copy(category = c) }; expanded = false })
                        }
                        DropdownMenuItem(text = { Text("+ New category…") }, onClick = { newCategory = true; vm.update { it.copy(category = "") }; expanded = false })
                    }
                }
            } else {
                OutlinedTextField(
                    value = draft.category, onValueChange = { v -> vm.update { it.copy(category = v) } },
                    label = { Text("New category name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    trailingIcon = { TextButton(onClick = { newCategory = false }) { Text("Pick existing") } }
                )
            }

            // Date / time
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) { Text(Dates.day(draft.timestamp)) }
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(0.7f)) { Text(Dates.time(draft.timestamp)) }
            }

            // App
            var appOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = appOpen, onExpandedChange = { appOpen = it }) {
                OutlinedTextField(
                    value = draft.app.label, onValueChange = {}, readOnly = true, label = { Text("App") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(appOpen) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = appOpen, onDismissRequest = { appOpen = false }) {
                    PayApp.values().forEach { a -> DropdownMenuItem(text = { Text(a.label) }, onClick = { vm.update { it.copy(app = a) }; appOpen = false }) }
                }
            }

            OutlinedTextField(
                value = draft.txnId, onValueChange = { v -> vm.update { it.copy(txnId = v) } },
                label = { Text("Transaction ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
            OutlinedTextField(
                value = draft.note, onValueChange = { v -> vm.update { it.copy(note = v) } },
                label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            if (draft.upiId.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = draft.remember, onCheckedChange = { c -> vm.update { it.copy(remember = c) } })
                Text("Remember this UPI ID for this category", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.local(draft.timestamp).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d: LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val t = Dates.local(draft.timestamp).toLocalTime()
                        vm.update { it.copy(timestamp = Dates.millis(LocalDateTime.of(d, t))) }
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val cur = Dates.local(draft.timestamp)
        val state = rememberTimePickerState(initialHour = cur.hour, initialMinute = cur.minute)
        Dialog(onDismissRequest = { showTime = false }) {
            Column(Modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showTime = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        val ldt = LocalDateTime.of(cur.toLocalDate(), java.time.LocalTime.of(state.hour, state.minute))
                        vm.update { it.copy(timestamp = Dates.millis(ldt)) }
                        showTime = false
                    }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun Thumb(uri: android.net.Uri) {
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(uri).use { s ->
                    val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeStream(s, null, o)
                }
            }.getOrNull()
        }
    }
    Box(Modifier.size(width = 84.dp, height = 150.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        bmp?.let { Image(it.asImageBitmap(), "Screenshot", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}
