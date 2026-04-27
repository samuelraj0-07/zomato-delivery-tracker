package com.delivery.tracker.viewmodel

import androidx.lifecycle.*
import com.delivery.tracker.data.model.*
import com.delivery.tracker.data.repository.*
import com.delivery.tracker.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodaySummary(
    val totalTrips: Int = 0,
    val totalOrderPay: Double = 0.0,
    val totalExtras: Double = 0.0,
    val totalScreenshotDistance: Double = 0.0,
    val actualDistance: Double = 0.0,
    val ratePerKmLive: Double = 0.0,      // order pay / screenshot distance
    val ratePerKmActual: Double = 0.0,    // order pay / odometer distance
    val deadKm: Double = 0.0,
    val isSessionEnded: Boolean = false
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val tripRepo: TripRepository,
    private val sessionRepo: SessionRepository,
    private val cycleRepo: CycleRepository
) : ViewModel() {

    private val _activeSession = sessionRepo.getActiveSession()
    val activeSession: LiveData<DailySession?> = _activeSession

    private val _todayTrips = MutableLiveData<List<Trip>>()
    val todayTrips: LiveData<List<Trip>> = _todayTrips

    private val _todaySummary = MutableLiveData<TodaySummary>()
    val todaySummary: LiveData<TodaySummary> = _todaySummary

    private val _ocrError = MutableLiveData<String?>()
    val ocrError: LiveData<String?> = _ocrError

    private val _sessionStarted = MutableLiveData<Boolean>()
    val sessionStarted: LiveData<Boolean> = _sessionStarted

    private val _sessionEnded = MutableLiveData<Boolean>()
    val sessionEnded: LiveData<Boolean> = _sessionEnded

    init {
        // Load today's trips whenever active session changes
        _activeSession.observeForever { session ->
            session?.let { loadTodayTrips(it.id) }
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
        val totalOrderPay = trips.sumOf { it.orderPay }
        val totalExtras = trips.sumOf { it.totalExtras }
        val totalScreenshotDist = trips.sumOf { it.screenshotDistance }
        val actualDist = session?.actualDistance ?: 0.0

        val ratePerKmLive = if (totalScreenshotDist > 0)
            totalOrderPay / totalScreenshotDist else 0.0

        val ratePerKmActual = if (actualDist > 0)
            totalOrderPay / actualDist else 0.0

        _todaySummary.value = TodaySummary(
            totalTrips = trips.size,
            totalOrderPay = totalOrderPay,
            totalExtras = totalExtras,
            totalScreenshotDistance = totalScreenshotDist,
            actualDistance = actualDist,
            ratePerKmLive = ratePerKmLive,
            ratePerKmActual = ratePerKmActual,
            deadKm = (actualDist - totalScreenshotDist).coerceAtLeast(0.0),
            isSessionEnded = session?.isEnded ?: false
        )
    }

    fun startDay(startOdometer: Double) {
        viewModelScope.launch {
            val existing = sessionRepo.getActiveSessionOnce()
            if (existing != null) {
                _sessionStarted.value = true
                return@launch
            }
            val cycle = cycleRepo.getActiveCycleOnce()
            val session = DailySession(
                dateMillis = DateUtils.startOfDay(),
                startOdometer = startOdometer,
                serviceCycleId = cycle?.id ?: 0L
            )
            sessionRepo.startSession(session)
            _sessionStarted.value = true
        }
    }

    fun endDay(endOdometer: Double) {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSessionOnce() ?: return@launch
            sessionRepo.updateSession(
                session.copy(
                    endOdometer = endOdometer,
                    isEnded = true
                )
            )
            // Recalculate with actual distance
            val trips = _todayTrips.value ?: emptyList()
            recalculateSummary(trips)
            _sessionEnded.value = true
        }
    }

    fun addTrip(trip: Trip) {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSessionOnce() ?: return@launch
            tripRepo.addTrip(
                trip.copy(
                    sessionId = session.id,
                    dateMillis = System.currentTimeMillis(),
                    servicecycleId = session.serviceCycleId
                )
            )
        }
    }

    fun deleteTrip(trip: Trip) {
        viewModelScope.launch {
            tripRepo.deleteTrip(trip)
        }
    }

    fun updateTrip(trip: Trip) {
        viewModelScope.launch {
            tripRepo.updateTrip(trip)
        }
    }

    fun clearOcrError() {
        _ocrError.value = null
    }
}
