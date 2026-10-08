package com.example.polywake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("ALARM_MESSAGE") ?: "C'est l'heure !"

        // 1. FORCER L'ALLUMAGE DE LA DALLE LUMINEUSE (WakeLock)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "PolyWake::AlarmWakeLock"
        )
        // Maintient l'écran allumé pendant 3 minutes max
        wakeLock.acquire(3 * 60 * 1000L)

        // 2. LANCER DIRECTEMENT L'ÉCRAN D'ALARME (Exploite la permission Xiaomi)
        val ringIntent = Intent(context, RingActivity::class.java).apply {
            putExtra("ALARM_MESSAGE", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        context.startActivity(ringIntent)
    }
}