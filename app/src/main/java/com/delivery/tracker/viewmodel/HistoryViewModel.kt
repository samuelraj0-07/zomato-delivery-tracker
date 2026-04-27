package com.delivery.tracker.viewmodel

import androidx.lifecycle.*
import com.delivery.tracker.data.model.Trip
import com.delivery.tracker.data.repository.*
import com.delivery.tracker.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryViewMode { DAY, WEEK, MONTH }

data class HistorySummary(
    val totalTrips: Int = 0,
    val totalOrderPay: Double = 0.0,
    val totalExtras: Double = 0.0,
    val totalTips: Double = 0.0,
    val totalSurge: Double = 0.0,
    val totalIncentive: Double = 0.0,
    val totalScreenshotDistance: Double = 0.0,
    val totalActualDistance: Double = 0.0,
    val ratePerKmScreenshot: Double = 0.0,
    val ratePerKmActual: Double = 0.0,
    val totalFuelSpent: Double = 0.0,
    val totalServiceSpent: Double = 0.0,
    val totalTds: Double = 0.0,
    val netRemaining: Double = 0.0,
    val periodLabel: String = ""
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val tripRepo: TripRepository,
    private val sessionRepo: SessionRepository,
    private val expenseRepo: ExpenseRepository
) : ViewModel() {

    private val _viewMode = MutableLiveData(HistoryViewMode.DAY)
    val viewMode: LiveData<HistoryViewMode> = _viewMode

    private val _selectedDateMillis = MutableLiveData(System.currentTimeMillis())
    val selectedDateMillis: LiveData<Long> = _selectedDateMillis

    private val _trips = MutableLiveData<List<Trip>>()
    val trips: LiveData<List<Trip>> = _trips

    private val _summary = MutableLiveData<HistorySummary>()
    val summary: LiveData<HistorySummary> = _summary

    init { loadData() }

    fun setViewMode(mode: HistoryViewMode) {
        _viewMode.value = mode
        loadData()
    }

    fun setSelectedDate(millis: Long) {
        _selectedDateMillis.value = millis
        loadData()
    }

    private fun loadData() {
        val millis = _selectedDateMillis.value ?: System.currentTimeMillis()
        val mode = _viewMode.value ?: HistoryViewMode.DAY

        val (start, end, label) = when (mode) {
            HistoryViewMode.DAY -> Triple(
                DateUtils.startOfDay(millis),
                DateUtils.endOfDay(millis),
                DateUtils.formatDate(millis)
            )
            HistoryViewMode.WEEK -> Triple(
                DateUtils.startOfWeekInMonth(millis),
                DateUtils.endOfWeekInMonth(millis),
                "Week: ${DateUtils.weekLabel(millis)}"
            )
            HistoryViewMode.MONTH -> Triple(
                DateUtils.startOfMonth(millis),
                DateUtils.endOfMonth(millis),
                DateUtils.formatMonthYear(millis)
            )
        }

        viewModelScope.launch {
            val trips = tripRepo.getTripsForRange(start, end)
            _trips.value = trips

            val sessions = sessionRepo.getSessionsByDateRange(start, end)
            val totalActualDist = sessions.value?.sumOf { it.actualDistance } ?: 0.0

            val totalOrderPay = trips.sumOf { it.orderPay }
            val totalExtras = trips.sumOf { it.totalExtras }
            val totalTips = trips.sumOf { it.tips }
            val totalSurge = trips.sumOf { it.surgePay }
            val totalIncentive = trips.sumOf { it.incentivePay }
            val totalScreenDist = trips.sumOf { it.screenshotDistance }

            val fuelSpent = expenseRepo.getTotalFuel(start, end)
            val tdsSpent = expenseRepo.getTotalTds(start, end)

            val netRemaining = totalOrderPay + totalExtras - fuelSpent - tdsSpent

            _summary.value = HistorySummary(
                totalTrips = trips.size,
                totalOrderPay = totalOrderPay,
                totalExtras = totalExtras,
                totalTips = totalTips,
                totalSurge = totalSurge,
                totalIncentive = totalIncentive,
                totalScreenshotDistance = totalScreenDist,
                totalActualDistance = totalActualDist,
                ratePerKmScreenshot = if (totalScreenDist > 0)
                    totalOrderPay / totalScreenDist else 0.0,
                ratePerKmActual = if (totalActualDist > 0)
                    totalOrderPay / totalActualDist else 0.0,
                totalFuelSpent = fuelSpent,
                totalTds = tdsSpent,
                netRemaining = netRemaining,
                periodLabel = label
            )
        }
    }
}
