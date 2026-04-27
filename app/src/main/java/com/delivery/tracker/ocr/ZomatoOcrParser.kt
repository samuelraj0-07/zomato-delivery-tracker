package com.delivery.tracker.ocr

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object ZomatoOcrParser {

    private const val TAG = "ZomatoOcrParser"

    // ── paste your Gemini API key here ──
    private const val GEMINI_API_KEY = "AIzaSyBzKf_574PadPq0K3FDn3xlKcgtN1IDjoM"
    private const val GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
        "gemini-1.5-flash:generateContent?key=$GEMINI_API_KEY"

    // Called from background thread / coroutine
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
            This is a Zomato delivery app trip details screenshot.
            Extract ONLY these fields and return ONLY a valid JSON object, nothing else:
            {
              "restaurantName": "exact restaurant name from PICKUP section",
              "orderPay": numeric value of Order pay (no currency symbol),
              "incentivePay": numeric value of Incentive pay (0 if not present),
              "tips": numeric value of Tips (0 if not present),
              "surgePay": numeric value of any surge or rain surge pay (0 if not present),
              "totalEarnings": numeric value of Total earnings,
              "pickupDistance": numeric value of Distance travelled under PICKUP section,
              "dropDistance": numeric value of Distance travelled under DROP section,
              "totalDistance": sum of pickup and drop distances,
              "orderAssignedTime": time shown next to Order assigned,
              "screenType": "TRIP_END"
            }
            Return ONLY the JSON. No explanation, no markdown, no backticks.
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
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0)
                put("maxOutputTokens", 512)
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
        Log.d(TAG, "Gemini raw response: $responseText")

        val root = JSONObject(responseText)
        val text = root
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()

        Log.d(TAG, "Gemini extracted text: $text")

        // Clean any accidental markdown backticks
        val clean = text
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = JSONObject(clean)

        return OcrResult(
            restaurantName  = json.optString("restaurantName", ""),
            orderPay        = json.optDouble("orderPay", 0.0),
            incentivePay    = json.optDouble("incentivePay", 0.0),
            tips            = json.optDouble("tips", 0.0),
            surgePay        = json.optDouble("surgePay", 0.0),
            distance        = json.optDouble("totalDistance", 0.0),
            assignedTime    = json.optString("orderAssignedTime", ""),
            screenType      = ScreenType.TRIP_END
        )
    }

    // ── Fallback regex parser (used if Gemini fails) ──
    fun parseWithRegex(rawText: String): OcrResult {
        val text = rawText.lowercase()
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Restaurant name → bold line right after "PICKUP"
        var restaurantName = ""
        for (i in lines.indices) {
            if (lines[i].contains("PICKUP", ignoreCase = true) && i + 1 < lines.size) {
                restaurantName = lines[i + 1].trim()
                break
            }
        }

        // Order pay
        val orderPayRegex = Regex("""(?i)order\s*pay\s*[₹]?\s*(\d+\.?\d*)""")
        val orderPay = orderPayRegex.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        // Incentive pay
        val incentiveRegex = Regex("""(?i)incentive\s*pay\s*[₹]?\s*(\d+\.?\d*)""")
        val incentivePay = incentiveRegex.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        // All distances → sum them
        val distRegex = Regex("""(\d+\.?\d*)\s*km""")
        val distances = distRegex.findAll(rawText).map {
            it.groupValues[1].toDoubleOrNull() ?: 0.0
        }.toList()
        val totalDistance = distances.sum()

        // Order assigned time
        val timeRegex = Regex("""(?i)order\s*assigned\s+(\d{1,2}:\d{2}\s*(?:am|pm))""")
        val assignedTime = timeRegex.find(rawText)?.groupValues?.get(1) ?: ""

        return OcrResult(
            restaurantName = restaurantName,
            orderPay = orderPay,
            incentivePay = incentivePay,
            distance = totalDistance,
            assignedTime = assignedTime,
            screenType = ScreenType.TRIP_END
        )
    }
}
