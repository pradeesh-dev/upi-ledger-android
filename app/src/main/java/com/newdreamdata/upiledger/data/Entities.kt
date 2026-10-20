package com.newdreamdata.upiledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Source { SCREENSHOT, SMS, MANUAL, IMPORT }

enum class PayApp(val label: String) {
    GPAY("GPay"), AMAZON_PAY("Amazon Pay"), PHONEPE("PhonePe"), PAYTM("Paytm"), BANK("Bank"), OTHER("Other")
}

const val UNCATEGORISED = "Uncategorised"

@Entity(
    tableName = "expenses",
    indices = [Index("timestamp"), Index("txnId"), Index("upiId"), Index("needsReview")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Amount in paise to avoid floating point drift. */
    val amountPaise: Long,
    val payee: String = "",
    val upiId: String = "",
    val category: String = UNCATEGORISED,
    /** Epoch millis of the payment. */
    val timestamp: Long,
    val app: PayApp = PayApp.OTHER,
    val txnId: String = "",
    val note: String = "",
    val source: Source = Source.MANUAL,
    /** True when the category was not resolved automatically and the user has not confirmed it. */
    val needsReview: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis()
) {
    val amountRupees: Double get() = amountPaise / 100.0
}

@Entity(tableName = "mappings", indices = [Index(value = ["upiId"], unique = true), Index("payeeKey")])
data class Mapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Normalised (lower-case, trimmed) UPI ID. */
    val upiId: String,
    val category: String,
    /** Display name of the payee as seen on receipts. */
    val payeeName: String = "",
    /** Normalised payee name used for fallback matching. */
    val payeeKey: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class CategoryTotal(val category: String, val totalPaise: Long, val count: Int)
data class PayeeTotal(val payee: String, val upiId: String, val totalPaise: Long, val count: Int)
data class QuickPayee(
    val payee: String,
    val upiId: String,
    val category: String,
    val lastAmountPaise: Long,
    val app: PayApp,
    val count: Int
)
