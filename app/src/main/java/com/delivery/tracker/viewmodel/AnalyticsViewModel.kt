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

data class HourStat(
    val hour: Int,         // 0-23
    val label: String,     // "12 PM"
    val orderCount: Int
)

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

    init { loadAnalytics() }

    fun loadAnalytics() {
        tripRepo.getAllTrips().observeForever { trips ->
            processAnalytics(trips)
        }
    }

    private fun processAnalytics(trips: List<Trip>) {
        viewModelScope.launch {
            // Restaurant stats
            val byRestaurant = trips.groupBy { it.restaurantName.trim() }
            val restaurantStats = byRestaurant.map { (name, tripList) ->
                val hourGroups = tripList.groupBy { parseHour(it.assignedTime) }
                val bestHour = hourGroups.maxByOrNull { it.value.size }?.key ?: 0
                RestaurantStat(
                    name = name,
                    orderCount = tripList.size,
                    avgOrderPay = tripList.sumOf { it.orderPay } / tripList.size,
                    avgDistance = tripList.sumOf { it.screenshotDistance } / tripList.size,
                    totalEarnings = tripList.sumOf { it.totalEarnings },
                    bestHour = formatHour(bestHour)
                )
            }.sortedByDescending { it.orderCount }

            // Hourly stats
            val byHour = trips.groupBy { parseHour(it.assignedTime) }
            val hourStats = (0..23).map { hour ->
                HourStat(
                    hour = hour,
                    label = formatHour(hour),
                    orderCount = byHour[hour]?.size ?: 0
                )
            }

            // Peak hour
            val peakHour = hourStats.maxByOrNull { it.orderCount }

            // Days worked
            val uniqueDays = trips.map {
                it.dateMillis / (1000 * 60 * 60 * 24)
            }.toSet().size

            _analyticsSummary.value = AnalyticsSummary(
                restaurantStats = restaurantStats,
                hourStats = hourStats,
                totalTripsAnalyzed = trips.size,
                topRestaurant = restaurantStats.firstOrNull()?.name ?: "",
                peakHour = peakHour?.label ?: "",
                avgOrdersPerDay = if (uniqueDays > 0)
                    trips.size.toDouble() / uniqueDays else 0.0
            )
        }
    }

    private fun parseHour(timeStr: String): Int {
        return try {
            val clean = timeStr.trim().uppercase()
            val isPm = clean.contains("PM")
            val isAm = clean.contains("AM")
            val timePart = clean.replace("AM", "").replace("PM", "").trim()
            val hour = timePart.split(":").firstOrNull()?.trim()?.toIntOrNull() ?: 0
            when {
                isPm && hour != 12 -> hour + 12
                isAm && hour == 12 -> 0
                else -> hour
            }
        } catch (e: Exception) { 0 }
    }

    private fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> "12 AM"
            hour < 12 -> "$hour AM"
            hour == 12 -> "12 PM"
            else -> "${hour - 12} PM"
        }
    }
}
