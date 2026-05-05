package com.delivery.tracker.viewmodel

import androidx.lifecycle.*
import com.delivery.tracker.data.model.Trip
import com.delivery.tracker.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RestaurantStat(
    val name: String,
    val orderCount: Int,
    val avgOrderPay: Double,
    val avgDistance: Double,
    val totalEarnings: Double,
    val bestHour: String
)

data class HourStat(val hour: Int, val label: String, val orderCount: Int)

data class AnalyticsSummary(
    val restaurantStats: List<RestaurantStat> = emptyList(),
    val hourStats: List<HourStat> = emptyList(),
    val totalTripsAnalyzed: Int = 0,
    val topRestaurant: String = "",
    val peakHour: String = "",
    val avgOrdersPerDay: Double = 0.0
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val tripRepo: TripRepository
) : ViewModel() {

    private val _analyticsSummary = MutableLiveData<AnalyticsSummary>()
    val analyticsSummary: LiveData<AnalyticsSummary> = _analyticsSummary

    private val allTripsSource = tripRepo.getAllTrips()
    private val tripsObserver  = Observer<List<Trip>> { processAnalytics(it) }

    init {
        allTripsSource.observeForever(tripsObserver)
    }

    override fun onCleared() {
        super.onCleared()
        allTripsSource.removeObserver(tripsObserver)  // FIX: prevent leak
    }

    /** Filter to a date range; pass 0 / Long.MAX_VALUE for all-time. */
    fun setDateRange(start: Long, end: Long) {
        viewModelScope.launch {
            processAnalytics(tripRepo.getTripsForRange(start, end))
        }
    }

    private fun processAnalytics(trips: List<Trip>) {
        viewModelScope.launch {
            val byRestaurant = trips.groupBy { it.restaurantName.trim() }
            val restaurantStats = byRestaurant.map { (name, list) ->
                val bestHour = list.groupBy { parseHour(it.assignedTime) }
                    .maxByOrNull { it.value.size }?.key ?: 0
                RestaurantStat(
                    name          = name,
                    orderCount    = list.size,
                    avgOrderPay   = list.sumOf { it.orderPay } / list.size,
                    avgDistance   = list.sumOf { it.screenshotDistance } / list.size,
                    totalEarnings = list.sumOf { it.totalEarnings },
                    bestHour      = formatHour(bestHour)
                )
            }.sortedByDescending { it.orderCount }

            val byHour   = trips.groupBy { parseHour(it.assignedTime) }
            val hourStats = (0..23).map { h ->
                HourStat(h, formatHour(h), byHour[h]?.size ?: 0)
            }
            val peakHour   = hourStats.maxByOrNull { it.orderCount }
            val uniqueDays = trips.map { it.dateMillis / 86_400_000L }.toSet().size

            _analyticsSummary.value = AnalyticsSummary(
                restaurantStats    = restaurantStats,
                hourStats          = hourStats,
                totalTripsAnalyzed = trips.size,
                topRestaurant      = restaurantStats.firstOrNull()?.name ?: "",
                peakHour           = peakHour?.label ?: "",
                avgOrdersPerDay    = if (uniqueDays > 0) trips.size.toDouble() / uniqueDays else 0.0
            )
        }
    }

    private fun parseHour(timeStr: String): Int = try {
        val clean = timeStr.trim().uppercase()
        val isPm  = clean.contains("PM")
        val isAm  = clean.contains("AM")
        val h     = clean.replace("AM","").replace("PM","").trim()
            .split(":").firstOrNull()?.trim()?.toIntOrNull() ?: 0
        when { isPm && h != 12 -> h + 12; isAm && h == 12 -> 0; else -> h }
    } catch (e: Exception) { 0 }

    private fun formatHour(h: Int) = when {
        h == 0  -> "12 AM"; h < 12 -> "$h AM"
        h == 12 -> "12 PM"; else   -> "${h - 12} PM"
    }
}