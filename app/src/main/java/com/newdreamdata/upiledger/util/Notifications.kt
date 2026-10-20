package com.newdreamdata.upiledger.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.newdreamdata.upiledger.MainActivity
import com.newdreamdata.upiledger.R
import com.newdreamdata.upiledger.data.Expense
import com.newdreamdata.upiledger.data.UNCATEGORISED

object Notifications {
    const val CHANNEL = "review"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
        )
    }

    fun paymentLogged(context: Context, id: Long, e: Expense) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("upiledger://expense/$id"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id.toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val who = e.payee.ifBlank { e.upiId.ifBlank { "Unknown payee" } }
        val title = if (e.needsReview) "Categorise ${Money.format(e.amountPaise)} to $who" else "${Money.format(e.amountPaise)} · ${e.category}"
        val text = if (e.needsReview) "Tap to pick a category. It'll be remembered for next time." else "Logged automatically from SMS · $who"
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (e.needsReview) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(id.toInt(), n)
    }
}
