package com.newdreamdata.upiledger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newdreamdata.upiledger.LedgerApp
import com.newdreamdata.upiledger.data.CategoryTotal
import com.newdreamdata.upiledger.data.Expense
import com.newdreamdata.upiledger.data.ImportResult
import com.newdreamdata.upiledger.data.Mapping
import com.newdreamdata.upiledger.data.Match
import com.newdreamdata.upiledger.data.PayApp
import com.newdreamdata.upiledger.data.PayeeTotal
import com.newdreamdata.upiledger.data.QuickPayee
import com.newdreamdata.upiledger.data.Source
import com.newdreamdata.upiledger.data.UNCATEGORISED
import com.newdreamdata.upiledger.ocr.Ocr
import com.newdreamdata.upiledger.parse.CsvStatement
import com.newdreamdata.upiledger.parse.Direction
import com.newdreamdata.upiledger.parse.Parsed
import com.newdreamdata.upiledger.parse.ReceiptParser
import com.newdreamdata.upiledger.parse.SmsParser
import com.newdreamdata.upiledger.util.CsvExport
import com.newdreamdata.upiledger.util.Normalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** What the review screen is editing. */
data class Draft(
    val id: Long = 0,
    val amountText: String = "",
    val payee: String = "",
    val upiId: String = "",
    val category: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val app: PayApp = PayApp.GPAY,
    val txnId: String = "",
    val note: String = "",
    val source: Source = Source.MANUAL,
    val remember: Boolean = true,
    val imageUri: Uri? = null,
    val status: String = "",
    val busy: Boolean = false,
    val matchLabel: String? = null,
    val warning: String? = null,
    val duplicate: Boolean = false
) {
    val amountPaise: Long? get() = Normalize.paise(amountText)?.takeIf { it > 0 }
}

sealed class Event {
    data class Toast(val text: String) : Event()
    object Saved : Event()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val ledger = app as LedgerApp
    private val repo = ledger.repository
    val settings = ledger.settings

    val month = MutableStateFlow(YearMonth.now())
    val expenses: StateFlow<List<Expense>> = month.flatMapLatest { repo.expensesIn(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categoryTotals: StateFlow<List<CategoryTotal>> = month.flatMapLatest { repo.categoryTotals(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val payeeTotals: StateFlow<List<PayeeTotal>> = month.flatMapLatest { repo.payeeTotals(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reviewCount: StateFlow<Int> = repo.reviewCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val needsReview: StateFlow<List<Expense>> = repo.needsReview().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val quickPayees: StateFlow<List<QuickPayee>> = repo.quickPayees().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories: StateFlow<List<String>> = repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repo.defaultCategories)
    val mappings: StateFlow<List<Mapping>> = repo.mappings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val smsAuto: StateFlow<Boolean> = settings.smsAutoCaptureFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val draft = MutableStateFlow<Draft?>(null)
    val events = MutableStateFlow<Event?>(null)
    fun consumeEvent() { events.value = null }
    private fun toast(s: String) { events.value = Event.Toast(s) }

    // ---- entry points ----
    fun startManual() { draft.value = Draft(); viewModelScope.launch { refreshMatch() } }

    fun startQuick(q: QuickPayee) {
        draft.value = Draft(
            amountText = formatAmount(q.lastAmountPaise), payee = q.payee, upiId = q.upiId, category = q.category,
            app = q.app, remember = false, matchLabel = "Quick add · last paid ${formatAmount(q.lastAmountPaise)}"
        )
    }

    fun startEdit(id: Long) {
        viewModelScope.launch {
            val e = repo.expense(id) ?: run { toast("That entry no longer exists"); return@launch }
            draft.value = Draft(
                id = e.id, amountText = formatAmount(e.amountPaise), payee = e.payee, upiId = e.upiId,
                category = if (e.category == UNCATEGORISED) "" else e.category, timestamp = e.timestamp, app = e.app,
                txnId = e.txnId, note = e.note, source = e.source, remember = e.needsReview && e.upiId.isNotBlank()
            )
            if (e.needsReview) refreshMatch()
        }
    }

    fun startFromImage(uri: Uri) {
        draft.value = Draft(imageUri = uri, source = Source.SCREENSHOT, busy = true, status = "Reading screenshot…")
        viewModelScope.launch {
            val parsed = try {
                val text = Ocr.recognize(getApplication(), uri)
                ReceiptParser.parse(text)
            } catch (e: Exception) {
                draft.value = draft.value?.copy(busy = false, status = "Couldn't read the image. Fill in the details.")
                return@launch
            }
            applyParsed(parsed, Source.SCREENSHOT)
        }
    }

    fun startFromText(text: String) {
        draft.value = Draft(source = Source.SMS, busy = true, status = "Reading message…")
        viewModelScope.launch { applyParsed(SmsParser.parse(text), Source.SMS) }
    }

    private suspend fun applyParsed(p: Parsed, source: Source) {
        val d = draft.value ?: return
        val warning = when {
            p.isEmpty -> "Couldn't find payment details. Fill them in."
            p.direction == Direction.RECEIVED -> "This looks like money received, not paid. Check before saving."
            p.amountPaise == null -> "Amount not found. Enter it."
            else -> null
        }
        draft.value = d.copy(
            amountText = p.amountPaise?.let { formatAmount(it) } ?: "",
            payee = p.payee ?: "", upiId = p.upiId ?: "", timestamp = p.timestamp ?: System.currentTimeMillis(),
            app = p.app ?: if (source == Source.SMS) PayApp.BANK else PayApp.GPAY, txnId = p.txnId ?: "",
            source = source, busy = false, status = "", warning = warning
        )
        refreshMatch()
    }

    // ---- draft edits ----
    fun update(transform: (Draft) -> Draft) { draft.value = draft.value?.let(transform) }
    fun updateAndMatch(transform: (Draft) -> Draft) { update(transform); viewModelScope.launch { refreshMatch() } }

    private suspend fun refreshMatch() {
        val d = draft.value ?: return
        val m: Match? = repo.match(d.upiId, d.payee)
        val label = when (m) {
            is Match.ByUpi -> "Matched UPI ID → ${m.category}"
            is Match.ByName -> "Matched payee name → ${m.category}"
            is Match.ByHistory -> "Last time this payee was ${m.category}"
            null -> if (Normalize.upi(d.upiId).isNotBlank()) "New UPI ID — pick a category" else null
        }
        val dup = d.id == 0L && d.amountPaise != null && repo.isDuplicate(
            Expense(amountPaise = d.amountPaise!!, upiId = Normalize.upi(d.upiId), timestamp = d.timestamp, txnId = d.txnId.trim())
        )
        draft.value = draft.value?.copy(
            category = if (m != null && draft.value?.category.isNullOrBlank()) m.category else draft.value?.category ?: "",
            matchLabel = label,
            remember = if (m is Match.ByUpi) false else draft.value?.remember ?: true,
            duplicate = dup
        )
    }

    fun save() {
        val d = draft.value ?: return
        val amount = d.amountPaise ?: run { toast("Enter the amount paid"); return }
        val cat = d.category.trim().ifBlank { toast("Choose a category"); return }
        viewModelScope.launch {
            val e = Expense(
                id = d.id, amountPaise = amount, payee = d.payee.trim(), upiId = Normalize.upi(d.upiId), category = cat,
                timestamp = d.timestamp, app = d.app, txnId = d.txnId.trim(), note = d.note.trim(), source = d.source,
                needsReview = false
            )
            repo.save(e, remember = d.remember)
            draft.value = null
            events.value = Event.Saved
            toast("Saved ${formatAmount(amount)} · $cat")
        }
    }

    fun discard() { draft.value = null }

    fun delete(e: Expense) { viewModelScope.launch { repo.delete(e); toast("Deleted") } }

    // ---- registry ----
    fun upsertMapping(upi: String, category: String, name: String) {
        viewModelScope.launch { repo.upsertMapping(upi, category, name); toast("Mapping saved") }
    }
    fun deleteMapping(m: Mapping) { viewModelScope.launch { repo.deleteMapping(m) } }

    // ---- import / export ----
    val importPreview = MutableStateFlow<CsvStatement.Preview?>(null)

    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if (text == null) { toast("Couldn't open that file"); return@launch }
            importPreview.value = CsvStatement.parse(text)
        }
    }
    fun confirmImport() {
        val p = importPreview.value ?: return
        viewModelScope.launch {
            val r: ImportResult = repo.importAll(p.rows)
            importPreview.value = null
            toast("Imported ${r.inserted} · ${r.duplicates} already logged" + if (r.skipped > 0) " · ${r.skipped} skipped" else "")
        }
    }
    fun cancelImport() { importPreview.value = null }

    fun exportCsv(uri: Uri, allMonths: Boolean) {
        viewModelScope.launch {
            val rows = if (allMonths) repo.allExpenses() else repo.monthRange(month.value).let { (f, t) -> repo.expensesBetween(f, t) }
            val csv = CsvExport.build(rows)
            val ok = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } }.isSuccess
            }
            toast(if (ok) "Exported ${rows.size} rows" else "Export failed")
        }
    }

    fun setSmsAuto(v: Boolean) { viewModelScope.launch { settings.setSmsAutoCapture(v) } }

    companion object {
        fun formatAmount(paise: Long): String =
            if (paise % 100 == 0L) (paise / 100).toString() else String.format(java.util.Locale.ROOT, "%.2f", paise / 100.0)
    }
}
