package com.newdreamdata.upiledger.parse

import com.newdreamdata.upiledger.data.PayApp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

enum class Direction { PAID, RECEIVED, UNKNOWN }

/** Fields extracted from a screenshot, SMS or statement row. Any field may be missing. */
data class Parsed(
    val amountPaise: Long? = null,
    val upiId: String? = null,
    val payee: String? = null,
    val timestamp: Long? = null,
    val txnId: String? = null,
    val app: PayApp? = null,
    val direction: Direction = Direction.UNKNOWN,
    val rawText: String = ""
) {
    val isEmpty: Boolean get() = amountPaise == null && upiId == null && payee == null && txnId == null
}

internal object ParseUtil {
    val UPI_REGEX = Regex("""[A-Za-z0-9][A-Za-z0-9._\-]{1,}@[A-Za-z][A-Za-z0-9]{1,}""")
    val AMOUNT_REGEX = Regex("""(?:₹|Rs\.?|INR|rs\.?)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    val BARE_AMOUNT_LINE = Regex("""^\s*[₹?Rs.]*\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]{1,7}(?:\.[0-9]{1,2})?)\s*$""")
    val REF_REGEX = Regex(
        """(?:UPI\s*(?:transaction|txn)\s*ID|transaction\s*ID|txn\s*ID|UPI\s*Ref(?:erence)?(?:\s*(?:No\.?|ID|#))?|Ref(?:erence)?(?:\s*(?:No\.?|ID|#))?|UTR(?:\s*No\.?)?|RRN)\s*[:#.\-]?\s*([0-9A-Za-z]{10,})""",
        RegexOption.IGNORE_CASE
    )
    val TWELVE_DIGITS = Regex("""\b[0-9]{12}\b""")

    fun amount(text: String): Long? {
        val m = AMOUNT_REGEX.find(text) ?: return null
        return m.groupValues[1].replace(",", "").toDoubleOrNull()?.takeIf { it > 0 && it < 10_000_000 }?.let { Math.round(it * 100) }
    }

    fun ref(text: String): String? {
        REF_REGEX.find(text)?.groupValues?.get(1)?.let { return it }
        return TWELVE_DIGITS.find(text)?.value
    }

    fun detectApp(text: String): PayApp? {
        val t = text.lowercase(Locale.ROOT)
        return when {
            "amazon pay" in t || "amazonpay" in t || "@apl" in t -> PayApp.AMAZON_PAY
            "google pay" in t || "g pay" in t || "gpay" in t || "@ok" in t -> PayApp.GPAY
            "phonepe" in t || "@ybl" in t || "@ibl" in t || "@axl" in t -> PayApp.PHONEPE
            "paytm" in t || "@paytm" in t || "@ptyes" in t || "@ptaxis" in t -> PayApp.PAYTM
            else -> null
        }
    }

    fun direction(text: String): Direction {
        val t = text.lowercase(Locale.ROOT)
        val received = listOf("credited", "received", "money received", "payment received", "has been credited")
        val paid = listOf("debited", "paid", "sent", "payment successful", "you paid", "paid to", "spent")
        val r = received.any { it in t }
        val p = paid.any { it in t }
        return when {
            r && !p -> Direction.RECEIVED
            p -> Direction.PAID
            else -> Direction.UNKNOWN
        }
    }

    private val MONTHS = Month.values().associateBy { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(Locale.ROOT) }

    fun month(s: String): Month? = MONTHS[s.lowercase(Locale.ROOT).take(3)]

    /** Parses dates like 20-10-26, 20/10/2026, 20Oct26, 20 Oct 2026, 2026-10-20, 20-Oct-2026. */
    private val NUMERIC = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b""")
    private val ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val ALPHA = Regex("""\b(\d{1,2})\s*[-\s]?\s*([A-Za-z]{3,9})\.?,?\s*[-\s]?\s*(\d{2,4})\b""")
    private val TIME = Regex("""\b(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([aApP][mM])?\b""")

    fun dateTime(text: String): LocalDateTime? {
        val date: LocalDate = run {
            ISO.find(text)?.let { m ->
                return@run runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
            }
            NUMERIC.find(text)?.let { m ->
                val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); var y = m.groupValues[3].toInt()
                if (y < 100) y += 2000
                return@run runCatching { LocalDate.of(y, mo, d) }.getOrNull()
            }
            for (m in ALPHA.findAll(text)) {
                val mo = month(m.groupValues[2]) ?: continue
                var y = m.groupValues[3].toInt(); if (y < 100) y += 2000
                val d = runCatching { LocalDate.of(y, mo, m.groupValues[1].toInt()) }.getOrNull()
                if (d != null) return@run d
            }
            null
        } ?: return null
        val time = TIME.find(text)?.let { m ->
            var h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            val ampm = m.groupValues[4].lowercase(Locale.ROOT)
            if (ampm == "pm" && h < 12) h += 12
            if (ampm == "am" && h == 12) h = 0
            runCatching { LocalTime.of(h, mi) }.getOrNull()
        } ?: LocalTime.NOON
        return LocalDateTime.of(date, time)
    }

    fun cleanName(s: String): String = s.trim()
        .trimEnd('.', ',', ':', '-', '|')
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
}
