package com.newdreamdata.upiledger.parse

import com.newdreamdata.upiledger.data.Expense
import com.newdreamdata.upiledger.data.PayApp
import com.newdreamdata.upiledger.data.Source
import com.newdreamdata.upiledger.data.UNCATEGORISED
import com.newdreamdata.upiledger.util.Dates
import java.util.Locale

/**
 * Reads a bank statement CSV (HDFC, ICICI, SBI, Axis, Kotak and most others) and returns debit rows
 * as expenses. Column roles are detected from the header row, so the exact layout doesn't matter.
 */
object CsvStatement {
    data class Preview(val rows: List<Expense>, val debitRows: Int, val creditRowsIgnored: Int, val headerFound: Boolean)

    fun parse(text: String): Preview {
        val table = readCsv(text).filter { row -> row.any { it.isNotBlank() } }
        if (table.isEmpty()) return Preview(emptyList(), 0, 0, false)

        // Header: first row that names a date column and some amount/debit column.
        val headerIdx = table.indexOfFirst { r ->
            val lower = r.map { it.lowercase(Locale.ROOT) }
            lower.any { "date" in it } && lower.any { c -> listOf("debit", "withdraw", "amount", "dr").any { it in c } }
        }
        if (headerIdx < 0) return Preview(emptyList(), 0, 0, false)
        val header = table[headerIdx].map { it.lowercase(Locale.ROOT).trim() }

        fun col(vararg keys: String, exclude: List<String> = emptyList()): Int =
            header.indexOfFirst { h -> keys.any { it in h } && exclude.none { it in h } }

        val cDate = col("transaction date", "txn date", "value date", "date")
        val cNarr = col("narration", "description", "particulars", "remarks", "details", "transaction remarks", "merchant")
        val cDebit = col("withdrawal", "debit", "dr amount", "dr", exclude = listOf("credit", "cr"))
        val cCredit = col("deposit", "credit", "cr amount", exclude = listOf("debit"))
        val cAmount = col("amount", exclude = listOf("debit", "credit", "withdraw", "deposit", "balance"))
        val cType = col("dr/cr", "type", "cr/dr", "transaction type")
        val cRef = col("ref", "utr", "chq", "cheque", "transaction id", "txn id")

        val out = mutableListOf<Expense>()
        var credits = 0
        for (row in table.drop(headerIdx + 1)) {
            fun cell(i: Int) = if (i >= 0 && i < row.size) row[i].trim() else ""
            val dateText = cell(cDate)
            val ldt = ParseUtil.dateTime(dateText) ?: continue
            val narr = cell(cNarr)

            var debitPaise: Long? = null
            if (cDebit >= 0) debitPaise = money(cell(cDebit))
            if (debitPaise == null && cAmount >= 0) {
                val amt = money(cell(cAmount))
                val type = cell(cType).lowercase(Locale.ROOT)
                val isDebit = when {
                    type.isNotBlank() -> type.startsWith("d") || type == "dr" || "debit" in type
                    amt != null && amt < 0 -> true
                    cCredit >= 0 -> money(cell(cCredit)) == null
                    else -> true
                }
                if (isDebit && amt != null) debitPaise = kotlin.math.abs(amt)
            }
            if (debitPaise == null || debitPaise <= 0) {
                if ((cCredit >= 0 && money(cell(cCredit)) != null) || (cAmount >= 0 && money(cell(cAmount)) != null)) credits++
                continue
            }

            // Look inside each narration segment first: HDFC-style "UPI-RAJU TEA STALL-raju@okicici-..." would
            // otherwise match "STALL-raju@okicici" because '-' is a valid VPA character.
            val upi = (narr.split('/', '-', '|').firstNotNullOfOrNull { ParseUtil.UPI_REGEX.find(it)?.value }
                ?: ParseUtil.UPI_REGEX.find(narr)?.value)?.lowercase(Locale.ROOT) ?: ""
            val payee = payeeFromNarration(narr, upi)
            val ref = cell(cRef).takeIf { it.isNotBlank() && it != "0" } ?: refFromNarration(narr) ?: ""
            val app = ParseUtil.detectApp(narr) ?: if (upi.isNotBlank() || narr.uppercase(Locale.ROOT).startsWith("UPI")) PayApp.BANK else PayApp.OTHER

            out += Expense(
                amountPaise = debitPaise,
                payee = payee,
                upiId = upi,
                category = UNCATEGORISED,
                timestamp = Dates.millis(ldt),
                app = app,
                txnId = ref,
                note = if (upi.isBlank() && payee.isBlank()) narr.take(80) else "",
                source = Source.IMPORT,
                needsReview = true
            )
        }
        return Preview(out, out.size, credits, true)
    }

    private fun money(s: String): Long? {
        val t = s.replace(Regex("[₹,\\sA-Za-z]"), "").trim()
        if (t.isBlank()) return null
        val neg = t.startsWith("-") || t.startsWith("(")
        val v = t.trim('-', '(', ')').toDoubleOrNull() ?: return null
        val p = Math.round(v * 100)
        return if (neg) -p else p
    }

    /** "UPI/528312345678/Payment/raju@okicici/Raju Tea Stall" -> "Raju Tea Stall". */
    private fun payeeFromNarration(narr: String, upi: String): String {
        if (narr.isBlank()) return ""
        val parts = narr.split('/', '-', '|').map { it.trim() }.filter { it.isNotBlank() }
        val skip = Regex("""(?i)^(upi|imps|neft|rtgs|p2m|p2a|dr|cr|payment|pay|oth|to|from|collect|\d+|[a-z0-9]{1,3}|.*\bbank\b.*)$""")
        val cand = parts.filter { p ->
            !p.contains('@') && !skip.matches(p) && !Regex("""^\d{6,}$""").matches(p) && p.length in 3..48
        }
        val best = cand.maxByOrNull { it.count { ch -> ch.isLetter() } } ?: return if (upi.isNotBlank()) "" else narr.take(40)
        return ParseUtil.cleanName(best)
    }

    private fun refFromNarration(narr: String): String? = Regex("""\b\d{12}\b""").find(narr)?.value

    // ---- minimal RFC 4180 reader ----
    fun readCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < s.length && s[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                    } else cell.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(cell.toString()); cell.setLength(0) }
                c == '\r' -> {}
                c == '\n' -> { row.add(cell.toString()); cell.setLength(0); rows.add(row.toList()); row.clear() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row.toList()) }
        return rows
    }
}
