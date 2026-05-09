package com.delivery.tracker.viewmodel

import androidx.lifecycle.*
import com.delivery.tracker.data.model.DailySession
import com.delivery.tracker.data.model.Trip
import com.delivery.tracker.data.repository.*
import com.delivery.tracker.ocr.OcrResult
import com.delivery.tracker.utils.DateUtils
import com.delivery.tracker.utils.SingleLiveEvent
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
    val fuelAllocated: Double = 0.0,
    val serviceAllocated: Double = 0.0,
    val fuelActualSpent: Double = 0.0,
    val serviceActualSpent: Double = 0.0,
    val totalTds: Double = 0.0,
    val netRemaining: Double = 0.0,
    val periodLabel: String = ""
) {
    companion object {
        const val FUEL_RATE_PER_KM    = 1.5
        const val SERVICE_RATE_PER_KM = 0.7
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val tripRepo: TripRepository,
    private val sessionRepo: SessionRepository,
    private val expenseRepo: ExpenseRepository,
    private val cycleRepo: CycleRepository
) : ViewModel() {

    private val _viewMode = MutableLiveData(HistoryViewMode.DAY)
    val viewMode: LiveData<HistoryViewMode> = _viewMode

    private val _selectedDateMillis = MutableLiveData(System.currentTimeMillis())
    val selectedDateMillis: LiveData<Long> = _selectedDateMillis

    private val _trips = MutableLiveData<List<Trip>>()
    val trips: LiveData<List<Trip>> = _trips

    private val _summary = MutableLiveData<HistorySummary>()
    val summary: LiveData<HistorySummary> = _summary

    // Issue 6: Use SingleLiveEvent so the toast only fires once, not on every re-observe
    private val _tripAdded = SingleLiveEvent<Int>()
    val tripAdded: LiveData<Int> = _tripAdded

    private val _daySession = MutableLiveData<DailySession?>()
    val daySession: LiveData<DailySession?> = _daySession

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

            if (mode == HistoryViewMode.DAY) {
                _daySession.value = sessionRepo.getSessionForDate(start, end)
            } else {
                _daySession.value = null
            }

            val sessions = sessionRepo.getSessionsForRangeOnce(start, end)
            val totalActualDist = sessions.sumOf { it.actualDistance }

            // Issue 7: Exclude incentive-only trips (restaurantName starts with "🎁")
            // from trip count and earnings, but keep their extras in totalExtras
            val deliveryTrips = trips.filter { !it.restaurantName.startsWith("🎁") }

            val totalOrderPay  = trips.sumOf { it.orderPay }
            val totalExtras    = trips.sumOf { it.totalExtras }
            val totalTips      = trips.sumOf { it.tips }
            val totalSurge     = trips.sumOf { it.surgePay }
            val totalIncentive = trips.sumOf { it.incentivePay }
            val totalScreenDist = deliveryTrips.sumOf { it.screenshotDistance }

            val tdsSpent = if (mode == HistoryViewMode.DAY) 0.0
                           else expenseRepo.getTotalTds(start, end)

            // Issue 3: Week/Month fuel & service calculated from odometer readings
            // Use the first session's startOdo of the period and last session's endOdo
            val fuelAllocated: Double
            val serviceAllocated: Double

            when (mode) {
                HistoryViewMode.DAY -> {
                    // Day: use actual distance from session odometer as before
                    fuelAllocated    = totalActualDist * HistorySummary.FUEL_RATE_PER_KM
                    serviceAllocated = totalActualDist * HistorySummary.SERVICE_RATE_PER_KM
                }
                HistoryViewMode.WEEK, HistoryViewMode.MONTH -> {
                    // Week/Month: use (endOdo of last session) - (startOdo of first session)
                    // This captures all riding (personal + delivery) across the period
                    val periodKm = calcPeriodOdometerKm(sessions)
                    fuelAllocated    = periodKm * HistorySummary.FUEL_RATE_PER_KM
                    serviceAllocated = periodKm * HistorySummary.SERVICE_RATE_PER_KM
                }
            }

            val fuelActualSpent    = expenseRepo.getTotalFuel(start, end)
            val serviceActualSpent = expenseRepo.getTotalService(start, end)
            val netRemaining       = totalOrderPay + totalExtras - fuelAllocated - serviceAllocated

            _summary.value = HistorySummary(
                totalTrips              = deliveryTrips.size,
                totalOrderPay           = totalOrderPay,
                totalExtras             = totalExtras,
                totalTips               = totalTips,
                totalSurge              = totalSurge,
                totalIncentive          = totalIncentive,
                totalScreenshotDistance = totalScreenDist,
                totalActualDistance     = totalActualDist,
                ratePerKmScreenshot     = if (totalScreenDist > 0) totalOrderPay / totalScreenDist else 0.0,
                ratePerKmActual         = if (totalActualDist > 0) totalOrderPay / totalActualDist else 0.0,
                fuelAllocated           = fuelAllocated,
                serviceAllocated        = serviceAllocated,
                totalTds                = tdsSpent,
                netRemaining            = netRemaining,
                periodLabel             = label,
                fuelActualSpent         = fuelActualSpent,
                serviceActualSpent      = serviceActualSpent
            )
        }
    }

    /**
     * Issue 3: Calculate km ridden in a period from session odometer readings.
     * Uses: last session's endOdometer - first session's startOdometer.
     * Falls back to sum of individual session distances if readings are incomplete.
     */
    private fun calcPeriodOdometerKm(sessions: List<DailySession>): Double {
        if (sessions.isEmpty()) return 0.0
        val realSessions = sessions.filter { !it.isRetroactive && it.isEnded }
        if (realSessions.isEmpty()) return sessions.sumOf { it.actualDistance }

        val sortedByDate = realSessions.sortedBy { it.dateMillis }
        val startOdo = sortedByDate.first().startOdometer
        val endOdo   = sortedByDate.last().let {
            if (it.endOdometer > 0) it.endOdometer else it.startOdometer
        }
        return if (endOdo > startOdo) endOdo - startOdo
               else realSessions.sumOf { it.actualDistance }
    }

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
            loadData()
        }
    }

    fun addTripManual(
        restaurantName: String,
        assignedTime: String,
        orderPay: Double,
        distance: Double,
        extraPays: Map<String, Double>,
        isIncentive: Boolean = false
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

    fun updateTrip(trip: Trip) {
        viewModelScope.launch {
            tripRepo.updateTrip(trip)
            loadData()
        }
    }

    fun deleteTrip(trip: Trip) {
        viewModelScope.launch {
            tripRepo.deleteTrip(trip)
            loadData()
        }
    }

    fun setDayOdometer(startOdo: Double, endOdo: Double) {
        viewModelScope.launch {
            val dayMillis = _selectedDateMillis.value
                ?: DateUtils.startOfDay(System.currentTimeMillis())
            val existing = _daySession.value
            if (existing != null) {
                sessionRepo.updateSession(
                    existing.copy(
                        startOdometer = startOdo,
                        endOdometer   = endOdo,
                        isEnded       = endOdo > 0
                    )
                )
            } else {
                val start = DateUtils.startOfDay(dayMillis)
                val cycle = cycleRepo.getActiveCycleOnce()
                sessionRepo.startSession(
                    DailySession(
                        dateMillis     = start,
                        startOdometer  = startOdo,
                        endOdometer    = endOdo,
                        isEnded        = endOdo > 0,
                        isRetroactive  = true,
                        serviceCycleId = cycle?.id ?: 0L
                    )
                )
                _daySession.value = sessionRepo.getSessionForDate(start, DateUtils.endOfDay(dayMillis))
            }
            loadData()
        }
    }

    fun deleteDayEntries() {
        viewModelScope.launch {
            val session = _daySession.value ?: return@launch
            sessionRepo.deleteTripsForSession(session.id)
            sessionRepo.deleteSession(session)
            _daySession.value = null
            loadData()
        }
    }

    fun getDayAppDistance(): Double {
        return trips.value?.sumOf { it.screenshotDistance } ?: 0.0
    }

    fun updateSessionOdometer(session: DailySession, newStart: Double, newEnd: Double) {
        viewModelScope.launch {
            sessionRepo.updateSession(
                session.copy(
                    startOdometer = newStart,
                    endOdometer = newEnd
                )
            )
            loadData()
        }
    }

    private suspend fun getOrCreateSessionForDay(dayMillis: Long): DailySession {
        val start = DateUtils.startOfDay(dayMillis)
        val end   = DateUtils.endOfDay(dayMillis)
        val existing = sessionRepo.getSessionForDate(start, end)
        if (existing != null) return existing

        val id = sessionRepo.startSession(
            DailySession(
                dateMillis    = start,
                startOdometer = 0.0,
                endOdometer   = 0.0,
                isEnded       = true,
                isRetroactive = true
            )
        )
        return sessionRepo.getSessionForDate(start, end)
            ?: DailySession(id = id, dateMillis = start, startOdometer = 0.0,
                            isEnded = true, isRetroactive = true)
    }
}
