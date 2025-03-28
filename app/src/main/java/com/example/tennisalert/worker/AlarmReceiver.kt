package com.example.tennisalert.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.example.tennisalert.model.Reservation
import com.example.tennisfetcher.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

class AlarmReceiver : BroadcastReceiver() {

    private val checker = CourtAvailabilityChecker(MeraFetcher())
    private val dateFormatter = DateTimeFormatter.ofPattern("d-M-yyyy")

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AlarmReceiver", "Alarm triggered!")
        val prefs = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val intervalMillis = prefs.getLong("intervalMillis", 60_000L)

        CoroutineScope(Dispatchers.IO).launch {
            performCheck(context)
            scheduleNextAlarm(context, intervalMillis)
        }
    }

    private suspend fun performCheck(context: Context) {
        val prefs = context.getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("reservations", null) ?: return
        val type = object : TypeToken<MutableList<Reservation>>() {}.type
        val reservationList = Gson().fromJson<MutableList<Reservation>>(json, type)

        var hasAvailability = false

        for (res in reservationList) {
            Log.d("AlarmReceiver", "Checking Reservation: $res")

            val date = LocalDate.parse(res.date, dateFormatter)
            val startTime = date.atTime(LocalTime.parse(res.startTime))
            val endTime = date.atTime(LocalTime.parse(res.endTime))
            val dateRange = DateRange(startTime, endTime)

            for (courtId in res.courts) {
                val court = Court(courtId)
                val request = CourtRequest(dateRange, court, nonOverlapping = true)
                val availabilityList = checker.check(request)

                if (availabilityList.isNotEmpty()) {
                    hasAvailability = true
                    Log.d("AlarmReceiver", "Court $courtId availability count: ${availabilityList.size}")
                    availabilityList.forEachIndexed { index, item ->
                        Log.d("AlarmReceiver", "Availability[$index]: From ${item.start} to ${item.end}")
                    }
                    break
                }
            }

            if (hasAvailability) {
                break
            }
        }

        if (hasAvailability) {
            showNotification(context)
        }
    }

    private fun showNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "reservation_alert_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Reservation Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Reservation Alert")
            .setContentText("Time to check new tennis reservations!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        notificationManager.notify(1, notification)
    }

    private fun scheduleNextAlarm(context: Context, intervalMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("AlarmReceiver", "Exact alarm permission not granted on API >= 31.")
                return
            }
        }

        val nextIntent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + intervalMillis,
                pendingIntent
            )
            Log.d("AlarmReceiver", "Rescheduled next alarm in $intervalMillis ms")
        } catch (e: SecurityException) {
            Log.e("AlarmReceiver", "SecurityException scheduling alarm: ${e.message}")
        }
    }
}
