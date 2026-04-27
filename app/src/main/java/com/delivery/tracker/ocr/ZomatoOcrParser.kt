package com.delivery.tracker.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ZomatoOcrParser {

    private const val TAG = "ZomatoOcrParser"
    private const val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE"
    private const val GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
        "gemini-1.5-flash:generateContent?key=$GEMINI_API_KEY"

    fun parseWithGemini(bitmap: Bitmap): OcrResult {
        return try {
            val base64Image = bitmapToBase64(bitmap)
            val requestBody = buildRequestJson(base64Image)
            val responseText = callGeminiApi(requestBody)
            parseGeminiResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini OCR failed: ${e.message}")
            OcrResult()
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildRequestJson(base64Image: String): String {
        val prompt = """
            This is a Zomato delivery partner trip details screenshot.
            There may be ONE or MULTIPLE orders (Order 1, Order 2, etc).
            Each order has its own PICKUP section with a restaurant name
            and its own DROP section with a customer name.
            Different orders may have DIFFERENT restaurants.

            Extract all data and return ONLY this exact JSON, nothing else, no markdown:
            {
              "totalEarnings": numeric total earnings amount,
              "orderPay": numeric Order pay amount,
              "incentivePay": numeric Incentive pay (0 if absent),
              "tips": numeric Tips (0 if absent),
              "surgePay": numeric surge or rain surge pay (0 if absent),
              "isMultiOrder": true if more than one order exists,
              "orders": [
                {
                  "orderNumber": 1,
                  "restaurantName": "restaurant name from THIS order PICKUP section",
                  "dropLocationName": "customer name from THIS order DROP section",
                  "pickupDistanceKm": numeric distance under THIS order PICKUP (0 if missing),
                  "dropDistanceKm": numeric distance under THIS order DROP (0 if missing),
                  "orderAssignedTime": "time next to Order assigned for this order",
                  "orderPickedTime": "time next to Order picked for this order",
                  "orderDeliveredTime": "time next to Order delivered for this order"
                }
              ]
            }

            Critical rules:
            - Each order must have its OWN restaurantName from its own PICKUP section
            - If orders come from different restaurants use different names
            - Return ONLY raw JSON, no explanation, no backticks
        """.trimIndent()

        return JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0)
                put("maxOutputTokens", 1024)
            })
        }.toString()
    }

    private fun callGeminiApi(requestBody: String): String {
        val url = URL(GEMINI_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
        }
        connection.outputStream.use { it.write(requestBody.toByteArray()) }
        return connection.inputStream.bufferedReader().readText()
    }

    private fun parseGeminiResponse(responseText: String): OcrResult {
        Log.d(TAG, "Gemini response: $responseText")

        val root = JSONObject(responseText)
        val text = root
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        Log.d(TAG, "Parsed JSON: $text")
        val json = JSONObject(text)

        val ordersArray = json.optJSONArray("orders") ?: JSONArray()
        val subOrders = mutableListOf<SubOrderResult>()

        for (i in 0 until ordersArray.length()) {
            val o = ordersArray.getJSONObject(i)
            subOrders.add(
                SubOrderResult(
                    orderNumber        = o.optInt("orderNumber", i + 1),
                    restaurantName     = o.optString("restaurantName", ""),
                    dropLocationName   = o.optString("dropLocationName", ""),
                    pickupDistanceKm   = o.optDouble("pickupDistanceKm", 0.0),
                    dropDistanceKm     = o.optDouble("dropDistanceKm", 0.0),
                    orderAssignedTime  = o.optString("orderAssignedTime", ""),
                    orderPickedTime    = o.optString("orderPickedTime", ""),
                    orderDeliveredTime = o.optString("orderDeliveredTime", "")
                )
            )
        }

        val isMultiOrder = json.optBoolean("isMultiOrder", false)
        val totalDistance = subOrders.sumOf { it.totalDistanceKm }
        val firstTime = subOrders.firstOrNull()?.orderAssignedTime ?: ""

        val restaurantName = when {
            subOrders.size == 1 -> subOrders[0].restaurantName
            subOrders.size > 1 -> {
                val unique = subOrders.map { it.restaurantName }.distinct()
                if (unique.size == 1) unique[0]
                else "Multi-order"
            }
            else -> ""
        }

        return OcrResult(
            restaurantName = restaurantName,
            orderPay       = json.optDouble("orderPay", 0.0),
            incentivePay   = json.optDouble("incentivePay", 0.0),
            tips           = json.optDouble("tips", 0.0),
            surgePay       = json.optDouble("surgePay", 0.0),
            distance       = totalDistance,
            assignedTime   = firstTime,
            screenType     = ScreenType.TRIP_END,
            subOrders      = subOrders,
            isMultiOrder   = isMultiOrder
        )
    }

    fun parseWithRegex(rawText: String): OcrResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var restaurantName = ""
        for (i in lines.indices) {
            if (lines[i].contains("PICKUP", ignoreCase = true) && i + 1 < lines.size) {
                restaurantName = lines[i + 1].trim()
                break
            }
        }
        val orderPay = Regex("""(?i)order\s*pay\s*[₹]?\s*(\d+\.?\d*)""")
            .find(rawText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val incentivePay = Regex("""(?i)incentive\s*pay\s*[₹]?\s*(\d+\.?\d*)""")
            .find(rawText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val totalDistance = Regex("""(\d+\.?\d*)\s*km""")
            .findAll(rawText).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
        val assignedTime = Regex("""(?i)order\s*assigned\s+(\d{1,2}:\d{2}\s*(?:am|pm))""")
            .find(rawText)?.groupValues?.get(1) ?: ""
        return OcrResult(
            restaurantName = restaurantName,
            orderPay       = orderPay,
            incentivePay   = incentivePay,
            distance       = totalDistance,
            assignedTime   = assignedTime,
            screenType     = ScreenType.TRIP_END
        )
    }
}
