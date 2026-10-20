package com.newdreamdata.upiledger.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Normalize {
    fun upi(s: String?): String = (s ?: "").trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "")
    fun name(s: String?): String = (s ?: "").trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
    fun paise(rupees: Double): Long = Math.round(rupees * 100)
    fun paise(text: String): Long? = text.replace(",", "").trim().toDoubleOrNull()?.let { paise(it) }
}

object Money {
    private val fmt: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    fun format(paise: Long): String = fmt.format(paise / 100.0).replace(".00", "")
    fun formatExact(paise: Long): String = fmt.format(paise / 100.0)
}

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val fullFmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val isoTime = DateTimeFormatter.ofPattern("HH:mm")

    fun local(ts: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)
    fun millis(ldt: LocalDateTime): Long = ldt.atZone(zone).toInstant().toEpochMilli()
    fun day(ts: Long): String = local(ts).format(dayFmt)
    fun time(ts: Long): String = local(ts).format(timeFmt)
    fun full(ts: Long): String = local(ts).format(fullFmt)
    fun isoDate(ts: Long): String = local(ts).format(isoDate)
    fun isoTime(ts: Long): String = local(ts).format(isoTime)
}
