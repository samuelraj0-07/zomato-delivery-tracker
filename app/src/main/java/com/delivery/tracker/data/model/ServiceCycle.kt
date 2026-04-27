package com.delivery.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_cycles")
data class ServiceCycle(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startOdometer: Double,
    val endOdometer: Double = 0.0,  // 0 = cycle still active
    val cycleKmLimit: Double = 3000.0,
    val startDateMillis: Long,
    val endDateMillis: Long = 0L,
    val isActive: Boolean = true,
    val fuelBudget: Double = 0.0,   // user can set expected fuel budget
    val serviceBudget: Double = 0.0 // user can set expected service budget
) {
    val kmCovered: Double
        get() = if (endOdometer > startOdometer) endOdometer - startOdometer else 0.0

    val progressPercent: Int
        get() = ((kmCovered / cycleKmLimit) * 100).toInt().coerceIn(0, 100)

    val remainingKm: Double
        get() = (cycleKmLimit - kmCovered).coerceAtLeast(0.0)
}
