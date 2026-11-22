package com.example.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    private const val CHANNEL_ID = "kitchen_expiry_alerts"
    private const val CHANNEL_NAME = "Mutfak Tüketim Uyarıları"
    private const val CHANNEL_DESC = "Ürünlerin son tüketim tarihi yaklaştığında uyarı gönderir."

    private const val TIMER_CHANNEL_ID = "cooking_timer_alerts"
    private const val TIMER_CHANNEL_NAME = "Pişirme Zamanlayıcı Bildirimleri"
    private const val TIMER_CHANNEL_DESC = "Pişirme sayacı dolduğunda sesli bildirim gönderir."

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val expiryChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESC
            }
            notificationManager.createNotificationChannel(expiryChannel)

            val timerChannel = NotificationChannel(TIMER_CHANNEL_ID, TIMER_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = TIMER_CHANNEL_DESC
            }
            notificationManager.createNotificationChannel(timerChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun sendNotification(context: Context, id: Int, title: String, message: String) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }

    fun sendCookingTimerDoneNotification(context: Context, recipeTitle: String) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 9001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val msg = "$recipeTitle için pişirme sayacı tamamlandı!"
        val builder = NotificationCompat.Builder(context, TIMER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pişirme Süresi Doldu!")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(9001, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }
}
