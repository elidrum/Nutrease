package com.example.nutrease.data.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.nutrease.MainActivity
import com.example.nutrease.R

/**
 * Costruisce e mostra la notifica locale del promemoria diario. Centralizza canale
 * (creato idempotente all'avvio dell'app e ricontrollato qui) e deep-link: il tap
 * apre MainActivity con extra `nav_target=diary`, che la fa navigare al diario.
 */
object ReminderNotificationBuilder {

    const val CHANNEL_ID = "diary_reminder"
    const val NOTIFICATION_ID = 1001
    const val EXTRA_NAV_TARGET = "nav_target"
    const val NAV_TARGET_DIARY = "diary"

    private const val CHANNEL_NAME = "Promemoria diario"
    private const val CHANNEL_DESCRIPTION =
        "Promemoria giornaliero per la compilazione del diario alimentare"

    /** Crea il notification channel se manca (obbligatorio da API 26; idempotente). */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        manager.createNotificationChannel(channel)
    }

    // POST_NOTIFICATIONS è dichiarato nel manifest e richiesto a runtime (API 33+) quando esiste
    // un promemoria abilitato; la notify() è comunque in runCatching (se il permesso manca, no-op
    // senza crash). Il lint MissingPermission non riconosce runCatching → soppresso.
    @SuppressLint("MissingPermission")
    fun show(context: Context, message: String) {
        ensureChannel(context)

        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_TARGET, NAV_TARGET_DIARY)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.reminder_notification_title)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}