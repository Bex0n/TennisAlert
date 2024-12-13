package com.example.tennisalert.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.DatePicker
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.tennisalert.R
import com.example.tennisalert.model.Reservation

class AddReservationDialogFragment(
    private val onReservationAdded: (Reservation) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_reservation, null)
        val datePicker = view.findViewById<DatePicker>(R.id.date_picker)
        val courtPicker = view.findViewById<EditText>(R.id.court_picker)

        builder.setView(view)
            .setTitle("Add Reservation")
            .setPositiveButton("Add") { _, _ ->
                val selectedDate = "${datePicker.dayOfMonth}-${datePicker.month + 1}-${datePicker.year}"
                val courts = courtPicker.text.split(",").map { it.trim().toIntOrNull() ?: 0 }
                onReservationAdded(Reservation(selectedDate, courts))
            }
            .setNegativeButton("Cancel", null)

        return builder.create()
    }
}
