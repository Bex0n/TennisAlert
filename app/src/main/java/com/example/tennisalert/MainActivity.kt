package com.example.tennisalert

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tennisalert.adapter.ReservationAdapter
import com.example.tennisalert.fragment.AddReservationDialogFragment
import com.example.tennisalert.model.Reservation
import com.example.tennisalert.worker.AlarmReceiver
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.Spinner
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var reservationList: MutableList<Reservation>
    private lateinit var adapter: ReservationAdapter
    private lateinit var switchAlarm: SwitchMaterial
    private lateinit var spinnerInterval: Spinner
    private var intervalMillis = 60_000L

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
        private const val PREF_KEY = "myPrefs"
        private const val RESERVATION_LIST_KEY = "reservations"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchAlarm = findViewById(R.id.switchAlarm)
        spinnerInterval = findViewById(R.id.spinnerInterval)

        ArrayAdapter.createFromResource(
            this,
            R.array.interval_choices,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerInterval.adapter = adapter
        }

        spinnerInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val chosen = parent.getItemAtPosition(position).toString()
                intervalMillis = when (chosen) {
                    "15 sec" -> 15_000L
                    "1 min" -> 60_000L
                    "2 min" -> 2 * 60_000L
                    "5 min" -> 5 * 60_000L
                    "10 min" -> 10 * 60_000L
                    "15 min" -> 15 * 60_000L
                    "30 min" -> 30 * 60_000L
                    else -> 10 * 60_000L
                }
                if (switchAlarm.isChecked) scheduleExactAlarm()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        switchAlarm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) scheduleExactAlarm() else cancelAlarm()
        }

        reservationList = loadReservations()

        adapter = ReservationAdapter(reservationList, ::removeReservation)
        findViewById<RecyclerView>(R.id.recycler_view).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            showAddReservationDialog()
        }

        requestPostNotificationsPermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (canScheduleExactAlarms()) {
                scheduleExactAlarm()
            } else {
                promptForExactAlarmPermission()
            }
        } else {
            scheduleExactAlarm()
        }
    }

    private fun scheduleExactAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + intervalMillis,
            pendingIntent
        )

        getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("intervalMillis", intervalMillis)
            .apply()

        Log.d("MainActivity", "Exact alarm scheduled: $intervalMillis ms.")
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(pendingIntent)
        Log.d("MainActivity", "Alarm cancelled.")
    }

    private fun showAddReservationDialog() {
        val dialog = AddReservationDialogFragment { reservation ->
            reservationList.add(reservation)
            adapter.notifyItemInserted(reservationList.size - 1)
            saveReservations()
        }
        dialog.show(supportFragmentManager, "AddReservationDialog")
    }

    private fun removeReservation(position: Int) {
        reservationList.removeAt(position)
        adapter.notifyItemRemoved(position)
        saveReservations()
    }

    private fun saveReservations() {
        val prefs = getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
        val json = Gson().toJson(reservationList)
        prefs.edit().putString(RESERVATION_LIST_KEY, json).apply()
    }

    private fun loadReservations(): MutableList<Reservation> {
        val prefs = getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
        val json = prefs.getString(RESERVATION_LIST_KEY, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Reservation>>() {}.type
        return Gson().fromJson(json, type)
    }

    private fun requestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun promptForExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Post Notifications Permission Granted")
            } else {
                Log.d("MainActivity", "Post Notifications Permission Denied")
            }
        }
    }
}