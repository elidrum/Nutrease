package com.example.nutrease.data.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.nutrease.MainActivity
import com.example.nutrease.data.receiver.ReminderAlarmReceiver
import com.example.nutrease.domain.model.ReminderConfig
import com.example.nutrease.domain.repository.ReminderScheduler
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Scheduling dei promemoria via [AlarmManager] con allarmi **esatti** ([AlarmManager.setAlarmClock]):
 * a differenza di WorkManager (tolleranza ~±15 min per batching/Doze) l'orario è rispettato al
 * secondo, requisito RF22 "scarto ≤ 5 min". `setAlarmClock` è sempre esatto ed esente da Doze, ma
 * **richiede comunque** il permesso exact-alarm (`USE_EXACT_ALARM` su API 33+, `SCHEDULE_EXACT_ALARM`
 * su API 31-32): senza dichiararlo nel manifest il sistema lancia `SecurityException`. Entrambi
 * sono concessi all'installazione, quindi non serve un flusso runtime.
 *
 * Una schedulazione = un allarme one-shot per ogni coppia (promemoria, giorno). Allo sparo, il
 * [ReminderAlarmReceiver] mostra la notifica e ri-arma la settimana successiva. La matematica del
 * prossimo slot vive in [ReminderScheduling] (pura, testabile). L'interfaccia resta
 * [ReminderScheduler], così il dominio non vede AlarmManager.
 */
class AlarmManagerReminderScheduler(
    private val context: Context
) : ReminderScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Registro dei promemoria attivi: AlarmManager non sa elencare i propri allarmi, quindi
    // ci serve per implementare cancelAll() in modo deterministico.
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun schedule(config: ReminderConfig) {
        val configId = config.id ?: return
        val message = config.message?.takeIf { it.isNotBlank() } ?: ReminderConfig.DEFAULT_MESSAGE
        config.daysOfWeek.forEach { day ->
            arm(configId, day, config.time.hour, config.time.minute, message)
        }
        rememberConfig(configId)
    }

    /**
     * Arma (o ri-arma) il singolo slot (promemoria, giorno) alla prossima occorrenza settimanale.
     * Usato sia da [schedule] sia dal [ReminderAlarmReceiver] dopo lo sparo (gli allarmi esatti
     * sono one-shot e vanno re-impostati).
     *
     * `setAlarmClock` è sempre esatto e Doze-exempt, ma richiede il permesso exact-alarm dichiarato
     * nel manifest (`USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`). Essendo permessi concessi
     * all'installazione, `canScheduleExactAlarms()` è sempre true qui e non serve un guard runtime.
     * Segnalazione conservativa del lint → soppressa: `MissingPermission` (lint Gradle) +
     * `ScheduleExactAlarm` (ispezione in-editor), stesso warning ma ID diverso secondo il contesto.
     */
    @SuppressLint("MissingPermission", "ScheduleExactAlarm")
    fun arm(configId: Long, day: DayOfWeek, hour: Int, minute: Int, message: String) {
        val tz = TimeZone.currentSystemDefault()
        val now: LocalDateTime = Clock.System.now().toLocalDateTime(tz)
        val delayMs = ReminderScheduling.delayUntilNext(now, day, hour, minute, tz)
        val triggerAtMillis = System.currentTimeMillis() + delayMs
        val isoDay = day.ordinal + 1

        val operation = firePendingIntent(configId, isoDay, hour, minute, message, mutableUpdate = true)!!
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent()),
            operation
        )
    }

    override fun cancel(configId: Long) {
        for (isoDay in 1..7) {
            // FLAG_NO_CREATE → ritorna l'eventuale PendingIntent già armato (extras ignorati nel
            // matching: contano action+data+component, che ricostruiamo identici).
            firePendingIntent(configId, isoDay, 0, 0, "", mutableUpdate = false)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
        forgetConfig(configId)
    }

    override fun cancelAll() {
        activeConfigIds().forEach { cancel(it) }
    }

    private fun firePendingIntent(
        configId: Long,
        isoDay: Int,
        hour: Int,
        minute: Int,
        message: String,
        mutableUpdate: Boolean
    ): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE
            // Uri unica per (promemoria, giorno): ogni allarme è distinto anche a parità di requestCode.
            data = "nutrease://reminder/$configId/$isoDay".toUri()
            putExtra(ReminderAlarmReceiver.EXTRA_CONFIG_ID, configId)
            putExtra(ReminderAlarmReceiver.EXTRA_ISO_DAY, isoDay)
            putExtra(ReminderAlarmReceiver.EXTRA_HOUR, hour)
            putExtra(ReminderAlarmReceiver.EXTRA_MINUTE, minute)
            putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, message)
        }
        val flags = if (mutableUpdate) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        }
        return PendingIntent.getBroadcast(context, requestCode(configId, isoDay), intent, flags)
    }

    /**
     * PendingIntent mostrato quando l'utente tocca l'allarme nella status bar: apre l'app.
     * requestCode dedicato (≠ 0) per non aliasare con il contentIntent della notifica
     * (anch'esso `getActivity` su MainActivity con requestCode 0): con UPDATE_CURRENT il re-arm
     * sovrascriverebbe gli extra della notifica (perderebbe `nav_target=diary`).
     */
    private fun showPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQ_CODE_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun requestCode(configId: Long, isoDay: Int): Int = (configId * 10 + isoDay).toInt()

    private fun rememberConfig(configId: Long) {
        val ids = activeConfigIds().toMutableSet().apply { add(configId) }
        prefs.edit { putStringSet(KEY_ACTIVE, ids.map { it.toString() }.toSet()) }
    }

    private fun forgetConfig(configId: Long) {
        val ids = activeConfigIds().toMutableSet().apply { remove(configId) }
        prefs.edit { putStringSet(KEY_ACTIVE, ids.map { it.toString() }.toSet()) }
    }

    private fun activeConfigIds(): Set<Long> =
        prefs.getStringSet(KEY_ACTIVE, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    companion object {
        private const val PREFS_NAME = "reminder_alarms"
        private const val KEY_ACTIVE = "active_config_ids"
        // requestCode dedicato allo showIntent: i requestCode dei broadcast sono configId*10+iso
        // (piccoli) e comunque di tipo diverso (getBroadcast), quindi nessuna collisione.
        private const val REQ_CODE_SHOW = 1_000_000
    }
}
