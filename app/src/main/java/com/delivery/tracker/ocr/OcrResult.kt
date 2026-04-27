package com.delivery.tracker.ocr

data class OcrResult(
    val restaurantName: String = "",
    val assignedTime: String = "",
    val orderPay: Double = 0.0,
    val distance: Double = 0.0,
    val incentivePay: Double = 0.0,
    val tips: Double = 0.0,
    val surgePay: Double = 0.0,
    val otherPay: Double = 0.0,
    val screenType: ScreenType = ScreenType.UNKNOWN
)

enum class ScreenType {
    TRIP_START,   // before delivery - has restaurant, est. distance, est. pay
    TRIP_END,     // after delivery - has actual distance, actual pay
    UNKNOWN
}
