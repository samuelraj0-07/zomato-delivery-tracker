package com.delivery.tracker.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.delivery.tracker.data.model.ServiceCycle

@Dao
interface ServiceCycleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cycle: ServiceCycle): Long

    @Update
    suspend fun update(cycle: ServiceCycle)

    @Query("SELECT * FROM service_cycles WHERE isActive = 1 LIMIT 1")
    fun getActiveCycle(): LiveData<ServiceCycle?>

    @Query("SELECT * FROM service_cycles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveCycleOnce(): ServiceCycle?

    @Query("SELECT * FROM service_cycles ORDER BY startDateMillis DESC")
    fun getAllCycles(): LiveData<List<ServiceCycle>>
}
