package com.example.tennisfetcher

import com.google.gson.Gson
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun main() {
    val fetcher = MeraFetcher()
    val schedule = fetcher.fetch(LocalDate.now(), 0)
    val courtChecker = CourtAvailabilityChecker(fetcher)

    val dateFormatter = DateTimeFormatter.ofPattern("d-M-yyyy")
    val date = LocalDate.parse("28-3-2025", dateFormatter)
    val startTime = date.atTime(LocalTime.parse("22:00"))
    val endTime = date.atTime(LocalTime.parse("23:00"))
    val dateRange = DateRange(startTime, endTime)

    var intervals = courtChecker.check(
        CourtRequest(
            dateRange=dateRange,
            court = Court(5),
            nonOverlapping = true
        )
    )

    println(intervals)
}