package com.delivery.tracker.viewmodel

import androidx.lifecycle.*
import com.delivery.tracker.data.model.DailySession
import com.delivery.tracker.data.model.Trip
import com.delivery.tracker.data.repository.*
import com.delivery.tracker.ocr.OcrResult
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

    /** Emits true after a retroactive trip is added so the UI can show a toast. */
    private val _tripAdded = MutableLiveData<Int>()   // count of trips just added
    val tripAdded: LiveData<Int> = _tripAdded

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
        val mode   = _viewMode.value ?: HistoryViewMode.DAY

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

            val totalOrderPay  = trips.sumOf { it.orderPay }
            val totalExtras    = trips.sumOf { it.totalExtras }
            val totalTips      = trips.sumOf { it.tips }
            val totalSurge     = trips.sumOf { it.surgePay }
            val totalIncentive = trips.sumOf { it.incentivePay }
            val totalScreenDist = trips.sumOf { it.screenshotDistance }

            val fuelSpent = expenseRepo.getTotalFuel(start, end)
            val tdsSpent  = expenseRepo.getTotalTds(start, end)
            val netRemaining = totalOrderPay + totalExtras - fuelSpent - tdsSpent

            _summary.value = HistorySummary(
                totalTrips              = trips.size,
                totalOrderPay           = totalOrderPay,
                totalExtras             = totalExtras,
                totalTips               = totalTips,
                totalSurge              = totalSurge,
                totalIncentive          = totalIncentive,
                totalScreenshotDistance = totalScreenDist,
                totalActualDistance     = totalActualDist,
                ratePerKmScreenshot     = if (totalScreenDist > 0) totalOrderPay / totalScreenDist else 0.0,
                ratePerKmActual         = if (totalActualDist > 0) totalOrderPay / totalActualDist else 0.0,
                totalFuelSpent          = fuelSpent,
                totalTds                = tdsSpent,
                netRemaining            = netRemaining,
                periodLabel             = label
            )
        }
    }

    /**
     * Add one or more trips retroactively to the currently viewed day.
     *
     * Looks up the existing DailySession for that day. If none exists
     * (e.g. the user never formally "started" that day in the app),
     * a minimal ended session is created automatically so the trips
     * have a valid sessionId to link to.
     */
    fun addTripsFromOcrList(results: List<OcrResult>) {
        val dayMillis = _selectedDateMillis.value ?: System.currentTimeMillis()
        viewModelScope.launch {
            val session = getOrCreateSessionForDay(dayMillis)
            results.forEach { ocr ->
                tripRepo.addTrip(
                    Trip(
                        sessionId          = session.id,
                        restaurantName     = ocr.restaurantName,
                        assignedTime       = ocr.assignedTime,
                        orderPay           = ocr.orderPay,
                        screenshotDistance = ocr.distance,
                        extraPays          = ocr.extraPays,
                        dateMillis         = DateUtils.startOfDay(dayMillis),
                        servicecycleId     = session.serviceCycleId
                    )
                )
            }
            _tripAdded.value = results.size
            loadData()   // refresh the list + summary
        }
    }

    fun addTripManual(
        restaurantName: String,
        assignedTime: String,
        orderPay: Double,
        distance: Double,
        extraPays: Map<String, Double>
    ) {
        val dayMillis = _selectedDateMillis.value ?: System.currentTimeMillis()
        viewModelScope.launch {
            val session = getOrCreateSessionForDay(dayMillis)
            tripRepo.addTrip(
                Trip(
                    sessionId          = session.id,
                    restaurantName     = restaurantName,
                    assignedTime       = assignedTime,
                    orderPay           = orderPay,
                    screenshotDistance = distance,
                    extraPays          = extraPays,
                    dateMillis         = DateUtils.startOfDay(dayMillis),
                    servicecycleId     = session.serviceCycleId
                )
            )
            _tripAdded.value = 1
            loadData()
        }
    }

    fun deleteTrip(trip: Trip) {
        viewModelScope.launch {
            tripRepo.deleteTrip(trip)
            loadData()
        }
    }

    /**
     * Returns the DailySession for the given day, creating a minimal
     * ended session if none exists.
     */
    private suspend fun getOrCreateSessionForDay(dayMillis: Long): DailySession {
        val start = DateUtils.startOfDay(dayMillis)
        val end   = DateUtils.endOfDay(dayMillis)
        val existing = sessionRepo.getSessionForDate(start, end)
        if (existing != null) return existing

        // No session found — create a placeholder ended session
        val id = sessionRepo.startSession(
            DailySession(
                dateMillis    = start,
                startOdometer = 0.0,
                endOdometer   = 0.0,
                isEnded       = true
            )
        )
        return sessionRepo.getSessionForDate(start, end)
            ?: DailySession(id = id, dateMillis = start, startOdometer = 0.0, isEnded = true)
    }
}
