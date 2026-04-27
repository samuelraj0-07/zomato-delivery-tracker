package com.delivery.tracker.data.repository

import com.delivery.tracker.data.db.ExpenseDao
import com.delivery.tracker.data.model.FuelEntry
import com.delivery.tracker.data.model.ServiceEntry
import com.delivery.tracker.data.model.TdsEntry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {
    // Fuel
    fun getFuelByDateRange(start: Long, end: Long) =
        expenseDao.getFuelByDateRange(start, end)

    fun getFuelByCycle(cycleId: Long) =
        expenseDao.getFuelByCycle(cycleId)

    suspend fun addFuelEntry(entry: FuelEntry) =
        expenseDao.insertFuel(entry)

    suspend fun deleteFuelEntry(entry: FuelEntry) =
        expenseDao.deleteFuel(entry)

    suspend fun getTotalFuel(start: Long, end: Long) =
        expenseDao.getTotalFuel(start, end) ?: 0.0

    suspend fun getTotalFuelForCycle(cycleId: Long) =
        expenseDao.getTotalFuelForCycle(cycleId) ?: 0.0

    // Service
    fun getServiceByDateRange(start: Long, end: Long) =
        expenseDao.getServiceByDateRange(start, end)

    fun getServiceByCycle(cycleId: Long) =
        expenseDao.getServiceByCycle(cycleId)

    suspend fun addServiceEntry(entry: ServiceEntry) =
        expenseDao.insertService(entry)

    suspend fun deleteServiceEntry(entry: ServiceEntry) =
        expenseDao.deleteService(entry)

    suspend fun getTotalServiceForCycle(cycleId: Long) =
        expenseDao.getTotalServiceForCycle(cycleId) ?: 0.0

    // TDS
    fun getAllTds() = expenseDao.getAllTds()

    suspend fun addTdsEntry(entry: TdsEntry) =
        expenseDao.insertTds(entry)

    suspend fun deleteTdsEntry(entry: TdsEntry) =
        expenseDao.deleteTds(entry)

    suspend fun getTotalTds(start: Long, end: Long) =
        expenseDao.getTotalTds(start, end) ?: 0.0
}
