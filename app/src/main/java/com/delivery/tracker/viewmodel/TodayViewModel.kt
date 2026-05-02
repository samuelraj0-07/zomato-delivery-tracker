package com.delivery.tracker.viewmodel

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.delivery.tracker.data.db.SubOrderDao
import com.delivery.tracker.data.model.*
import com.delivery.tracker.data.repository.*
import com.delivery.tracker.ocr.OcrResult
import com.delivery.tracker.utils.DateUtils
import com.delivery.tracker.utils.SingleLiveEvent

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

    /** The date the user has chosen for this session (defaults to today). */
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

    init {
        _activeSession.observeForever { session ->
            if (session != null) {
                // Sync the displayed date to the session's actual date
                _selectedDateMillis.value = session.dateMillis
                loadTodayTrips(session.id)
            }
        }
    }

    fun setSelectedDate(dateMillis: Long) {
        // Only allow changing the date when no active session exists
        if (_activeSession.value == null) {
            _selectedDateMillis.value = dateMillis
        }
    }

    private fun loadTodayTrips(sessionId: Long) {
        tripRepo.getTripsBySession(sessionId).observeForever { trips ->
            _todayTrips.value = trips
            recalculateSummary(trips)
        }
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

    // Keep the existing recalculateSummary() exactly as is, then ADD this below it:

    // New overload that accepts an explicit session (used by endDay so we
    // don't depend on LiveData catching up after the DB write)
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




    /**
     * Start a day session for the given [dateMillis] (defaults to selectedDateMillis).
     * This allows feeding in past data by picking an earlier date before starting.
     */
    fun startDay(startOdometer: Double, dateMillis: Long = _selectedDateMillis.value ?: System.currentTimeMillis()) {
        viewModelScope.launch {
            val existing = sessionRepo.getActiveSessionOnce()
            if (existing != null) { _sessionStarted.value = true; return@launch }

            // Validate: startOdometer must be >= the highest endOdometer ever recorded
            val maxPrevOdometer = sessionRepo.getMaxEndOdometer() ?: 0.0
            if (maxPrevOdometer > 0 && startOdometer < maxPrevOdometer) {
                _odometerError.value =
                    "Start odometer (%.1f km) is less than last recorded reading (%.1f km)."
                        .format(startOdometer, maxPrevOdometer)
                return@launch
            }

            val cycle = cycleRepo.getActiveCycleOnce()
            sessionRepo.startSession(
                DailySession(
                    dateMillis     = DateUtils.startOfDay(dateMillis),
                    startOdometer  = startOdometer,
                    serviceCycleId = cycle?.id ?: 0L
                )
            )
            _sessionStarted.value = true
        }
    }

    fun endDay(endOdometer: Double) {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSessionOnce() ?: return@launch

            // Validate: endOdometer must be greater than startOdometer
            if (endOdometer <= session.startOdometer) {
                _odometerError.value =
                    "End odometer (%.1f km) must be greater than start odometer (%.1f km)."
                        .format(endOdometer, session.startOdometer)
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
                    dateMillis     = System.currentTimeMillis(),
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
                    dateMillis         = System.currentTimeMillis(),
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

    /** Add multiple trips from a parsed JSON list (one per element). */
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
                        dateMillis         = System.currentTimeMillis(),
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
