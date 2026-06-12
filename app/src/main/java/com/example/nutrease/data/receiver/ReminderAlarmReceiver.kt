package com.example.nutrease.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.nutrease.data.notification.ReminderNotificationBuilder
import com.example.nutrease.data.scheduler.AlarmManagerReminderScheduler
import com.example.nutrease.domain.model.ReminderConfig
import kotlinx.datetime.DayOfWeek

/**
 * Riceve lo sparo dell'allarme esatto ([AlarmManagerReminderScheduler]): mostra la notifica del
 * promemoria e **ri-arma** lo stesso slot per la settimana successiva (gli allarmi di
 * `setAlarmClock` sono one-shot).
 *
 * Niente Hilt: i BroadcastReceiver sono istanziati dal sistema. Le operazioni sono brevi e
 * sincrone (mostra notifica + re-arm), quindi restano nel budget di `onReceive`.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return

        val message = intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
            ?: ReminderConfig.DEFAULT_MESSAGE
        ReminderNotificationBuilder.show(context, message)

        val configId = intent.getLongExtra(EXTRA_CONFIG_ID, -1L)
        val isoDay = intent.getIntExtra(EXTRA_ISO_DAY, -1)
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
        if (configId >= 0 && isoDay in 1..7 && hour in 0..23 && minute in 0..59) {
            val day = DayOfWeek.entries[isoDay - 1] // isoDay 1..7 → MONDAY..SUNDAY (ordinal 0..6)
            AlarmManagerReminderScheduler(context).arm(configId, day, hour, minute, message)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.example.nutrease.action.REMINDER_FIRE"
        const val EXTRA_CONFIG_ID = "config_id"
        const val EXTRA_ISO_DAY = "iso_day"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_MESSAGE = "message"
    }
}
