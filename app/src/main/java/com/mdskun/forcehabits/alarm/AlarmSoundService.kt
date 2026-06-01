package com.mdskun.forcehabits.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mdskun.forcehabits.MainActivity
import com.mdskun.forcehabits.R

class AlarmSoundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START_ALARM = "START_ALARM"
        const val ACTION_STOP_ALARM  = "STOP_ALARM"
        const val NOTIF_ID           = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> {
                val habitId   = intent.getLongExtra("habit_id", -1L)
                val habitName = intent.getStringExtra("habit_name") ?: "Habit"
                startAlarm(habitId, habitName)
            }
            ACTION_STOP_ALARM -> stopAlarm()
            else -> stopSelf()
        }
        return START_STICKY
    }

    private fun startAlarm(habitId: Long, habitName: String) {
        // Wake lock — keep CPU alive for up to 10 minutes
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "StrictHabit::AlarmWakeLock"
        ).also { it.acquire(10 * 60 * 1000L) }

        startForeground(NOTIF_ID, buildNotification(habitName, habitId))

        // Use alarm sound, fall back to ringtone
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(applicationContext, alarmUri)
            isLooping = true
            prepare()
            start()
        }

        // Force maximum alarm volume
        val am = getSystemService(AudioManager::class.java)
        am.setStreamVolume(
            AudioManager.STREAM_ALARM,
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )
    }

    private fun stopAlarm() {
        mediaPlayer?.runCatching { if (isPlaying) stop(); release() }
        mediaPlayer = null
        wakeLock?.runCatching { release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(habitName: String, habitId: Long): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("open_habit_id", habitId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "alarm_channel")
            .setContentTitle("⏰ $habitName")
            .setContentText("Complete your habit proof to stop the alarm")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openIntent)
            .setFullScreenIntent(openIntent, true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.runCatching { if (isPlaying) stop(); release() }
        wakeLock?.runCatching { release() }
    }
}
