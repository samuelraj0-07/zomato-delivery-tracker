package com.delivery.tracker.ocr

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses JSON trip data from the Zomato partner app.
 *
 * Two entry points:
 *  - parse()     — legacy single-OcrResult (merges all objects). Used when
 *                  the JSON truly is one multi-restaurant trip.
 *  - parseAll()  — returns one OcrResult per JSON object. Use this when the
 *                  user pastes a list of separate trips at once.
 */
object JsonTripParser {

    /** Parse every element of the array as an independent trip. */
    fun parseAll(jsonText: String): List<OcrResult>? {
        return try {
            val array = JSONArray(jsonText.trim())
            if (array.length() == 0) return null
            (0 until array.length()).mapNotNull { i ->
                parseSingle(array.getJSONObject(i))
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /** Legacy: merge all objects into one OcrResult (multi-order trip). */
    fun parse(jsonText: String): OcrResult? {
        return try {
            val array = JSONArray(jsonText.trim())
            if (array.length() == 0) return null

            // If only one element, just parse it directly
            if (array.length() == 1) {
                return parseSingle(array.getJSONObject(0))
            }

            val subOrders         = mutableListOf<SubOrderResult>()
            var totalOrderPay     = 0.0
            var totalDistance     = 0.0
            val combinedExtraPays = mutableMapOf<String, Double>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val restaurantName = obj.optString("restaurant_name", "")
                val assignedTime   = obj.optString("order_assigned_time", "")
                val orderPay       = obj.optDouble("order_pay", 0.0)
                val distanceKm     = if (obj.isNull("total_distance_km")) 0.0
                                     else obj.optDouble("total_distance_km", 0.0)
                val extras = parseExtraPays(
                    if (obj.has("extra_pay") && !obj.isNull("extra_pay"))
                        obj.getJSONObject("extra_pay") else null
                )

                totalOrderPay += orderPay
                totalDistance += distanceKm
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

            val restaurantName = subOrders.map { it.restaurantName }
                .filter { it.isNotEmpty() }.distinct()
                .let { if (it.size == 1) it[0] else "Multi-order" }

            OcrResult(
                restaurantName = restaurantName,
                assignedTime   = subOrders.firstOrNull()?.orderAssignedTime ?: "",
                orderPay       = totalOrderPay,
                distance       = totalDistance,
                extraPays      = combinedExtraPays,
                screenType     = ScreenType.TRIP_END,
                subOrders      = subOrders,
                isMultiOrder   = true
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Parse a single JSON object into one OcrResult. */
    private fun parseSingle(obj: JSONObject): OcrResult? {
        return try {
            val restaurantName = obj.optString("restaurant_name", "")
            val assignedTime   = obj.optString("order_assigned_time", "")
            val orderPay       = obj.optDouble("order_pay", 0.0)
            val distanceKm     = if (obj.isNull("total_distance_km")) 0.0
                                 else obj.optDouble("total_distance_km", 0.0)
            val extras = parseExtraPays(
                if (obj.has("extra_pay") && !obj.isNull("extra_pay"))
                    obj.getJSONObject("extra_pay") else null
            )
            OcrResult(
                restaurantName = restaurantName,
                assignedTime   = assignedTime,
                orderPay       = orderPay,
                distance       = distanceKm,
                extraPays      = extras,
                screenType     = ScreenType.TRIP_END,
                subOrders      = emptyList(),
                isMultiOrder   = false
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseExtraPays(extra: JSONObject?): Map<String, Double> {
        extra ?: return emptyMap()
        val map = mutableMapOf<String, Double>()
        val keys = extra.keys()
        while (keys.hasNext()) {
            val key   = keys.next()
            val value = extra.optDouble(key, 0.0)
            if (value != 0.0) map[key] = value
        }
        return map
    }
}
