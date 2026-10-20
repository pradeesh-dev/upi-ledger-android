package com.newdreamdata.upiledger.parse

import com.newdreamdata.upiledger.data.PayApp
import com.newdreamdata.upiledger.util.Dates
import java.util.Locale

/**
 * Parses Indian bank / wallet debit SMS. Examples it handles:
 *  - "Rs.45.00 debited from A/c XX1234 to VPA tttt@icici on 20-10-26. UPI Ref No 528312345678"
 *  - "INR 250.00 debited from A/c no. XX1234 on 20-10-26 to VPA abc@okaxis (UPI Ref No 528312345678) - ICICI Bank"
 *  - "Sent Rs.45.00 From HDFC Bank A/C *1234 To RAJU TEA STALL On 20/10/26 Ref 528312345678"
 *  - "Dear UPI user A/C X1234 debited by 45.0 on date 20Oct26 trf to RAJU TEA STALL Refno 528312345678"
 *  - "Rs 120 debited from Kotak Bank A/c XX1234 to raju@ybl on 20-10-2026. UPI Ref 528312345678"
 *  - "You paid ₹45 to Raju Tea Stall using Google Pay. UPI transaction ID 528312345678"
 */
object SmsParser {
    private val DEBITED_BY = Regex("""debited\s+(?:by|with|for)\s*(?:Rs\.?|INR|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val PAYEE_PATTERNS = listOf(
        Regex("""\b(?:to|trf to|transferred to|paid to|sent to)\s+VPA\s+(\S+@\S+)(?:\s*\(([^)]+)\))?""", RegexOption.IGNORE_CASE),
        Regex("""\bTo\s+([A-Z][A-Z0-9 &.'\-]{2,40}?)\s+On\s+\d""", RegexOption.IGNORE_CASE),
        Regex("""\btrf to\s+([A-Za-z][A-Za-z0-9 &.'\-]{2,40}?)\s+(?:Refno|Ref|UPI|on)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpaid\s+(?:Rs\.?|INR|₹)?\s*[0-9][0-9,.]*\s+to\s+([A-Za-z][A-Za-z0-9 &.'\-]{2,40}?)(?:\s+(?:using|via|on|through|from)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:to|towards)\s+([A-Z][A-Za-z0-9 &.'\-]{2,40}?)(?:\s+on\s+\d|\s+via\b|\s+ref\b|[.,]|$)""")
    )

    fun looksLikePayment(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        val hasMoney = ParseUtil.AMOUNT_REGEX.containsMatchIn(text) || DEBITED_BY.containsMatchIn(text)
        val hasVerb = listOf("debited", "paid", "sent", "spent", "txn", "transaction").any { it in t }
        val isOtp = "otp" in t || "one time password" in t
        return hasMoney && hasVerb && !isOtp
    }

    fun parse(text: String): Parsed {
        val body = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val amount = DEBITED_BY.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.let { Math.round(it * 100) }
            ?: ParseUtil.amount(body)
        val direction = ParseUtil.direction(body)

        var upi: String? = null
        var payee: String? = null
        for (p in PAYEE_PATTERNS) {
            val m = p.find(body) ?: continue
            val g1 = m.groupValues[1].trim()
            if (g1.contains('@')) {
                upi = g1.trimEnd('.', ',', ')').lowercase(Locale.ROOT)
                m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() && !Regex("""(?i)ref|utr|\d{6,}""").containsMatchIn(it) }?.let { payee = ParseUtil.cleanName(it) }
            } else {
                payee = ParseUtil.cleanName(g1)
            }
            break
        }
        if (upi == null) upi = ParseUtil.UPI_REGEX.find(body)?.value?.lowercase(Locale.ROOT)
        if (payee != null && payee!!.contains('@')) { upi = upi ?: payee!!.lowercase(Locale.ROOT); payee = null }
        // Bank names are not payees
        if (payee != null && Regex("""(?i)\b(bank|a/c|account)\b""").containsMatchIn(payee!!) && upi != null) payee = null

        val ts = ParseUtil.dateTime(body)?.let { Dates.millis(it) }
        val txn = ParseUtil.ref(body)
        val app = ParseUtil.detectApp(body) ?: if (upi != null || "upi" in body.lowercase(Locale.ROOT)) PayApp.BANK else null

        return Parsed(
            amountPaise = amount, upiId = upi, payee = payee, timestamp = ts, txnId = txn,
            app = app, direction = direction, rawText = text
        )
    }
}
