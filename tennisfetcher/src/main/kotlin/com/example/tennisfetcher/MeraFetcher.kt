package com.example.tennisfetcher

import mu.KotlinLogging
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

// Models

data class DateRange(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val step: Duration = Duration.ofMinutes(30)
) : Iterable<LocalDateTime>, Comparable<DateRange> {
    override fun iterator(): Iterator<LocalDateTime> = generateSequence(start) { it.plus(step).takeIf { next -> next <= end } }.iterator()
    override fun compareTo(other: DateRange) = start.compareTo(other.start)
}

data class CourtAvailability(val courtId: Int, val availability: List<DateRange>)

data class Court(val id: Int) {
    val page: Int get() = (id - 1) / 6

    fun isAvailable(schedule: CourtSchedule, timeSlot: LocalDateTime): Boolean =
        schedule.getIntervals(id).any { interval -> !timeSlot.isBefore(interval.start) && timeSlot.isBefore(interval.end) }
}

data class CourtSchedule(val date: LocalDate, val courtAvailabilities: List<CourtAvailability>) {
    fun getIntervals(courtId: Int): List<DateRange> = courtAvailabilities
        .filter { it.courtId == courtId }
        .flatMap { it.availability }
        .sorted()
        .let(::mergeIntervals)

    private fun mergeIntervals(intervals: List<DateRange>): List<DateRange> {
        if (intervals.isEmpty()) return emptyList()
        val merged = mutableListOf<DateRange>()
        var (currentStart, currentEnd) = intervals.first().start to intervals.first().end

        for (range in intervals.drop(1)) {
            if (!range.start.isAfter(currentEnd)) {
                currentEnd = maxOf(currentEnd, range.end)
            } else {
                merged += DateRange(currentStart, currentEnd)
                currentStart = range.start
                currentEnd = range.end
            }
        }
        merged += DateRange(currentStart, currentEnd)
        return merged
    }
}

data class CourtRequest(
    val dateRange: DateRange,
    val court: Court,
    val requiredInterval: Duration = Duration.ofHours(1),
    val nonOverlapping: Boolean = false
)

// Checker logic

class CourtAvailabilityChecker(private val fetcher: Fetcher) {
    fun check(request: CourtRequest): List<DateRange> {
        val availability = getAvailability(request)
        var intervals = findIntervals(availability, request)
        if (request.nonOverlapping) intervals = selectNonOverlapping(intervals)
        return intervals
    }

    private fun getAvailability(request: CourtRequest): Map<LocalDateTime, Boolean> {
        val avail = mutableMapOf<LocalDateTime, Boolean>()
        val page = request.court.page

        println(request.dateRange)
        request.dateRange.forEach { dt ->
            val schedule = fetcher.fetch(dt.toLocalDate(), page)
            avail[dt] = request.court.isAvailable(schedule, dt)
        }
        avail.remove(request.dateRange.end)

        return avail
    }

    private fun findIntervals(availability: Map<LocalDateTime, Boolean>, request: CourtRequest): List<DateRange> {
        val times = availability.keys.sorted()
        val step = request.dateRange.step
        val requiredSteps = (request.requiredInterval.toMinutes() / step.toMinutes()).toInt()

        return times.windowed(requiredSteps, 1, partialWindows = false)
            .filter { window -> window.zipWithNext().all { it.second.minusMinutes(step.toMinutes()) == it.first } }
            .filter { window -> window.all { availability[it] == true } }
            .map { DateRange(it.first(), it.last().plus(step)) }
    }

    private fun selectNonOverlapping(intervals: List<DateRange>): List<DateRange> {
        val sortedIntervals = intervals.sorted()
        val selected = mutableListOf<DateRange>()
        var lastEnd: LocalDateTime? = null
        sortedIntervals.forEach { interval ->
            if (lastEnd == null || !interval.start.isBefore(lastEnd)) {
                selected += interval
                lastEnd = interval.end
            }
        }
        return selected
    }
}

// Abstract Fetcher (replace with Retrofit/OkHttp logic)

interface Fetcher {
    fun fetch(date: LocalDate, page: Int): CourtSchedule
}

// Mera Fetcher

class MeraFetcher() : Fetcher {
    private val client = OkHttpClient()
    private val cache = mutableMapOf<Pair<LocalDate, Int>, CourtSchedule>()
    private val BASE_URL = "https://kluby.org/klub/wkt-mera/dedykowane/grafik?data_grafiku=%s&dyscyplina=1&strona=%d"
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun fetch(date: LocalDate, page: Int): CourtSchedule {
        println("Call to fetch")
        println(date)
        println(page)
        val key = date to page
        cache[key]?.let { return it }
        // Build & execute the HTTP request
        val url = BASE_URL.format(date.format(dateFormatter), page)
        print(date.format(dateFormatter))
        println(url)
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Network request failed")

        // Parse the HTML
        val document = Jsoup.parse(response.body?.string() ?: "")
        val tableRows = document.select("#grafik tbody tr")
        logger.debug { "Fetched ${tableRows.size} table rows" }

        // 1) Collect times from the first <td> of each row if possible
        //    We'll store them in rowTimes in the same order as we process tableRows.
        val rowTimes = mutableListOf<LocalDateTime>()

        // 2) We'll build a 2D 'availability' grid with rowspans accounted for.
        //    each row => a Boolean[6], for 6 courts on that page
        val numCourts = 6
        data class PendingCell(var available: Boolean, var rowsLeft: Int)
        val pending = Array<PendingCell?>(numCourts) { null }  // track rowspans

        val grid = mutableListOf<List<Boolean>>()

        tableRows.forEach { rowElement ->
            // Try parsing the time from the FIRST td
            val timeCell = rowElement.selectFirst("td")
            val parsedTime = timeCell?.text()?.trim()?.let { parseTime(it, date) }

            // If we successfully parsed, we store it in rowTimes
            // (If it fails, we skip adding a time, but still parse the row for the grid.)
            if (parsedTime != null) rowTimes.add(parsedTime)

            // Prepare array for the 6 courts in this row
            val rowData = Array<Boolean?>(numCourts) { null }

            // These are the td cells AFTER the first one (the time cell).
            val cells = rowElement.select("td:not(:first-child)")
            var cellIndex = 0
            var col = 0

            // Fill rowData, either from pending rowspans or from new cells
            while (col < numCourts) {
                if (pending[col] != null) {
                    // We still have an ongoing rowspan for this column
                    rowData[col] = pending[col]!!.available
                    pending[col]!!.rowsLeft -= 1
                    if (pending[col]!!.rowsLeft == 0) {
                        pending[col] = null
                    }
                    col += 1
                } else {
                    // If we have more cells left in this row, read the next cell
                    if (cellIndex < cells.size) {
                        val cell = cells[cellIndex]
                        cellIndex += 1

                        // Determine if it's available
                        val cellClass = cell.className()
                        val available = !cellClass.contains("active") && !cellClass.contains("danger")

                        // Put in rowData
                        rowData[col] = available

                        // Check rowspan
                        val rowspanAttr = cell.attr("rowspan")
                        if (rowspanAttr.isNotEmpty()) {
                            val span = rowspanAttr.toIntOrNull() ?: 1
                            if (span > 1) {
                                pending[col] = PendingCell(available, span - 1)
                            }
                        }
                        col += 1
                    } else {
                        // No more <td> cells; default to "available" or false – up to you.
                        rowData[col] = true
                        col += 1
                    }
                }
            }
            // Convert to non-null Booleans (default to true if missing)
            grid.add(rowData.map { it ?: true })
        }

        // 3) Print out the ASCII grid for debugging
        logAvailabilityGrid(date, page, rowTimes, grid)

        // 4) Figure out slot duration from the first two times if possible
        val slotDurationMinutes = if (rowTimes.size >= 2) {
            Duration.between(rowTimes[0], rowTimes[1]).toMinutes()
        } else {
            30L
        }

        // 5) Map page => court IDs
        val courtIds = (1 + page * 6..6 + page * 6).toList()

        // 6) Build availability intervals for each court
        val courtAvailabilities = courtIds.mapIndexed { courtIdx, courtId ->
            val intervals = mutableListOf<DateRange>()
            var currentStart: LocalDateTime? = null
            grid.forEachIndexed { rowIdx, row ->
                // If rowIdx >= rowTimes.size => no time
                if (rowIdx >= rowTimes.size) {
                    return@forEachIndexed
                }
                val currentTime = rowTimes[rowIdx]
                val isAvailable = row[courtIdx]
                if (isAvailable) {
                    if (currentStart == null) currentStart = currentTime
                } else {
                    if (currentStart != null) {
                        intervals += DateRange(currentStart!!, currentTime)
                        currentStart = null
                    }
                }
            }
            currentStart?.let { start ->
                intervals += DateRange(start, rowTimes.last().plusMinutes(slotDurationMinutes))
            }
            CourtAvailability(courtId, intervals)
        }

        val schedule = CourtSchedule(date, courtAvailabilities)
        cache[key] = schedule
        return schedule
    }

    private fun logAvailabilityGrid(
        date: LocalDate,
        page: Int,
        rowTimes: List<LocalDateTime>,
        grid: List<List<Boolean>>
    ) {
        val sb = StringBuilder("Availability for date=$date, page=$page:\n")

        rowTimes.forEachIndexed { rowIndex, time ->
            // Show the time (HH:mm) to the left, then each court's availability as '.' or '*'
            val hhmm = time.toLocalTime().toString().padStart(5, '0')
            sb.append("$hhmm ")
            grid[rowIndex].forEach { isAvailable ->
                sb.append(if (isAvailable) "." else "*")
            }
            sb.append("\n")
        }

        // Now log the entire availability map as an ASCII rectangle
        // '.' = available, '*' = unavailable
        println(sb)
    }


    private fun parseTime(time: String, date: LocalDate): LocalDateTime? =
        runCatching {
            val (h, m) = time.split(":").map { it.toInt() }
            date.atTime(h, m)
        }.getOrNull()
}

// Mock Fetcher Example

class MockedFetcher : Fetcher {
    override fun fetch(date: LocalDate, page: Int): CourtSchedule {
        val startOfDay = date.atStartOfDay()
        val courts = when (page) {
            0 -> (1..6)
            1 -> (7..12)
            else -> emptyList()
        }
        val courtAvailabilities = courts.map { court ->
            CourtAvailability(
                court,
                (court % 3 until 24 step 3).map { hour ->
                    DateRange(startOfDay.plusHours(hour.toLong()), startOfDay.plusHours((hour + 1).toLong()).plusMinutes(30))
                }
            )
        }
        return CourtSchedule(date, courtAvailabilities)
    }
}