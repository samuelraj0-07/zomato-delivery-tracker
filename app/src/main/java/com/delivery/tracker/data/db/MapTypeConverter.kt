package com.delivery.tracker.data.db

import androidx.room.TypeConverter
import org.json.JSONObject

/**
 * Converts Map<String, Double> ↔ JSON string for Room storage.
 * e.g. {"incentive_pay":5.0,"rain_bonus":15.0} stored as a single TEXT column.
 */
class MapTypeConverter {

    @TypeConverter
    fun fromMap(map: Map<String, Double>): String {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    @TypeConverter
    fun toMap(json: String): Map<String, Double> {
        if (json.isBlank() || json == "{}") return emptyMap()
        val obj = JSONObject(json)
        val map = mutableMapOf<String, Double>()
        obj.keys().forEach { key -> map[key] = obj.optDouble(key, 0.0) }
        return map
    }
}
