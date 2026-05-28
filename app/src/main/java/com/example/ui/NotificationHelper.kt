package com.example.ui

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID = "maco_budget_alerts"
    const val NOTIFICATION_ID = 2390

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Pengingat Anggaran MACO"
            val descriptionText = "Saluran untuk notifikasi mingguan pengingat budget dapet meningkatkan kesehatan finansial Anda."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(context: Context, title: String, message: String) {
        createNotificationChannel(context)

        // Check permission inline
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Notification permission not granted, skipping notification")
                return
            }
        }

        // Target activity when user taps the notification
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Standard fallback icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(0xFFFF5722.toInt()) // Orange theme color

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending notification", e)
        }
    }

    fun scheduleWeeklyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BudgetReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            24512,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set to Saturday at 8 PM (20:00)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        // If calendar time is in the past, move it to next week
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Successfully scheduled weekly reminder for Saturday 8 PM")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot schedule strict alarm due to security or platform restrictions, falling back to non-exact strategy", e)
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Failed all alarm scheduling fallback strategies", ex)
            }
        }
    }
}

class BudgetReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BudgetReminderReceiver", "Alarm broadcast received!")
        
        // Let's run a coroutine scope to get the remaining budget and construct a hyper-personalized weekend advice!
        val db = AppDatabase.getInstance(context)
        val monthYear = "Mei 2026" // default or check current month
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch income and expenses to check financial health
                val income = db.dao.getSumByType(monthYear, "Pemasukan").first() ?: 0.0
                val savings = db.dao.getSumByType(monthYear, "Tabungan").first() ?: 0.0
                val bills = db.dao.getSumByType(monthYear, "Tagihan").first() ?: 0.0
                val expensePlans = db.dao.getSumByType(monthYear, "Pengeluaran").first() ?: 0.0
                
                val totalSpend = savings + bills + expensePlans
                val sisa = income - totalSpend
                
                val (title, text) = if (sisa < 0) {
                    Pair(
                        "⚠️ MACO Alert: Anggaran Melebihi Batas!",
                        "Kesehatan keuangan kamu defisit Rp%,.0f akhir pekan ini. Kurangi pengeluaran hiburan/makan!"
                            .format(kotlin.math.abs(sisa)).replace(",", ".")
                    )
                } else if (sisa < 500000.0) {
                    Pair(
                        "👀 MACO Reminder: Anggaran Menipis!",
                        "Sisa anggaran kamu tinggal Rp%,.0f. Tetap waspada di akhir pekan ya!"
                            .format(sisa).replace(",", ".")
                    )
                } else {
                    Pair(
                        "✨ MACO Weekend: Keuangan Sehat!",
                        "Bagus! Sisa anggaran kamu aman sebesar Rp%,.0f. Pertahankan kedisiplinan keuangan!"
                            .format(sisa).replace(",", ".")
                    )
                }
                
                NotificationHelper.showReminderNotification(context, title, text)
                
                // Reschedule for next week
                NotificationHelper.scheduleWeeklyReminder(context)
            } catch (e: Exception) {
                // Safe absolute fallback if DB query fails on receiver
                NotificationHelper.showReminderNotification(
                    context, 
                    "Reminder Anggaran Akhir Pekan MACO", 
                    "Ayo pantau anggaran mingguan Anda di aplikasi MACO untuk menjaga stabilitas finansial!"
                )
            }
        }
    }
}
