package com.newdreamdata.upiledger.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.newdreamdata.upiledger.LedgerApp
import com.newdreamdata.upiledger.data.Expense
import com.newdreamdata.upiledger.data.PayApp
import com.newdreamdata.upiledger.data.Source
import com.newdreamdata.upiledger.data.UNCATEGORISED
import com.newdreamdata.upiledger.parse.Direction
import com.newdreamdata.upiledger.parse.SmsParser
import com.newdreamdata.upiledger.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens for bank debit SMS and logs them automatically. Known UPI IDs are categorised silently;
 * unknown ones are saved as "Uncategorised" and a notification asks you to pick a category.
 */
class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as LedgerApp
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val sender = messages.firstOrNull()?.originatingAddress ?: ""
        if (body.isBlank() || !SmsParser.looksLikePayment(body)) return
        // Personal numbers rarely send bank alerts; sender IDs look like "VM-HDFCBK" / "AX-ICICIB".
        if (sender.matches(Regex("""^\+?\d{10,}$"""))) return

        val pending = goAsync()
        scope.launch {
            try {
                if (!app.settings.smsAutoCapture()) return@launch
                val p = SmsParser.parse(body)
                if (p.direction == Direction.RECEIVED) return@launch
                val amount = p.amountPaise ?: return@launch
                val repo = app.repository
                val draft = Expense(
                    amountPaise = amount,
                    payee = p.payee ?: "",
                    upiId = p.upiId ?: "",
                    timestamp = p.timestamp ?: System.currentTimeMillis(),
                    app = p.app ?: PayApp.BANK,
                    txnId = p.txnId ?: "",
                    source = Source.SMS,
                    note = ""
                )
                if (repo.isDuplicate(draft)) return@launch
                val match = repo.match(draft.upiId, draft.payee)
                val e = if (match != null) draft.copy(category = match.category, needsReview = false)
                        else draft.copy(category = UNCATEGORISED, needsReview = true)
                val id = repo.save(e, remember = false)
                Notifications.paymentLogged(context, id, e)
            } finally {
                pending.finish()
            }
        }
    }
}
