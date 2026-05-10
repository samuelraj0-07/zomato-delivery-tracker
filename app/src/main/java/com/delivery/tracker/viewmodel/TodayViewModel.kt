package com.delivery.tracker.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delivery.tracker.data.db.SubOrderDao
import com.delivery.tracker.data.model.*
import com.delivery.tracker.data.repository.*
import com.delivery.tracker.ocr.OcrResult
import com.delivery.tracker.utils.DateUtils
import com.delivery.tracker.utils.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class TodaySummary(
    val totalTrips: Int = 0,
    val totalOrderPay: Double = 0.0,
    val totalExtras: Double = 0.0,
    val totalScreenshotDistance: Double = 0.0,
    val actualDistance: Double = 0.0,
    val ratePerKmLive: Double = 0.0,
    val ratePerKmActual: Double = 0.0,
    val deadKm: Double = 0.0,
    val isSessionEnded: Boolean = false
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val tripRepo: TripRepository,
    private val sessionRepo: SessionRepository,
    private val cycleRepo: CycleRepository,
    private val subOrderDao: SubOrderDao
) : ViewModel() {

    private val _selectedDateMillis = MutableLiveData(System.currentTimeMillis())
    val selectedDateMillis: LiveData<Long> = _selectedDateMillis

    private val _activeSession = sessionRepo.getActiveSession()
    val activeSession: LiveData<DailySession?> = _activeSession

    private val _todayTrips = MutableLiveData<List<Trip>>()
    val todayTrips: LiveData<List<Trip>> = _todayTrips

    private val _todaySummary = MutableLiveData<TodaySummary>()
    val todaySummary: LiveData<TodaySummary> = _todaySummary

    private val _sessionStarted = SingleLiveEvent<Boolean>()
    val sessionStarted: LiveData<Boolean> = _sessionStarted

    private val _sessionEnded = SingleLiveEvent<Boolean>()
    val sessionEnded: LiveData<Boolean> = _sessionEnded

    private val _odometerError = MutableLiveData<String>()
    val odometerError: LiveData<String> = _odometerError

    /** Set of day-start epoch millis for each day in the current calendar month
     *  that has a recorded session — used to highlight ride days on the calendar. */
    private val _rideDaysInMonth = MutableLiveData<Set<Long>>()
    val rideDaysInMonth: LiveData<Set<Long>> = _rideDaysInMonth

    init {
        _activeSession.observeForever { session ->
            if (session != null) {
                _selectedDateMillis.value = session.dateMillis
                loadTodayTrips(session.id)
            } else {
                tripsSource?.removeObserver(tripsObserver)
                tripsSource = null
                _todayTrips.value = emptyList()
            }
        }
        loadRideDaysForCurrentMonth()
    }

    fun setSelectedDate(dateMillis: Long) {
        if (_activeSession.value == null) {
            _selectedDateMillis.value = dateMillis
        }
    }

    /** Call when user navigates to a different month on the calendar. */
    fun loadRideDaysForMonth(year: Int, month: Int) {
        viewModelScope.launch {
            val cal = Calendar.getInstance().apply { set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            val start = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
            val end = cal.timeInMillis
            val dates = sessionRepo.getSessionDateMillisInRange(start, end)
            _rideDaysInMonth.value = dates.map { DateUtils.startOfDay(it) }.toSet()
        }
    }

    private fun loadRideDaysForCurrentMonth() {
        val cal = Calendar.getInstance()
        loadRideDaysForMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    private var tripsSource: LiveData<List<Trip>>? = null
    private val tripsObserver = Observer<List<Trip>> { trips ->
        _todayTrips.value = trips
        recalculateSummary(trips)
    }

    private fun loadTodayTrips(sessionId: Long) {
        tripsSource?.removeObserver(tripsObserver)
        val liveData = tripRepo.getTripsBySession(sessionId)
        tripsSource  = liveData
        liveData.observeForever(tripsObserver)
    }

    override fun onCleared() {
        super.onCleared()
        tripsSource?.removeObserver(tripsObserver)
    }

    private fun recalculateSummary(trips: List<Trip>) {
        val session = _activeSession.value
        val totalOrderPay   = trips.sumOf { it.orderPay }
        val totalExtras     = trips.sumOf { it.totalExtras }
        val totalScreenDist = trips.sumOf { it.screenshotDistance }
        val actualDist      = session?.actualDistance ?: 0.0

        _todaySummary.value = TodaySummary(
            totalTrips              = trips.size,
            totalOrderPay           = totalOrderPay,
            totalExtras             = totalExtras,
            totalScreenshotDistance = totalScreenDist,
            actualDistance          = actualDist,
            ratePerKmLive           = if (totalScreenDist > 0) totalOrderPay / totalScreenDist else 0.0,
            ratePerKmActual         = if (actualDist > 0) totalOrderPay / actualDist else 0.0,
            deadKm                  = (actualDist - totalScreenDist).coerceAtLeast(0.0),
            isSessionEnded          = session?.isEnded ?: false
        )
    }

    private fun recalculateSummaryWithSession(session: DailySession, trips: List<Trip>) {
        val totalOrderPay   = trips.sumOf { it.orderPay }
        val totalExtras     = trips.sumOf { it.totalExtras }
        val totalScreenDist = trips.sumOf { it.screenshotDistance }
        val actualDist      = session.actualDistance
        _todaySummary.value = TodaySummary(
            totalTrips              = trips.size,
            totalOrderPay           = totalOrderPay,
            totalExtras             = totalExtras,
            totalScreenshotDistance = totalScreenDist,
            actualDistance          = actualDist,
            ratePerKmLive           = if (totalScreenDist > 0) totalOrderPay / totalScreenDist else 0.0,
            ratePerKmActual         = if (actualDist > 0) totalOrderPay / actualDist else 0.0,
            deadKm                  = (actualDist - totalScreenDist).coerceAtLeast(0.0),
            isSessionEnded          = session.isEnded
        )
    }

    fun startDay(startOdometer: Double, dateMillis: Long = _selectedDateMillis.value ?: System.currentTimeMillis()) {
        viewModelScope.launch {
            val existing = sessionRepo.getActiveSessionOnce()
            if (existing != null) { _sessionStarted.value = true; return@launch }

            val dayStart = DateUtils.startOfDay(dateMillis)

            val maxBefore = sessionRepo.getMaxEndOdometerBefore(dayStart) ?: 0.0
            if (maxBefore > 0 && startOdometer < maxBefore) {
                _odometerError.value =
                    "Start odometer (%.1f km) is less than the previous day's reading (%.1f km)."
                        .format(startOdometer, maxBefore)
                return@launch
            }

            val minAfter = sessionRepo.getMinStartOdometerAfter(dayStart) ?: 0.0
            if (minAfter > 0 && startOdometer > minAfter) {
                _odometerError.value =
                    "Start odometer (%.1f km) exceeds the next recorded day's reading (%.1f km)."
                        .format(startOdometer, minAfter)
                return@launch
            }

            val cycle = cycleRepo.getActiveCycleOnce()
            sessionRepo.startSession(
                DailySession(
                    dateMillis     = dayStart,
                    startOdometer  = startOdometer,
                    serviceCycleId = cycle?.id ?: 0L
                )
            )
            _sessionStarted.value = true
            // Refresh calendar so newly started day lights up
            val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
            loadRideDaysForMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        }
    }

    fun endDay(endOdometer: Double) {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSessionOnce() ?: return@launch

            if (endOdometer <= session.startOdometer) {
                _odometerError.value =
                    "End odometer (%.1f km) must be greater than start odometer (%.1f km)."
                        .format(endOdometer, session.startOdometer)
                return@launch
            }

            val minAfter = sessionRepo.getMinStartOdometerAfter(session.dateMillis) ?: 0.0
            if (minAfter > 0 && endOdometer > minAfter) {
                _odometerError.value =
                    "End odometer (%.1f km) exceeds the next recorded day's start (%.1f km)."
                        .format(endOdometer, minAfter)
                return@launch
            }

            sessionRepo.updateSession(session.copy(endOdometer = endOdometer, isEnded = true))
            recalculateSummary(_todayTrips.value ?: emptyList())
            _sessionEnded.value = true
        }
    }

    fun addTrip(trip: Trip) {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSessionOnce() ?: return@launch
            tripRepo.addTrip(
                trip.copy(
                    sessionId      = session.id,
                    dateMillis     = session.dateMillis,
                    servicecycleId = session.serviceCycleId
                )
            )
        }
    }

    fun addTripFromOcr(ocrResult: OcrResult) {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSessionOnce() ?: return@launch
            val tripId = tripRepo.addTrip(
                Trip(
                    sessionId          = session.id,
                    restaurantName     = ocrResult.restaurantName,
                    assignedTime       = ocrResult.assignedTime,
                    orderPay           = ocrResult.orderPay,
                    screenshotDistance = ocrResult.distance,
                    extraPays          = ocrResult.extraPays,
                    dateMillis         = session.dateMillis,
                    servicecycleId     = session.serviceCycleId
                )
            )
            if (ocrResult.subOrders.isNotEmpty()) {
                subOrderDao.insertAll(
                    ocrResult.subOrders.map { s ->
                        SubOrder(
                            tripId             = tripId,
                            orderNumber        = s.orderNumber,
                            restaurantName     = s.restaurantName,
                            dropLocationName   = s.dropLocationName,
                            pickupDistanceKm   = s.pickupDistanceKm,
                            dropDistanceKm     = s.dropDistanceKm,
                            orderAssignedTime  = s.orderAssignedTime,
                            orderPickedTime    = s.orderPickedTime,
                            orderDeliveredTime = s.orderDeliveredTime
                        )
                    }
                )
            }
        }
    }

    fun addTripsFromOcrList(results: List<OcrResult>) {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSessionOnce() ?: return@launch
            results.forEach { ocrResult ->
                tripRepo.addTrip(
                    Trip(
                        sessionId          = session.id,
                        restaurantName     = ocrResult.restaurantName,
                        assignedTime       = ocrResult.assignedTime,
                        orderPay           = ocrResult.orderPay,
                        screenshotDistance = ocrResult.distance,
                        extraPays          = ocrResult.extraPays,
                        dateMillis         = session.dateMillis,
                        servicecycleId     = session.serviceCycleId
                    )
                )
            }
        }
    }

    fun getSubOrdersForTrip(tripId: Long) = subOrderDao.getSubOrdersForTrip(tripId)

    fun deleteTrip(trip: Trip) {
        viewModelScope.launch { tripRepo.deleteTrip(trip) }
    }

    fun updateTrip(trip: Trip) {
        viewModelScope.launch { tripRepo.updateTrip(trip) }
    }
}
