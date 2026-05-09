package com.delivery.tracker.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.delivery.tracker.data.model.FuelEntry
import com.delivery.tracker.data.model.ServiceEntry
import com.delivery.tracker.data.model.TdsEntry

@Dao
interface ExpenseDao {
    // Fuel
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFuel(entry: FuelEntry): Long

    @Delete
    suspend fun deleteFuel(entry: FuelEntry)

    @Query("SELECT * FROM fuel_entries WHERE dateMillis BETWEEN :start AND :end ORDER BY dateMillis DESC")
    fun getFuelByDateRange(start: Long, end: Long): LiveData<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries WHERE serviceCycleId = :cycleId ORDER BY dateMillis ASC")
    fun getFuelByCycle(cycleId: Long): LiveData<List<FuelEntry>>

    @Query("SELECT SUM(amountSpent) FROM fuel_entries WHERE dateMillis BETWEEN :start AND :end")
    suspend fun getTotalFuel(start: Long, end: Long): Double?

    @Query("SELECT SUM(amountSpent) FROM service_entries WHERE dateMillis BETWEEN :start AND :end")
    suspend fun getTotalService(start: Long, end: Long): Double?

    @Query("SELECT * FROM service_entries ORDER BY dateMillis DESC")
    fun getAllService(): LiveData<List<ServiceEntry>>

    @Query("SELECT SUM(amountSpent) FROM fuel_entries WHERE serviceCycleId = :cycleId")
    suspend fun getTotalFuelForCycle(cycleId: Long): Double?

    // Service
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(entry: ServiceEntry): Long

    @Delete
    suspend fun deleteService(entry: ServiceEntry)

    @Query("SELECT * FROM service_entries WHERE dateMillis BETWEEN :start AND :end ORDER BY dateMillis DESC")
    fun getServiceByDateRange(start: Long, end: Long): LiveData<List<ServiceEntry>>

    @Query("SELECT * FROM service_entries WHERE serviceCycleId = :cycleId ORDER BY dateMillis ASC")
    fun getServiceByCycle(cycleId: Long): LiveData<List<ServiceEntry>>

    @Query("SELECT SUM(amountSpent) FROM service_entries WHERE serviceCycleId = :cycleId")
    suspend fun getTotalServiceForCycle(cycleId: Long): Double?

    // TDS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTds(entry: TdsEntry): Long

    @Delete
    suspend fun deleteTds(entry: TdsEntry)

    @Query("SELECT * FROM tds_entries ORDER BY weekStartMillis DESC")
    fun getAllTds(): LiveData<List<TdsEntry>>

    @Query("SELECT * FROM fuel_entries ORDER BY dateMillis DESC")
    fun getAllFuel(): LiveData<List<FuelEntry>>

    @Query("SELECT SUM(amount) FROM tds_entries WHERE weekStartMillis < :end AND weekEndMillis > :start")
    suspend fun getTotalTds(start: Long, end: Long): Double?

    @Update
    suspend fun updateFuel(entry: FuelEntry)

    @Update
    suspend fun updateService(entry: ServiceEntry)

    @Query("SELECT * FROM fuel_entries ORDER BY dateMillis DESC")
    suspend fun getFuelForExport(): List<FuelEntry>

    @Query("SELECT * FROM service_entries ORDER BY dateMillis DESC")
    suspend fun getServiceForExport(): List<ServiceEntry>

    @Query("""
        UPDATE fuel_entries
        SET serviceCycleId = :cycleId
        WHERE serviceCycleId = 0
        AND odometerReading >= :cycleStartOdo
        AND (:cycleEndOdo = 0.0 OR odometerReading < :cycleEndOdo)
    """)
    suspend fun linkFuelToCycle(cycleId: Long, cycleStartOdo: Double, cycleEndOdo: Double)

    @Query("""
        UPDATE service_entries
        SET serviceCycleId = :cycleId
        WHERE serviceCycleId = 0
        AND odometerReading >= :cycleStartOdo
        AND (:cycleEndOdo = 0.0 OR odometerReading < :cycleEndOdo)
    """)
    suspend fun linkServiceToCycle(cycleId: Long, cycleStartOdo: Double, cycleEndOdo: Double)

    @Query("DELETE FROM fuel_entries")
    suspend fun deleteAllFuel()

    @Query("DELETE FROM service_entries")
    suspend fun deleteAllService()

    @Query("DELETE FROM tds_entries")
    suspend fun deleteAllTds()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFuelWithId(entry: FuelEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceWithId(entry: ServiceEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTdsWithId(entry: TdsEntry): Long

    @Query("SELECT * FROM tds_entries ORDER BY weekStartMillis DESC")
    suspend fun getTdsForExport(): List<TdsEntry>
}
