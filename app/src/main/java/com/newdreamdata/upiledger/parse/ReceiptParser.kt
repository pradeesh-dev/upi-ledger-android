package com.newdreamdata.upiledger.parse

import com.newdreamdata.upiledger.util.Dates
import java.util.Locale

/**
 * Turns OCR text from a GPay / Amazon Pay / PhonePe / Paytm confirmation screen into fields.
 * OCR output is one line per visual line, top to bottom.
 */
object ReceiptParser {
    private val TO_LINE = Regex("""^(?:paid\s+)?to[:\s]+(.+)$""", RegexOption.IGNORE_CASE)
    private val TO_ONLY = Regex("""^(?:paid\s+)?to[:]?$""", RegexOption.IGNORE_CASE)
    private val FROM_LINE = Regex("""^from[:\s]""", RegexOption.IGNORE_CASE)
    private val NOISE = Regex("""(?i)^(?:paid|payment|successful|completed|done|₹|rs\.?|upi|bank|share|split|view details|details|pay again|home|ok|done)$""")
    private val PAID_SUCCESS_TO = Regex("""(?i)(?:paid|sent|transferred)\s+(?:successfully\s+)?to\s+(.+)""")

    fun parse(ocrText: String): Parsed {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val joined = lines.joinToString("\n")

        // ---- amount: first currency-marked number; else a standalone numeric line near the top ----
        var amount = ParseUtil.amount(joined)
        if (amount == null) {
            for (l in lines.take(8)) {
                val m = ParseUtil.BARE_AMOUNT_LINE.find(l) ?: continue
                val v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
                if (v in 1.0..999999.0 && m.groupValues[1].length < 10) { amount = Math.round(v * 100); break }
            }
        }

        // ---- UPI IDs: receiver first. Skip anything right after a "From" line (that's the payer). ----
        val upis = mutableListOf<String>()
        var afterFrom = false
        for (l in lines) {
            if (FROM_LINE.containsMatchIn(l)) afterFrom = true
            val found = ParseUtil.UPI_REGEX.find(l)?.value?.lowercase(Locale.ROOT)
            if (found != null) {
                if (!afterFrom) upis.add(0, found) else upis.add(found)
                afterFrom = false
            }
        }
        val upi = upis.firstOrNull()

        // ---- payee name ----
        var payee: String? = null
        loop@ for ((i, l) in lines.withIndex()) {
            TO_LINE.find(l)?.let { m ->
                val cand = ParseUtil.cleanName(m.groupValues[1])
                if (!cand.contains('@') && cand.length > 1) { payee = cand; return@let }
            }
            if (payee != null) break@loop
            if (TO_ONLY.matches(l)) {
                val next = lines.getOrNull(i + 1) ?: continue
                if (!next.contains('@') && !NOISE.matches(next)) { payee = ParseUtil.cleanName(next); break@loop }
            }
            PAID_SUCCESS_TO.find(l)?.let { m ->
                val cand = ParseUtil.cleanName(m.groupValues[1])
                if (!cand.contains('@') && cand.length > 1) payee = cand
            }
            if (payee != null) break@loop
        }
        if (payee == null && upi != null) {
            // Name usually sits on the line just above the UPI ID
            val idx = lines.indexOfFirst { it.lowercase(Locale.ROOT).contains(upi) }
            val above = lines.getOrNull(idx - 1)
            if (above != null && !NOISE.matches(above) && !ParseUtil.AMOUNT_REGEX.containsMatchIn(above) && above.length in 2..48) {
                payee = ParseUtil.cleanName(above.removePrefix("To").removePrefix("to").trim(':', ' '))
            }
        }
        // Strip trailing phone numbers / masked bank text sometimes merged into the name line
        payee = payee?.replace(Regex("""\s*[+(]?\d[\d\s\-()]{7,}$"""), "")?.trim()?.takeIf { it.isNotBlank() }

        val ts = ParseUtil.dateTime(joined)?.let { Dates.millis(it) }
        val txn = ParseUtil.ref(joined)
        val app = ParseUtil.detectApp(joined)
        val direction = ParseUtil.direction(joined)

        return Parsed(
            amountPaise = amount, upiId = upi, payee = payee, timestamp = ts, txnId = txn,
            app = app, direction = direction, rawText = ocrText
        )
    }
}
