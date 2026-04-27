package com.delivery.tracker.ocr

object ZomatoOcrParser {

    fun parse(rawText: String): OcrResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val fullText = rawText.lowercase()

        val screenType = detectScreenType(fullText)
        val restaurantName = extractRestaurantName(lines, fullText)
        val assignedTime = extractTime(lines, fullText)
        val orderPay = extractOrderPay(lines, fullText)
        val distance = extractDistance(lines, fullText)
        val incentivePay = extractIncentive(lines, fullText)
        val tips = extractTips(lines, fullText)
        val surgePay = extractSurge(lines, fullText)
        val otherPay = extractOther(lines, fullText)

        return OcrResult(
            restaurantName = restaurantName,
            assignedTime = assignedTime,
            orderPay = orderPay,
            distance = distance,
            incentivePay = incentivePay,
            tips = tips,
            surgePay = surgePay,
            otherPay = otherPay,
            screenType = screenType
        )
    }

    private fun detectScreenType(text: String): ScreenType {
        return when {
            text.contains("order delivered") ||
            text.contains("delivered") ||
            text.contains("trip completed") ||
            text.contains("you earned") -> ScreenType.TRIP_END

            text.contains("pick up") ||
            text.contains("pickup") ||
            text.contains("head to") ||
            text.contains("restaurant") ||
            text.contains("accept") -> ScreenType.TRIP_START

            else -> ScreenType.UNKNOWN
        }
    }

    private fun extractRestaurantName(lines: List<String>, fullText: String): String {
        // Zomato shows restaurant name prominently near top
        // Look for line after "Pick up from" or "Head to" or before address keywords
        val pickupPatterns = listOf(
            Regex("(?i)pick\\s*up\\s*from[:\\s]+(.+)"),
            Regex("(?i)head\\s*to[:\\s]+(.+)"),
            Regex("(?i)collect\\s*from[:\\s]+(.+)")
        )
        for (pattern in pickupPatterns) {
            val match = pattern.find(fullText)
            if (match != null) {
                return match.groupValues[1].trim()
                    .split("\n").first().trim()
                    .capitalizeWords()
            }
        }
        // Fallback: look for a line that looks like a restaurant name
        // (not a number, not too long, not an address)
        for (line in lines) {
            if (line.length in 3..40 &&
                !line.contains("₹") &&
                !line.contains("km") &&
                !line.contains("min") &&
                !line.any { it.isDigit() } &&
                line[0].isUpperCase()
            ) {
                return line.trim()
            }
        }
        return ""
    }

    private fun extractTime(lines: List<String>, fullText: String): String {
        // Match time formats: 12:30 PM, 12:30, 1:05 pm
        val timeRegex = Regex("(\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?)")
        return timeRegex.find(fullText)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractOrderPay(lines: List<String>, fullText: String): Double {
        // Zomato labels: "Order Pay", "Delivery Pay", "Earnings", "You earned"
        val patterns = listOf(
            Regex("(?i)order\\s*pay[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)delivery\\s*pay[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)you\\s*earned[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)base\\s*pay[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)trip\\s*pay[^\\d]*(\\d+\\.?\\d*)"),
            Regex("₹\\s*(\\d+\\.?\\d*)")  // fallback first ₹ amount
        )
        for (pattern in patterns) {
            val match = pattern.find(fullText)
            if (match != null) {
                return match.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }
        return 0.0
    }

    private fun extractDistance(lines: List<String>, fullText: String): Double {
        // Match: 3.2 km, 3.2km, 3 km
        val distRegex = Regex("(\\d+\\.?\\d*)\\s*km")
        return distRegex.find(fullText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun extractIncentive(lines: List<String>, fullText: String): Double {
        val patterns = listOf(
            Regex("(?i)incentive[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)long\\s*distance[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)return\\s*pay[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)bonus[^\\d]*(\\d+\\.?\\d*)")
        )
        var total = 0.0
        for (pattern in patterns) {
            pattern.find(fullText)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                total += it
            }
        }
        return total
    }

    private fun extractTips(lines: List<String>, fullText: String): Double {
        val tipRegex = Regex("(?i)tip[^\\d]*(\\d+\\.?\\d*)")
        return tipRegex.find(fullText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun extractSurge(lines: List<String>, fullText: String): Double {
        val patterns = listOf(
            Regex("(?i)surge[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)rain\\s*surge[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)peak[^\\d]*(\\d+\\.?\\d*)")
        )
        var total = 0.0
        for (pattern in patterns) {
            pattern.find(fullText)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                total += it
            }
        }
        return total
    }

    private fun extractOther(lines: List<String>, fullText: String): Double {
        val patterns = listOf(
            Regex("(?i)additional[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)extra[^\\d]*(\\d+\\.?\\d*)"),
            Regex("(?i)special[^\\d]*(\\d+\\.?\\d*)")
        )
        var total = 0.0
        for (pattern in patterns) {
            pattern.find(fullText)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                total += it
            }
        }
        return total
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}
