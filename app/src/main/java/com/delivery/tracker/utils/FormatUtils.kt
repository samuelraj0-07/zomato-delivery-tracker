package com.delivery.tracker.utils

import java.text.DecimalFormat

object FormatUtils {
    private val moneyFormat = DecimalFormat("₹#,##0.00")
    private val kmFormat    = DecimalFormat("#,##0.0")   // 1 decimal, e.g. 5.5 km
    private val odoFormat   = DecimalFormat("#,##0.0")   // 1 decimal for odometer readings
    private val rateFormat  = DecimalFormat("₹#,##0.00")

    fun formatMoney(amount: Double): String = moneyFormat.format(amount)

    /** For trip distances, dead km, actual distance — appends " km" */
    fun formatKm(km: Double): String = "${kmFormat.format(km)} km"

    /** For odometer readings — appends " km", 1 decimal, e.g. 20228.8 km */
    fun formatOdo(km: Double): String = "${odoFormat.format(km)} km"

    fun formatRate(rate: Double): String = "${rateFormat.format(rate)}/km"

    fun formatBalance(amount: Double): String {
        return if (amount >= 0) "+${moneyFormat.format(amount)}"
        else moneyFormat.format(amount)
    }
}
