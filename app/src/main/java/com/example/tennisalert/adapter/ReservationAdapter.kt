package com.example.tennisalert.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tennisalert.R
import com.example.tennisalert.model.Reservation

class ReservationAdapter(
    private val reservations: List<Reservation>,
    private val onRemoveClicked: (Int) -> Unit
) : RecyclerView.Adapter<ReservationAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.text_date)
        val courtsText: TextView = view.findViewById(R.id.text_courts)
        val removeButton: Button = view.findViewById(R.id.button_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reservation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reservation = reservations[position]
        holder.dateText.text = reservation.date
        holder.courtsText.text = "Courts: ${reservation.courts.joinToString(", ")}"
        holder.removeButton.setOnClickListener { onRemoveClicked(position) }
    }

    override fun getItemCount() = reservations.size
}
