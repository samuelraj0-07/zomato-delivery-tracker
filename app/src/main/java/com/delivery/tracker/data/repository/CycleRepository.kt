package com.delivery.tracker.data.repository

import com.delivery.tracker.data.db.ServiceCycleDao
import com.delivery.tracker.data.model.ServiceCycle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CycleRepository @Inject constructor(
    private val cycleDao: ServiceCycleDao
) {
    fun getActiveCycle() = cycleDao.getActiveCycle()
    fun getAllCycles() = cycleDao.getAllCycles()

    suspend fun getActiveCycleOnce() = cycleDao.getActiveCycleOnce()

    suspend fun startNewCycle(cycle: ServiceCycle) =
        cycleDao.insert(cycle)

    suspend fun updateCycle(cycle: ServiceCycle) =
        cycleDao.update(cycle)

    suspend fun closeCycle(cycle: ServiceCycle, endOdometer: Double) =
        cycleDao.update(
            cycle.copy(
                endOdometer = endOdometer,
                isActive = false,
                endDateMillis = System.currentTimeMillis()
            )
        )
}
