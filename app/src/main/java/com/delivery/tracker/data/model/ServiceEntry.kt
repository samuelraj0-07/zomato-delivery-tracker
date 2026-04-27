package com.delivery.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_entries")
data class ServiceEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long,
    val odometerReading: Double,
    val amountSpent: Double,
    val details: String,            // "Oil change", "Tyre", etc.
    val serviceCycleId: Long = 0
)
