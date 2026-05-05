package com.delivery.tracker.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    fun startOfDay(dateMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endOfDay(dateMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59);      set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    /** ISO Monday of the week — never clamped to month boundary. */
    fun startOfWeek(dateMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
            cal.add(Calendar.DAY_OF_MONTH, -1)
        return startOfDay(cal.timeInMillis)
    }

    /** ISO Sunday of the week — never clamped to month boundary. */
    fun endOfWeek(dateMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        return endOfDay(cal.timeInMillis)
    }

    // Keep old names as aliases so all existing call-sites still compile
    fun startOfWeekInMonth(dateMillis: Long): Long = startOfWeek(dateMillis)
    fun endOfWeekInMonth(dateMillis: Long): Long   = endOfWeek(dateMillis)

    fun startOfMonth(dateMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return startOfDay(cal.timeInMillis)
    }

    fun endOfMonth(dateMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        return endOfDay(cal.timeInMillis)
    }

    /**
     * All ISO Mon–Sun weeks that overlap the given month.
     * A week spanning two months appears in both months' lists.
     */
    fun weeksOverlappingMonth(year: Int, month: Int): List<Pair<String, Long>> {
        val monthStart = Calendar.getInstance().apply {
            set(year, month, 1, 12, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val monthEnd = endOfMonth(monthStart)
        val weeks    = mutableListOf<Pair<String, Long>>()
        var cursor   = startOfWeek(monthStart)

        while (cursor <= monthEnd) {
            val weekEnd = endOfWeek(cursor)
            if (weeks.none { it.second == cursor })
                weeks.add(Pair(weekRangeLabel(cursor, weekEnd), cursor))
            cursor += 7 * 24 * 60 * 60 * 1000L
        }
        return weeks
    }

    /** "7–13 Apr" for same-month, "28 Apr – 4 May" for cross-month. */
    fun weekRangeLabel(weekStartMillis: Long, weekEndMillis: Long): String {
        val fmtD  = SimpleDateFormat("d",       Locale.getDefault())
        val fmtDM = SimpleDateFormat("d MMM",   Locale.getDefault())
        val sc    = Calendar.getInstance().apply { timeInMillis = weekStartMillis }
        val ec    = Calendar.getInstance().apply { timeInMillis = weekEndMillis }
        return if (sc.get(Calendar.MONTH) == ec.get(Calendar.MONTH))
            "${fmtD.format(Date(weekStartMillis))}–${fmtDM.format(Date(weekEndMillis))}"
        else
            "${fmtDM.format(Date(weekStartMillis))} – ${fmtDM.format(Date(weekEndMillis))}"
    }

    fun formatDate(millis: Long): String =
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(millis))

    fun formatDateShort(millis: Long): String =
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(millis))

    fun formatTime(millis: Long): String =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))

    fun formatMonthYear(millis: Long): String =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(millis))

    fun weekLabel(millis: Long): String =
        weekRangeLabel(startOfWeek(millis), endOfWeek(millis))
}