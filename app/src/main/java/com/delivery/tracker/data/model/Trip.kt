package com.delivery.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val restaurantName: String,
    val assignedTime: String,       // "12:30 PM"
    val orderPay: Double,
    val screenshotDistance: Double, // from Zomato screenshot
    val incentivePay: Double = 0.0,
    val tips: Double = 0.0,
    val surgePay: Double = 0.0,
    val otherPay: Double = 0.0,
    val dateMillis: Long,           // date of trip
    val servicecycleId: Long = 0
) {
    // Live ₹/km using screenshot distance
    val ratePerKmLive: Double
        get() = if (screenshotDistance > 0) orderPay / screenshotDistance else 0.0

    // Total extra earnings
    val totalExtras: Double
        get() = incentivePay + tips + surgePay + otherPay

    // Total earnings including extras
    val totalEarnings: Double
        get() = orderPay + totalExtras
}
