package com.newdreamdata.upiledger.util

import com.newdreamdata.upiledger.data.Expense

object CsvExport {
    fun build(rows: List<Expense>): String {
        val sb = StringBuilder("date,time,amount,category,payee,upi_id,app,transaction_id,source,note\n")
        for (r in rows.sortedBy { it.timestamp }) {
            sb.append(Dates.isoDate(r.timestamp)).append(',')
                .append(Dates.isoTime(r.timestamp)).append(',')
                .append(String.format(java.util.Locale.ROOT, "%.2f", r.amountPaise / 100.0)).append(',')
                .append(q(r.category)).append(',')
                .append(q(r.payee)).append(',')
                .append(q(r.upiId)).append(',')
                .append(q(r.app.label)).append(',')
                .append(q(r.txnId)).append(',')
                .append(r.source.name.lowercase()).append(',')
                .append(q(r.note)).append('\n')
        }
        return sb.toString()
    }
    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
}
