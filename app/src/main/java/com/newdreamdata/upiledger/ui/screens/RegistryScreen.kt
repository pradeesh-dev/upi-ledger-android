package com.newdreamdata.upiledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newdreamdata.upiledger.ui.CategoryPill
import com.newdreamdata.upiledger.ui.EmptyState
import com.newdreamdata.upiledger.ui.LedgerViewModel
import com.newdreamdata.upiledger.ui.Mono

@Composable
fun RegistryScreen(vm: LedgerViewModel, bottomPadding: PaddingValues) {
    val mappings by vm.mappings.collectAsState()
    var upi by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(bottomPadding), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("UPI ID registry", style = MaterialTheme.typography.titleLarge)
            Text("Each UPI ID maps to a category. Payments to a known ID are categorised without asking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(upi, { upi = it.trim().lowercase() }, label = { Text("UPI ID") }, placeholder = { Text("tttt@icici") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cat, { cat = it }, label = { Text("Category") }, placeholder = { Text("Tea") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(name, { name = it }, label = { Text("Payee name") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                when {
                    !upi.contains('@') -> error = "Enter a UPI ID like name@bank."
                    cat.isBlank() -> error = "Enter a category."
                    else -> { error = ""; vm.upsertMapping(upi, cat, name); upi = ""; cat = ""; name = "" }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Add mapping") }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
        }
        if (mappings.isEmpty()) item {
            EmptyState("Registry is empty", "Add a UPI ID above, or save an expense with “Remember this UPI ID” ticked.")
        }
        items(mappings, key = { it.id }) { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Mono(m.upiId, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryPill(m.category)
                        if (m.payeeName.isNotBlank()) Text(m.payeeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = { upi = m.upiId; cat = m.category; name = m.payeeName }) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = { vm.deleteMapping(m) }) { Icon(Icons.Default.Delete, "Remove") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
