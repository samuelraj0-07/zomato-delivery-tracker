package com.delivery.tracker.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.delivery.tracker.data.model.Trip

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)

    @Query("SELECT * FROM trips WHERE sessionId = :sessionId ORDER BY assignedTime ASC")
    fun getTripsBySession(sessionId: Long): LiveData<List<Trip>>

    @Query("SELECT * FROM trips WHERE dateMillis BETWEEN :startMillis AND :endMillis ORDER BY dateMillis ASC")
    fun getTripsByDateRange(startMillis: Long, endMillis: Long): LiveData<List<Trip>>

    @Query("SELECT * FROM trips WHERE serviceCycleId = :cycleId ORDER BY dateMillis ASC")
    fun getTripsByCycle(cycleId: Long): LiveData<List<Trip>>

    @Query("SELECT * FROM trips WHERE dateMillis BETWEEN :startMillis AND :endMillis")
    suspend fun getTripsForRange(startMillis: Long, endMillis: Long): List<Trip>

    @Query("SELECT * FROM trips ORDER BY dateMillis DESC")
    fun getAllTrips(): LiveData<List<Trip>>

    @Query("SELECT SUM(orderPay) FROM trips WHERE dateMillis BETWEEN :start AND :end")
    suspend fun getTotalOrderPay(start: Long, end: Long): Double?

    @Query("SELECT SUM(screenshotDistance) FROM trips WHERE dateMillis BETWEEN :start AND :end")
    suspend fun getTotalDistance(start: Long, end: Long): Double?
}
