package com.example.tennisalert.fragment

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.tennisalert.R
import com.example.tennisalert.model.Reservation
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class AddReservationDialogFragment(
    private val onReservationAdded: (Reservation) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_reservation, null)

        val datePicker = view.findViewById<DatePicker>(R.id.date_picker)
        val startTimePicker = view.findViewById<TimePicker>(R.id.start_time_picker)
        val endTimePicker = view.findViewById<TimePicker>(R.id.end_time_picker)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_courts)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startTimePicker.setIs24HourView(true)
            startTimePicker.hour = 8
            startTimePicker.minute = 0

            endTimePicker.setIs24HourView(true)
            endTimePicker.hour = 20
            endTimePicker.minute = 0
        } else {
            // On older API, do the 12-hour logic if needed
            startTimePicker.setIs24HourView(true)
            startTimePicker.currentHour = 8
            startTimePicker.currentMinute = 0

            endTimePicker.setIs24HourView(true)
            endTimePicker.currentHour = 20
            endTimePicker.currentMinute = 0
        }

        builder.setView(view)
            .setTitle("Add Reservation")
            .setPositiveButton("Add") { _, _ ->
                val selectedDate = "${datePicker.dayOfMonth}-${datePicker.month + 1}-${datePicker.year}"
                val startHour = if (Build.VERSION.SDK_INT >= 23) startTimePicker.hour else startTimePicker.currentHour
                val startMinute = if (Build.VERSION.SDK_INT >= 23) startTimePicker.minute else startTimePicker.currentMinute
                val endHour = if (Build.VERSION.SDK_INT >= 23) endTimePicker.hour else endTimePicker.currentHour
                val endMinute = if (Build.VERSION.SDK_INT >= 23) endTimePicker.minute else endTimePicker.currentMinute

                val selectedCourts = chipGroup.checkedChipIds.mapNotNull { id ->
                    chipGroup.findViewById<Chip>(id)?.text?.toString()?.toIntOrNull()
                }

                val startTimeStr = String.format("%02d:%02d", startHour, startMinute)
                val endTimeStr = String.format("%02d:%02d", endHour, endMinute)

                onReservationAdded(Reservation(
                    date = selectedDate,
                    startTime = startTimeStr,
                    endTime = endTimeStr,
                    courts = selectedCourts
                ))

            }
            .setNegativeButton("Cancel", null)

        return builder.create()
    }
}
