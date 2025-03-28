package com.example.tennisalert.model

data class Reservation(
    val date: String,
    val startTime: String,
    val endTime: String,
    val courts: List<Int>
)