package com.delivery.tracker.ocr

data class SubOrderResult(
    val orderNumber: Int = 1,
    val restaurantName: String = "",
    val dropLocationName: String = "",
    val pickupDistanceKm: Double = 0.0,
    val dropDistanceKm: Double = 0.0,
    val orderAssignedTime: String = "",
    val orderPickedTime: String = "",
    val orderDeliveredTime: String = ""
) {
    val totalDistanceKm: Double get() = pickupDistanceKm + dropDistanceKm
}

data class OcrResult(
    val restaurantName: String = "",
    val assignedTime: String = "",
    val orderPay: Double = 0.0,
    val distance: Double = 0.0,
    val incentivePay: Double = 0.0,
    val tips: Double = 0.0,
    val surgePay: Double = 0.0,
    val otherPay: Double = 0.0,
    val screenType: ScreenType = ScreenType.UNKNOWN,
    val subOrders: List<SubOrderResult> = emptyList(),
    val isMultiOrder: Boolean = false
)

enum class ScreenType {
    TRIP_START,
    TRIP_END,
    UNKNOWN
}
