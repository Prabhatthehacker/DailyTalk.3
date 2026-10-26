package com.dailytalk.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Local notifications, shown while the app process is alive. Remote push is not implemented (see README). */
object Notifier {
    private const val MESSAGES = "messages"
    private const val CALLS = "calls"
    private const val CALL_ID = 9001

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun allowed(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33

    private fun open(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Suppress("MissingPermission")
    fun message(ctx: Context, conversationId: String, title: String, text: String) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, MESSAGES).setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(open(ctx))
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        NotificationManagerCompat.from(ctx).notify(conversationId.hashCode(), n)
    }

    @Suppress("MissingPermission")
    fun incomingCall(ctx: Context, name: String, video: Boolean) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CALLS).setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Incoming ${if (video) "video" else "voice"} call").setContentText(name)
            .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(open(ctx), true).setContentIntent(open(ctx)).setTimeoutAfter(45_000).build()
        NotificationManagerCompat.from(ctx).notify(CALL_ID, n)
    }

    fun cancelCall(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(CALL_ID)
}
