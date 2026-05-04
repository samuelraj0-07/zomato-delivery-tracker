package com.delivery.tracker.viewmodel

import androidx.lifecycle.*
import com.delivery.tracker.data.model.*
import com.delivery.tracker.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// REPLACE WITH:
data class CycleSummary(
    val cycle: ServiceCycle? = null,
    val totalEarnings: Double = 0.0,
    val totalExtras: Double = 0.0,
    val fuelAllocated: Double = 0.0,
    val serviceAllocated: Double = 0.0,
    val fuelUsed: Double = 0.0,
    val serviceUsed: Double = 0.0,
    val fuelRemaining: Double = 0.0,
    val serviceRemaining: Double = 0.0,
    val tripCount: Int = 0
) {
    companion object {
        const val FUEL_RATE_PER_KM    = 1.5
        const val SERVICE_RATE_PER_KM = 0.7
    }
}

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val cycleRepo: CycleRepository,
    private val tripRepo: TripRepository
) : ViewModel() {

    val activeCycle = cycleRepo.getActiveCycle()
    val allCycles = cycleRepo.getAllCycles()
    val allTds = expenseRepo.getAllTds()

    private val _cycleSummary = MutableLiveData<CycleSummary>()
    val cycleSummary: LiveData<CycleSummary> = _cycleSummary

    private val _fuelSaved = MutableLiveData<Boolean>()
    val fuelSaved: LiveData<Boolean> = _fuelSaved

    private val _serviceSaved = MutableLiveData<Boolean>()
    val serviceSaved: LiveData<Boolean> = _serviceSaved

    private val _tdsSaved = MutableLiveData<Boolean>()
    val tdsSaved: LiveData<Boolean> = _tdsSaved

    init {
        activeCycle.observeForever { cycle ->
            cycle?.let { loadCycleSummary(it) }
        }
    }

    // REPLACE WITH:
private fun loadCycleSummary(cycle: ServiceCycle) {
    viewModelScope.launch {
        val trips        = tripRepo.getTripsByCycle(cycle.id).value ?: emptyList()
        val fuelUsed     = expenseRepo.getTotalFuelForCycle(cycle.id)
        val serviceUsed  = expenseRepo.getTotalServiceForCycle(cycle.id)
        val kmRidden         = cycle.kmCovered
        val fuelAllocated    = kmRidden * CycleSummary.FUEL_RATE_PER_KM
        val serviceAllocated = kmRidden * CycleSummary.SERVICE_RATE_PER_KM

        _cycleSummary.value = CycleSummary(
            cycle            = cycle,
            totalEarnings    = trips.sumOf { it.orderPay },
            totalExtras      = trips.sumOf { it.totalExtras },
            fuelAllocated    = fuelAllocated,
            serviceAllocated = serviceAllocated,
            fuelUsed         = fuelUsed,
            serviceUsed      = serviceUsed,
            fuelRemaining    = fuelAllocated - fuelUsed,
            serviceRemaining = serviceAllocated - serviceUsed,
            tripCount        = trips.size
        )
    }
}

    fun addFuelEntry(
        odometer: Double,
        pricePerLitre: Double,
        amountSpent: Double
    ) {
        viewModelScope.launch {
            val cycle = cycleRepo.getActiveCycleOnce()
            expenseRepo.addFuelEntry(
                FuelEntry(
                    dateMillis = System.currentTimeMillis(),
                    odometerReading = odometer,
                    fuelPricePerLitre = pricePerLitre,
                    amountSpent = amountSpent,
                    serviceCycleId = cycle?.id ?: 0L
                )
            )
            _fuelSaved.value = true
        }
    }

    fun addServiceEntry(
        odometer: Double,
        amountSpent: Double,
        details: String
    ) {
        viewModelScope.launch {
            val cycle = cycleRepo.getActiveCycleOnce()
            expenseRepo.addServiceEntry(
                ServiceEntry(
                    dateMillis = System.currentTimeMillis(),
                    odometerReading = odometer,
                    amountSpent = amountSpent,
                    details = details,
                    serviceCycleId = cycle?.id ?: 0L
                )
            )
            _serviceSaved.value = true
        }
    }

    fun addTdsEntry(
        weekLabel: String,
        weekStart: Long,
        weekEnd: Long,
        amount: Double
    ) {
        viewModelScope.launch {
            expenseRepo.addTdsEntry(
                TdsEntry(
                    weekLabel = weekLabel,
                    weekStartMillis = weekStart,
                    weekEndMillis = weekEnd,
                    amount = amount,
                    dateMillis = System.currentTimeMillis()
                )
            )
            _tdsSaved.value = true
        }
    }

    fun deleteTdsEntry(entry: TdsEntry) {
        viewModelScope.launch {
            expenseRepo.deleteTdsEntry(entry)
        }
    }

    fun startNewCycle(startOdometer: Double, fuelBudget: Double, serviceBudget: Double) {
        viewModelScope.launch {
            // Close existing active cycle
            cycleRepo.getActiveCycleOnce()?.let { existing ->
                cycleRepo.closeCycle(existing, startOdometer)
            }
            // Start new cycle
            cycleRepo.startNewCycle(
                ServiceCycle(
                    startOdometer = startOdometer,
                    startDateMillis = System.currentTimeMillis(),
                    fuelBudget = fuelBudget,
                    serviceBudget = serviceBudget
                )
            )
        }
    }

    fun getFuelByCycle(cycleId: Long) = expenseRepo.getFuelByCycle(cycleId)
    fun getServiceByCycle(cycleId: Long) = expenseRepo.getServiceByCycle(cycleId)
}
