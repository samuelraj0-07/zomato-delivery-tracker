package com.delivery.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * extraPays stores ALL extra pay types by their exact key name.
 * e.g. {"incentive_pay": 5.0, "customer_tip": 10.0, "rain_bonus": 15.0}
 *
 * New pay types from Zomato are captured automatically by key name —
 * no schema change or migration needed.
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val restaurantName: String,
    val assignedTime: String,
    val orderPay: Double,
    val screenshotDistance: Double,
    val extraPays: Map<String, Double> = emptyMap(),  // replaces incentivePay/tips/surgePay/otherPay
    val dateMillis: Long,
    val servicecycleId: Long = 0
) {
    // Sum of all extra pay types, whatever they are called
    val totalExtras: Double
        get() = extraPays.values.sum()

    // Total earnings
    val totalEarnings: Double
        get() = orderPay + totalExtras

    // ₹/km using screenshot distance
    val ratePerKmLive: Double
        get() = if (screenshotDistance > 0) orderPay / screenshotDistance else 0.0

    // Convenience accessors for the known pay types (safe, return 0.0 if absent)
    val incentivePay: Double get() = extraPays["incentive_pay"] ?: 0.0
    val tips: Double         get() = extraPays["customer_tip"]  ?: 0.0
    val surgePay: Double     get() = extraPays["surge_pay"]     ?: 0.0
}
