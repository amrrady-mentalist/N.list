package com.notesapp.offline.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.notesapp.offline.R

/**
 * Global memory storage and trigger dispatcher for the Delete Peek magic effect.
 *
 * Captures deleted words from any note in real-time, stores them in memory, and
 * delivers them via:
 * 1) Inject API (if enabled with an apiUrl)
 * 2) Local push notification to the device (if enabled)
 * when a physical trigger (Volume button or Proximity sensor) is pressed/tripped.
 */
object DeletePeekMemory {
    /** The last deleted text/word captured from any note in memory. */
    var lastDeletedWord: String = ""

    /** Notification Channel ID for Delete Peek push notifications. */
    private const val CHANNEL_ID = "magic_delete_peek_channel"
    private const val NOTIFICATION_ID = 9002

    fun recordDeletion(deletedText: String) {
        val trimmed = deletedText.trim()
        if (trimmed.isNotEmpty()) {
            lastDeletedWord = trimmed
        }
    }

    fun showPushNotification(context: Context, word: String) {
        if (word.isBlank()) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notes System Alerts"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Deleted Content")
            .setContentText(word)
            .setStyle(NotificationCompat.BigTextStyle().bigText(word))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
