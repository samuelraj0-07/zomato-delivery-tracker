package com.delivery.tracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "sub_orders",
    foreignKeys = [ForeignKey(
        entity = Trip::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SubOrder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val orderNumber: Int,
    val restaurantName: String,
    val dropLocationName: String,
    val pickupDistanceKm: Double,
    val dropDistanceKm: Double,
    val orderAssignedTime: String,
    val orderPickedTime: String,
    val orderDeliveredTime: String
) {
    val totalDistanceKm: Double
        get() = pickupDistanceKm + dropDistanceKm
}
