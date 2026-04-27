package com.delivery.tracker.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    fun startOfDay(dateMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endOfDay(dateMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    // Week bounded within calendar month
    fun startOfWeekInMonth(dateMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val month = cal.get(Calendar.MONTH)
        // Go back to Monday
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        // Don't cross into previous month
        if (cal.get(Calendar.MONTH) != month) {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.MONTH, month)
        }
        return startOfDay(cal.timeInMillis)
    }

    fun endOfWeekInMonth(dateMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val month = cal.get(Calendar.MONTH)
        // Go forward to Sunday
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        // Don't cross into next month
        if (cal.get(Calendar.MONTH) != month) {
            cal.set(Calendar.DAY_OF_MONTH,
                cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.MONTH, month)
        }
        return endOfDay(cal.timeInMillis)
    }

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

    fun formatDate(millis: Long): String =
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(millis))

    fun formatDateShort(millis: Long): String =
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(millis))

    fun formatTime(millis: Long): String =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))

    fun formatMonthYear(millis: Long): String =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(millis))

    fun weekLabel(millis: Long): String {
        val start = startOfWeekInMonth(millis)
        val end = endOfWeekInMonth(millis)
        val month = SimpleDateFormat("MMM", Locale.getDefault()).format(Date(millis))
        val startDay = SimpleDateFormat("dd", Locale.getDefault()).format(Date(start))
        val endDay = SimpleDateFormat("dd", Locale.getDefault()).format(Date(end))
        return "$startDay-$endDay $month"
    }
}
