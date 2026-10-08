package com.example.polywake

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DailyEdtWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            executeAlarmCheck(applicationContext)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

suspend fun executeAlarmCheck(
    context: Context,
    targetDate: LocalDate = LocalDate.now(),
    useCache: Boolean = false
): Pair<String, List<String>>? {
    val settingsManager = SettingsManager(context)
    val prefs = settingsManager.userSettingsFlow.first()

    if (prefs.groups.isEmpty()) return null

    val dateStr = targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

    // 2. Logique de récupération : on tente le cache si demandé, sinon on télécharge
    var courses = if (useCache) NetworkManager.getCachedEvents(context, dateStr) else emptyList()

    if (courses.isEmpty()) {
        courses = NetworkManager.getEvents(context, dateStr, dateStr, prefs.groups)
    }

    if (courses.isEmpty()) return null

    val firstCourse = courses.minByOrNull { it.start_at } ?: return null
    val courseTime = OffsetDateTime.parse(firstCourse.start_at).toInstant()

    val y_time = 0 // Temps de trajet provisoire
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val alarmList = mutableListOf<String>()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    // 1. Alarme de départ
    val departureTime = courseTime.minus((y_time + prefs.z_time).toLong(), ChronoUnit.MINUTES)
    if (prefs.alarms_enabled) {
        setSystemAlarmClock(context, alarmManager, departureTime, "Départ Polytech !", 999)
    } else {
        cancelSystemAlarm(context, alarmManager, 999)
    }
    alarmList.add("Départ : ${timeFormatter.format(departureTime)}")
    // 2. Boucle des réveils
    val lastWakeUpTime = departureTime.minus(prefs.x_time.toLong(), ChronoUnit.MINUTES)
    for (i in 0 until prefs.n_alarms) {
        val wakeUpTime = lastWakeUpTime.minus((i * prefs.interval).toLong(), ChronoUnit.MINUTES)
        val msg = "Réveil ${prefs.n_alarms - i}/${prefs.n_alarms}"
        if (prefs.alarms_enabled) {
            setSystemAlarmClock(context, alarmManager, wakeUpTime, msg, i)
        } else {
            cancelSystemAlarm(context, alarmManager, i)
        }
        alarmList.add("$msg : ${timeFormatter.format(wakeUpTime)}")
    }

    val firstCourseTimeStr = OffsetDateTime.parse(firstCourse.start_at).format(DateTimeFormatter.ofPattern("HH:mm"))

    // On renvoie l'heure du cours ET la liste des alarmes triées par ordre chronologique
    return Pair(firstCourseTimeStr, alarmList.sorted())
}

// Rendue publique (retrait de "private") pour être utilisable par le bouton de test
fun setSystemAlarmClock(context: Context, alarmManager: AlarmManager, time: java.time.Instant, message: String, requestCode: Int) {
    if (time.isAfter(java.time.Instant.now())) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_MESSAGE", message)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context, requestCode, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(time.toEpochMilli(), showPendingIntent)

        try {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            e.printStackTrace()
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.toEpochMilli(), pendingIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}

fun cancelSystemAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int) {
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}