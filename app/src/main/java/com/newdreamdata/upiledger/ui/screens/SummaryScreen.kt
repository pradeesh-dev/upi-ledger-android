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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newdreamdata.upiledger.ui.BarRow
import com.newdreamdata.upiledger.ui.LedgerViewModel
import com.newdreamdata.upiledger.ui.MonthPicker
import com.newdreamdata.upiledger.ui.Mono
import com.newdreamdata.upiledger.util.Dates
import com.newdreamdata.upiledger.util.Money

@Composable
fun SummaryScreen(vm: LedgerViewModel, bottomPadding: PaddingValues) {
    val month by vm.month.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val cats by vm.categoryTotals.collectAsState()
    val payees by vm.payeeTotals.collectAsState()
    val total = expenses.sumOf { it.amountPaise }
    val days = expenses.map { Dates.isoDate(it.timestamp) }.distinct().size

    Column(Modifier.fillMaxSize().padding(bottomPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        MonthPicker(month, { vm.month.value = it }, Modifier.padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Spent", Money.format(total), Modifier.weight(1f))
            Stat("Payments", expenses.size.toString(), Modifier.weight(1f))
            Stat("Per active day", Money.format(if (days > 0) total / days else 0), Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Text("By category", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        if (cats.isEmpty()) Text("No payments in this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val maxCat = cats.firstOrNull()?.totalPaise ?: 0
        cats.forEach { c ->
            val pct = if (total > 0) (100 * c.totalPaise / total) else 0
            BarRow(c.category + " · " + c.count, c.totalPaise, maxCat, Money.format(c.totalPaise) + " · $pct%")
        }
        Spacer(Modifier.height(20.dp))
        Text("Top payees", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        val maxP = payees.firstOrNull()?.totalPaise ?: 0
        payees.forEach { p -> BarRow(p.payee.ifBlank { "Unknown" } + " · " + p.count, p.totalPaise, maxP, Money.format(p.totalPaise)) }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Mono(value, style = MaterialTheme.typography.titleMedium, weight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
