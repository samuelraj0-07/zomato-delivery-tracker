package com.delivery.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_sessions")
data class DailySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long,
    val startOdometer: Double,
    val endOdometer: Double = 0.0,
    val isEnded: Boolean = false,
    val serviceCycleId: Long = 0,
    val isRetroactive: Boolean = false  // true = placeholder, excluded from odo validation
) {
    val actualDistance: Double
        get() = if (endOdometer > startOdometer) endOdometer - startOdometer else 0.0

    val isRunning: Boolean
        get() = !isEnded
}