package com.delivery.tracker.data.repository

import com.delivery.tracker.data.db.TripDao
import com.delivery.tracker.data.model.Trip
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao
) {
    fun getTripsBySession(sessionId: Long) =
        tripDao.getTripsBySession(sessionId)

    fun getTripsByDateRange(start: Long, end: Long) =
        tripDao.getTripsByDateRange(start, end)

    fun getTripsByCycle(cycleId: Long) =
        tripDao.getTripsByCycle(cycleId)

    fun getAllTrips() = tripDao.getAllTrips()

    suspend fun addTrip(trip: Trip) = tripDao.insert(trip)
    suspend fun updateTrip(trip: Trip) = tripDao.update(trip)
    suspend fun deleteTrip(trip: Trip) = tripDao.delete(trip)

    suspend fun getTotalOrderPay(start: Long, end: Long) =
        tripDao.getTotalOrderPay(start, end) ?: 0.0

    suspend fun getTotalDistance(start: Long, end: Long) =
        tripDao.getTotalDistance(start, end) ?: 0.0

    suspend fun getTripsForRange(start: Long, end: Long) =
        tripDao.getTripsForRange(start, end)
}
