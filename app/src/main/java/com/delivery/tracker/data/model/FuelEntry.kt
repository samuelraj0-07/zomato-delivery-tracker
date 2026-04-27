package com.delivery.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_entries")
data class FuelEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long,
    val odometerReading: Double,
    val fuelPricePerLitre: Double,
    val amountSpent: Double,
    val serviceCycleId: Long = 0
) {
    val litresFilled: Double
        get() = if (fuelPricePerLitre > 0) amountSpent / fuelPricePerLitre else 0.0
}
