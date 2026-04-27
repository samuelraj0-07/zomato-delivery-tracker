package com.delivery.tracker.utils

import java.text.DecimalFormat

object FormatUtils {
    private val moneyFormat = DecimalFormat("₹#,##0.00")
    private val kmFormat = DecimalFormat("#,##0.0")
    private val rateFormat = DecimalFormat("₹#,##0.00")

    fun formatMoney(amount: Double): String = moneyFormat.format(amount)
    fun formatKm(km: Double): String = "${kmFormat.format(km)} km"
    fun formatRate(rate: Double): String = "${rateFormat.format(rate)}/km"

    fun formatBalance(amount: Double): String {
        return if (amount >= 0) "+${moneyFormat.format(amount)}"
        else moneyFormat.format(amount)
    }
}
