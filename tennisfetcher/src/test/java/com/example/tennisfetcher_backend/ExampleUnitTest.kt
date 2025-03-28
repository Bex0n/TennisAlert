package com.example.tennisfetcher

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MeraFetcherTest {

    @Test
    fun testMockedFetcherReturnsCorrectCourtSchedule() {
        val fetcher = MockedFetcher()
        val date = LocalDate.now()
        val schedule = fetcher.fetch(date, page = 0)

        val courtIds = schedule.courtAvailabilities.map { it.courtId }
        assertEquals((1..6).toList(), courtIds)

        schedule.courtAvailabilities.forEach { courtAvailability ->
            assertTrue(courtAvailability.availability.isNotEmpty())
        }

        schedule.courtAvailabilities.forEach { courtAvailability ->
            val intervals = courtAvailability.availability
            for (i in 0 until intervals.size - 1) {
                assertTrue(intervals[i].end <= intervals[i + 1].start)
            }
        }
    }
}
