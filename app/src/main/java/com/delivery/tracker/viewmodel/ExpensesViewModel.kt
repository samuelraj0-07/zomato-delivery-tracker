package com.delivery.tracker.viewmodel

import androidx.lifecycle.*
import com.delivery.tracker.data.model.*
import com.delivery.tracker.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.delivery.tracker.data.repository.SessionRepository

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
    private val tripRepo: TripRepository,
    private val sessionRepo: SessionRepository
) : ViewModel() {

    val activeCycle = cycleRepo.getActiveCycle()
    val allCycles = cycleRepo.getAllCycles()
    val allTds = expenseRepo.getAllTds()
    val allFuel    = expenseRepo.getAllFuel()
    val allService = expenseRepo.getAllService()

    private val _cycleSummary = MutableLiveData<CycleSummary>()
    val cycleSummary: LiveData<CycleSummary> = _cycleSummary

    private val _fuelSaved = MutableLiveData<Boolean>()
    val fuelSaved: LiveData<Boolean> = _fuelSaved

    private val _serviceSaved = MutableLiveData<Boolean>()
    val serviceSaved: LiveData<Boolean> = _serviceSaved

    private val _tdsSaved = MutableLiveData<Boolean>()
    val tdsSaved: LiveData<Boolean> = _tdsSaved

    init {
        // Merge cycle + expense changes into one reactive stream
        val trigger = MediatorLiveData<Unit>()
        trigger.addSource(activeCycle) { trigger.value = Unit }
        trigger.addSource(allFuel)    { trigger.value = Unit }
        trigger.addSource(allService) { trigger.value = Unit }

        trigger.observeForever {
            val cycle = activeCycle.value ?: return@observeForever
            loadCycleSummary(cycle)
        }
    }

    // REPLACE WITH:
private fun loadCycleSummary(cycle: ServiceCycle) {
    viewModelScope.launch {
        val trips       = tripRepo.getTripsByCycleOnce(cycle.id)
        val fuelUsed    = expenseRepo.getTotalFuelForCycle(cycle.id)
        val serviceUsed = expenseRepo.getTotalServiceForCycle(cycle.id)

        // For active cycles, use latest odometer from sessions instead of endOdometer
        val currentOdo = if (cycle.isActive) {
            sessionRepo.getMaxEndOdometer() ?: cycle.startOdometer
        } else {
            cycle.endOdometer
        }
        val kmRidden = (currentOdo - cycle.startOdometer).coerceAtLeast(0.0)

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

    fun deleteFuelEntry(entry: FuelEntry) {
        viewModelScope.launch {
            expenseRepo.deleteFuelEntry(entry)
        }
    }

    fun deleteServiceEntry(entry: ServiceEntry) {
        viewModelScope.launch {
            expenseRepo.deleteServiceEntry(entry)
        }
    }

    fun startNewCycle(startOdometer: Double) {
        startNewCycleWithDate(startOdometer, System.currentTimeMillis())
    }

    fun startNewCycleWithDate(startOdometer: Double, startDateMillis: Long) {
        viewModelScope.launch {
            cycleRepo.getActiveCycleOnce()?.let { existing ->
                cycleRepo.closeCycle(existing, startOdometer)
            }
            cycleRepo.startNewCycle(
                ServiceCycle(
                    startOdometer   = startOdometer,
                    startDateMillis = startDateMillis
                )
            )
        }
    }

    fun endCurrentCycle(endOdometer: Double) {
        viewModelScope.launch {
            cycleRepo.getActiveCycleOnce()?.let { cycle ->
                cycleRepo.closeCycle(cycle, endOdometer)
            }
        }
    }

    fun getFuelByCycle(cycleId: Long) = expenseRepo.getFuelByCycle(cycleId)
    fun getServiceByCycle(cycleId: Long) = expenseRepo.getServiceByCycle(cycleId)

    fun deleteCycle(cycle: ServiceCycle) {
        viewModelScope.launch {
            cycleRepo.deleteCycle(cycle)
        }
    }

    fun updateCycleDetails(cycle: ServiceCycle) {
        viewModelScope.launch {
            cycleRepo.updateCycleDetails(cycle)
        }
    }

    /** Returns the highest end-odometer ever recorded, for pre-filling dialogs. */
    fun getLastKnownOdometer(callback: (Double) -> Unit) {
        viewModelScope.launch {
            val maxOdo = sessionRepo.getMaxEndOdometer() ?: 0.0
            callback(maxOdo)
        }
    }
}
