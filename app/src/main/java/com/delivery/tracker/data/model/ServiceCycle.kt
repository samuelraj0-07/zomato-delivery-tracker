package com.delivery.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_cycles")
data class ServiceCycle(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startOdometer: Double,
    val endOdometer: Double = 0.0,      // 0 = cycle still active
    val startDateMillis: Long,
    val endDateMillis: Long = 0L,
    val isActive: Boolean = true,
    val fuelBudget: Double = 0.0,
    val serviceBudget: Double = 0.0
) {
    val kmCovered: Double
        get() = if (endOdometer > startOdometer) endOdometer - startOdometer else 0.0

    // Keep this so existing code using cycleKmLimit doesn't break
    val cycleKmLimit: Double get() = 0.0
    val progressPercent: Int get() = 0
    val remainingKm: Double get() = 0.0
}