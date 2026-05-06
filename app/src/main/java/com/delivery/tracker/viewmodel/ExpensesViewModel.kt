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
    val totalEarnings: Double = 0.0,   // base orderPay sum
    val totalExtras: Double = 0.0,     // extras sum
    val totalTds: Double = 0.0,        // TDS deducted this cycle
    val fuelAllocated: Double = 0.0,
    val serviceAllocated: Double = 0.0,
    val fuelUsed: Double = 0.0,
    val serviceUsed: Double = 0.0,
    val fuelRemaining: Double = 0.0,
    val serviceRemaining: Double = 0.0,
    val kmRidden: Double = 0.0,        // actual km from sessions
    val tripCount: Int = 0
) {
    // Net remaining = all earnings − fuel spent − service spent − TDS
    val netRemaining: Double
        get() = (totalEarnings + totalExtras) - fuelUsed - serviceUsed - totalTds

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

    private fun loadCycleSummary(cycle: ServiceCycle) {
        viewModelScope.launch {
            // ── Repair: stamp serviceCycleId on any sessions that were saved
            // before the cycle existed (serviceCycleId = 0 but odo fits this cycle).
            // cycleEndOdo = 0.0 means active cycle — no upper bound.
            sessionRepo.linkSessionsToCycle(
                cycleId       = cycle.id,
                cycleStartOdo = cycle.startOdometer,
                cycleEndOdo   = if (cycle.isActive) 0.0 else cycle.endOdometer
            )

            val trips       = tripRepo.getTripsByCycleOnce(cycle.id)
            val fuelUsed    = expenseRepo.getTotalFuelForCycle(cycle.id)
            val serviceUsed = expenseRepo.getTotalServiceForCycle(cycle.id)

            // KM ridden: for active cycle use session sum; for ended cycle use odo diff
            val kmRidden = if (cycle.isActive) {
                sessionRepo.getTotalKmForCycle(cycle.id)
            } else {
                cycle.kmCovered
            }

            // Allocated = km ridden × rate per km (money saved from riding)
            val fuelAllocated    = kmRidden * CycleSummary.FUEL_RATE_PER_KM
            val serviceAllocated = kmRidden * CycleSummary.SERVICE_RATE_PER_KM

            // TDS for this cycle: sum entries whose week falls within cycle date range
            val cycleEndMillis = if (cycle.endDateMillis > 0) cycle.endDateMillis
                                 else System.currentTimeMillis()
            val totalTds = expenseRepo.getTotalTds(cycle.startDateMillis, cycleEndMillis)

            val totalEarnings = trips.sumOf { it.orderPay }
            val totalExtras   = trips.sumOf { it.totalExtras }

            _cycleSummary.value = CycleSummary(
                cycle            = cycle,
                totalEarnings    = totalEarnings,
                totalExtras      = totalExtras,
                totalTds         = totalTds,
                fuelAllocated    = fuelAllocated,
                serviceAllocated = serviceAllocated,
                fuelUsed         = fuelUsed,
                serviceUsed      = serviceUsed,
                fuelRemaining    = fuelAllocated - fuelUsed,
                serviceRemaining = serviceAllocated - serviceUsed,
                kmRidden         = kmRidden,
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

    fun updateFuelEntry(entry: FuelEntry) {
        viewModelScope.launch { expenseRepo.updateFuelEntry(entry) }
    }

    fun updateServiceEntry(entry: ServiceEntry) {
        viewModelScope.launch { expenseRepo.updateServiceEntry(entry) }
    }

    suspend fun getCycleEarnings(cycleId: Long)    = tripRepo.getTotalEarningsForCycle(cycleId)
    suspend fun getCycleExtras(cycleId: Long)      = tripRepo.getTotalExtrasForCycle(cycleId)
    suspend fun getCycleFuelUsed(cycleId: Long)    = expenseRepo.getTotalFuelForCycle(cycleId)
    suspend fun getCycleServiceUsed(cycleId: Long) = expenseRepo.getTotalServiceForCycle(cycleId)
    suspend fun getCycleKmRidden(cycleId: Long)    = sessionRepo.getTotalKmForCycle(cycleId)
}
