package app.medassist

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.medassist.core.Reminder

/** Reminders from the analysis plan: repeat a test, visit a doctor. Kept on the phone only. */
object Reminders {
    private const val CHANNEL = "reminders"

    fun schedule(context: Context, r: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pending(context, r))
    }

    fun cancel(context: Context, r: Reminder) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, r))
        NotificationManagerCompat.from(context).cancel(r.id)
    }

    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        Store(context).reminders.forEach { r ->
            schedule(context, if (r.at < now) r.copy(at = now + 60_000) else r)
        }
    }

    private fun pending(context: Context, r: Reminder): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            r.id,
            Intent(context, ReminderReceiver::class.java)
                .putExtra("id", r.id)
                .putExtra("text", r.text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun show(context: Context, id: Int, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("МедАнализ: напоминание")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(id, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        Reminders.show(context, id, intent.getStringExtra("text") ?: "")
        val store = Store(context)
        store.reminders = store.reminders.filterNot { it.id == id }
    }
}

/** Alarms are lost on reboot; set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.rescheduleAll(context)
    }
}
