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
    // All extra pay types stored by their exact key name.
    // e.g. {"incentive_pay": 5.0, "rain_bonus": 15.0, "festival_pay": 20.0}
    val extraPays: Map<String, Double> = emptyMap(),
    val screenType: ScreenType = ScreenType.UNKNOWN,
    val subOrders: List<SubOrderResult> = emptyList(),
    val isMultiOrder: Boolean = false
) {
    val totalExtras: Double get() = extraPays.values.sum()
    val totalEarnings: Double get() = orderPay + totalExtras
}

enum class ScreenType {
    TRIP_START,
    TRIP_END,
    UNKNOWN
}
