package com.newdreamdata.upiledger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newdreamdata.upiledger.data.Expense
import com.newdreamdata.upiledger.data.UNCATEGORISED
import com.newdreamdata.upiledger.ui.CategoryPill
import com.newdreamdata.upiledger.ui.EmptyState
import com.newdreamdata.upiledger.ui.LedgerViewModel
import com.newdreamdata.upiledger.ui.MonthPicker
import com.newdreamdata.upiledger.ui.Mono
import com.newdreamdata.upiledger.ui.Pill
import com.newdreamdata.upiledger.util.Dates
import com.newdreamdata.upiledger.util.Money

@Composable
fun HomeScreen(vm: LedgerViewModel, bottomPadding: PaddingValues) {
    val month by vm.month.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    val needsReview by vm.needsReview.collectAsState()
    val quick by vm.quickPayees.collectAsState()
    var pendingDelete by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        modifier = Modifier.padding(bottomPadding),
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { vm.startManual() }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add") })
        }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    MonthPicker(month, { vm.month.value = it })
                    val total = expenses.sumOf { it.amountPaise }
                    Mono(Money.format(total), style = MaterialTheme.typography.titleMedium, weight = FontWeight.SemiBold, modifier = Modifier.padding(end = 12.dp))
                }
            }

            if (reviewCount > 0) item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text("$reviewCount payment${if (reviewCount > 1) "s" else ""} to categorise", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        needsReview.take(3).forEach { e ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.startEdit(e.id) }.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.payee.ifBlank { e.upiId.ifBlank { "Unknown payee" } }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(Dates.full(e.timestamp) + " · " + e.source.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Mono(Money.format(e.amountPaise))
                            }
                        }
                        if (needsReview.size > 3) Text("and ${needsReview.size - 3} more below", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (quick.isNotEmpty()) item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("Quick add", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quick) { q ->
                            AssistChip(
                                onClick = { vm.startQuick(q) },
                                label = { Text(q.payee.ifBlank { q.upiId } + " · " + Money.format(q.lastAmountPaise), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }

            if (expenses.isEmpty()) item {
                EmptyState(
                    "No expenses this month",
                    "Share a GPay or Amazon Pay screenshot to UPI Ledger, or let bank SMS log payments automatically. Tap Add for cash."
                )
            }

            val byDay = expenses.groupBy { Dates.isoDate(it.timestamp) }
            byDay.forEach { (_, dayRows) ->
                item(key = "h" + dayRows.first().timestamp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(Dates.day(dayRows.first().timestamp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Mono(Money.format(dayRows.sumOf { it.amountPaise }), style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                items(dayRows, key = { it.id }) { e ->
                    ExpenseRow(e, onClick = { vm.startEdit(e.id) }, onDelete = { pendingDelete = e })
                }
            }
        }
    }

    pendingDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this expense?") },
            text = { Text("${Money.format(e.amountPaise)} to ${e.payee.ifBlank { e.upiId.ifBlank { "unknown payee" } }}") },
            confirmButton = { TextButton(onClick = { vm.delete(e); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ExpenseRow(e: Expense, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.payee.ifBlank { e.upiId.ifBlank { "Unknown payee" } }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e.needsReview || e.category == UNCATEGORISED) Pill("Needs category", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary)
                else CategoryPill(e.category)
                Text(Dates.time(e.timestamp) + " · " + e.app.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (e.upiId.isNotBlank() || e.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(listOf(e.upiId, e.note).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Mono(Money.format(e.amountPaise), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
}
