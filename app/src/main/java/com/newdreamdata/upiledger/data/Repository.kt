package com.newdreamdata.upiledger.data

import com.newdreamdata.upiledger.util.Normalize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId

/** Result of looking a payee up in the registry. */
sealed class Match(val category: String) {
    class ByUpi(val mapping: Mapping) : Match(mapping.category)
    class ByName(val mapping: Mapping) : Match(mapping.category)
    class ByHistory(category: String) : Match(category)
}

data class ImportResult(val inserted: Int, val duplicates: Int, val skipped: Int)

class Repository(private val db: AppDatabase) {
    private val expenses = db.expenses()
    private val mappings = db.mappings()

    val defaultCategories = listOf(
        "Tea & Snacks", "Food", "Groceries", "Transport", "Fuel", "Shopping",
        "Bills & Recharge", "Health", "Entertainment", "Rent", "Transfers", "Other"
    )

    fun categories(): Flow<List<String>> =
        combine(expenses.observeCategories(), mappings.observeCategories()) { a, b ->
            (defaultCategories + a + b).filter { it.isNotBlank() && it != UNCATEGORISED }.distinct()
        }

    // ---- expenses ----
    fun monthRange(ym: YearMonth): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to to
    }

    fun expensesIn(ym: YearMonth): Flow<List<Expense>> = monthRange(ym).let { (f, t) -> expenses.observeBetween(f, t) }
    fun categoryTotals(ym: YearMonth): Flow<List<CategoryTotal>> = monthRange(ym).let { (f, t) -> expenses.observeCategoryTotals(f, t) }
    fun payeeTotals(ym: YearMonth, limit: Int = 8): Flow<List<PayeeTotal>> = monthRange(ym).let { (f, t) -> expenses.observePayeeTotals(f, t, limit) }
    fun reviewCount(): Flow<Int> = expenses.observeReviewCount()
    fun needsReview(): Flow<List<Expense>> = expenses.observeNeedsReview()
    fun quickPayees(limit: Int = 6): Flow<List<QuickPayee>> =
        expenses.observeQuickPayees(System.currentTimeMillis() - 60L * 24 * 3600 * 1000, limit)
            .map { list -> list.filter { it.count >= 2 || list.size <= 3 } }

    fun observeExpense(id: Long): Flow<Expense?> = expenses.observeById(id)
    suspend fun expense(id: Long): Expense? = expenses.byId(id)
    suspend fun allExpenses(): List<Expense> = expenses.all()
    suspend fun expensesBetween(from: Long, to: Long): List<Expense> = expenses.between(from, to)

    suspend fun isDuplicate(e: Expense): Boolean {
        if (e.txnId.isNotBlank() && expenses.countByTxn(e.txnId) > 0) return true
        // Same amount to the same UPI ID within 2 minutes: almost certainly the same payment seen twice
        // (e.g. SMS auto-captured, then the screenshot shared).
        return e.upiId.isNotBlank() && expenses.countSimilar(e.amountPaise, e.timestamp, e.upiId, 2 * 60 * 1000L) > 0
    }

    suspend fun save(e: Expense, remember: Boolean): Long {
        val id = if (e.id == 0L) expenses.insert(e) else { expenses.update(e); e.id }
        if (remember && e.upiId.isNotBlank() && e.category != UNCATEGORISED) {
            upsertMapping(e.upiId, e.category, e.payee)
        }
        return id
    }

    suspend fun delete(e: Expense) = expenses.delete(e)

    // ---- registry ----
    fun mappings(): Flow<List<Mapping>> = mappings.observeAll()

    suspend fun upsertMapping(upiId: String, category: String, payeeName: String) {
        val upi = Normalize.upi(upiId)
        val existing = mappings.byUpi(upi)
        mappings.upsert(
            Mapping(
                id = existing?.id ?: 0,
                upiId = upi,
                category = category.trim(),
                payeeName = payeeName.trim().ifBlank { existing?.payeeName ?: "" },
                payeeKey = Normalize.name(payeeName.ifBlank { existing?.payeeName ?: "" })
            )
        )
    }

    suspend fun deleteMapping(m: Mapping) = mappings.delete(m)

    suspend fun match(upiId: String, payee: String): Match? {
        val upi = Normalize.upi(upiId)
        if (upi.isNotBlank()) mappings.byUpi(upi)?.let { return Match.ByUpi(it) }
        val key = Normalize.name(payee)
        if (key.isNotBlank()) {
            mappings.byPayeeKey(key)?.let { return Match.ByName(it) }
            expenses.lastCategoryForPayee(payee.trim())?.takeIf { it != UNCATEGORISED }?.let { return Match.ByHistory(it) }
        }
        return null
    }

    /** Insert parsed statement rows, skipping duplicates. Categories come from the registry. */
    suspend fun importAll(rows: List<Expense>): ImportResult {
        var inserted = 0; var dup = 0; var skipped = 0
        for (r in rows) {
            if (r.amountPaise <= 0) { skipped++; continue }
            if (isDuplicate(r)) { dup++; continue }
            val m = match(r.upiId, r.payee)
            val e = if (m != null) r.copy(category = m.category, needsReview = false) else r.copy(category = UNCATEGORISED, needsReview = true)
            expenses.insert(e); inserted++
        }
        return ImportResult(inserted, dup, skipped)
    }
}
