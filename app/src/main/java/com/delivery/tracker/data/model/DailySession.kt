package com.delivery.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_sessions")
data class DailySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long,           // start of day in millis
    val startOdometer: Double,
    val endOdometer: Double = 0.0,  // 0 means day not ended yet
    val isEnded: Boolean = false,
    val serviceCycleId: Long = 0
) {
    // Actual distance from odometer
    val actualDistance: Double
        get() = if (endOdometer > startOdometer)
            endOdometer - startOdometer else 0.0

    // Day is still running
    val isRunning: Boolean
        get() = !isEnded
}
