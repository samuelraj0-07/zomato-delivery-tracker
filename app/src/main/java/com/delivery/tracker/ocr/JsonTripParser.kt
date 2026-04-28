package com.delivery.tracker.ocr

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses a JSON array of trip objects pasted by the user.
 *
 * extra_pay accepts ANY key — all keys are preserved by name.
 * Known keys (incentive_pay, customer_tip, surge_pay) AND any new keys
 * (rain_bonus, festival_pay, long_distance_pay, etc.) are all stored
 * individually in extraPays: Map<String, Double>.
 *
 * No code change needed when Zomato introduces a new pay type.
 */
object JsonTripParser {

    fun parse(jsonText: String): OcrResult? {
        return try {
            val array = JSONArray(jsonText.trim())
            if (array.length() == 0) return null

            val subOrders         = mutableListOf<SubOrderResult>()
            var totalOrderPay     = 0.0
            var totalDistance     = 0.0
            // Accumulate all extra pay types by key name across all sub-orders
            val combinedExtraPays = mutableMapOf<String, Double>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                val restaurantName = obj.optString("restaurant_name", "")
                val assignedTime   = obj.optString("order_assigned_time", "")
                val orderPay       = obj.optDouble("order_pay", 0.0)
                val distanceKm     = if (obj.isNull("total_distance_km")) 0.0
                                     else obj.optDouble("total_distance_km", 0.0)

                // Parse every key inside extra_pay — nothing is hardcoded
                val extras = parseExtraPays(
                    if (obj.has("extra_pay") && !obj.isNull("extra_pay"))
                        obj.getJSONObject("extra_pay")
                    else null
                )

                totalOrderPay += orderPay
                totalDistance += distanceKm

                // Merge this order's extras into the running total
                extras.forEach { (key, value) ->
                    combinedExtraPays[key] = (combinedExtraPays[key] ?: 0.0) + value
                }

                subOrders.add(
                    SubOrderResult(
                        orderNumber        = i + 1,
                        restaurantName     = restaurantName,
                        dropLocationName   = "",
                        pickupDistanceKm   = 0.0,
                        dropDistanceKm     = distanceKm,
                        orderAssignedTime  = assignedTime,
                        orderPickedTime    = "",
                        orderDeliveredTime = ""
                    )
                )
            }

            val restaurantName = when {
                subOrders.size == 1 -> subOrders[0].restaurantName
                else -> {
                    val unique = subOrders.map { it.restaurantName }
                        .filter { it.isNotEmpty() }.distinct()
                    if (unique.size == 1) unique[0] else "Multi-order"
                }
            }

            OcrResult(
                restaurantName = restaurantName,
                assignedTime   = subOrders.firstOrNull()?.orderAssignedTime ?: "",
                orderPay       = totalOrderPay,
                distance       = totalDistance,
                extraPays      = combinedExtraPays,   // all pay types, by name
                screenType     = ScreenType.TRIP_END,
                subOrders      = subOrders,
                isMultiOrder   = subOrders.size > 1
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads every key from the extra_pay JSON object and returns
     * them as a Map<String, Double>. No keys are hardcoded or ignored.
     */
    private fun parseExtraPays(extra: JSONObject?): Map<String, Double> {
        extra ?: return emptyMap()
        val map = mutableMapOf<String, Double>()
        val keys = extra.keys()
        while (keys.hasNext()) {
            val key   = keys.next()
            val value = extra.optDouble(key, 0.0)
            if (value != 0.0) map[key] = value   // skip zero-value keys to keep map clean
        }
        return map
    }
}
