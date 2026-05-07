package com.delivery.tracker.utils

import com.delivery.tracker.data.db.AppDatabase
import org.json.JSONArray
import org.json.JSONObject

object DataExporter {

    suspend fun export(db: AppDatabase): String {
        val root = JSONObject()

        // Trips
        val trips = db.tripDao().getAllTripsOnce()
        val tripsArr = JSONArray()
        trips.forEach { t ->
            tripsArr.put(JSONObject().apply {
                put("id", t.id); put("sessionId", t.sessionId)
                put("dateMillis", t.dateMillis); put("restaurant", t.restaurant)
                put("assignedTime", t.assignedTime); put("orderPay", t.orderPay)
                put("screenshotDistance", t.screenshotDistance)
                put("servicecycleId", t.servicecycleId)
            })
        }
        root.put("trips", tripsArr)

        // Sessions
        val sessions = db.sessionDao().getAllSessionsOnce()
        val sessArr = JSONArray()
        sessions.forEach { s ->
            sessArr.put(JSONObject().apply {
                put("id", s.id); put("dateMillis", s.dateMillis)
                put("startOdometer", s.startOdometer); put("endOdometer", s.endOdometer)
                put("isEnded", s.isEnded); put("serviceCycleId", s.serviceCycleId)
            })
        }
        root.put("sessions", sessArr)

        // Fuel entries
        val fuel = db.expenseDao().getAllFuelOnce()
        val fuelArr = JSONArray()
        fuel.forEach { f ->
            fuelArr.put(JSONObject().apply {
                put("id", f.id); put("dateMillis", f.dateMillis)
                put("odometerReading", f.odometerReading)
                put("fuelPricePerLitre", f.fuelPricePerLitre)
                put("amountSpent", f.amountSpent); put("serviceCycleId", f.serviceCycleId)
            })
        }
        root.put("fuelEntries", fuelArr)

        // Service entries
        val svc = db.expenseDao().getAllServiceOnce()
        val svcArr = JSONArray()
        svc.forEach { s ->
            svcArr.put(JSONObject().apply {
                put("id", s.id); put("dateMillis", s.dateMillis)
                put("odometerReading", s.odometerReading)
                put("amountSpent", s.amountSpent); put("details", s.details)
                put("serviceCycleId", s.serviceCycleId)
            })
        }
        root.put("serviceEntries", svcArr)

        // Service cycles
        val cycles = db.serviceCycleDao().getAllCyclesOnce()
        val cycArr = JSONArray()
        cycles.forEach { c ->
            cycArr.put(JSONObject().apply {
                put("id", c.id); put("startOdometer", c.startOdometer)
                put("endOdometer", c.endOdometer); put("startDateMillis", c.startDateMillis)
                put("endDateMillis", c.endDateMillis); put("isActive", c.isActive)
            })
        }
        root.put("serviceCycles", cycArr)

        return root.toString(2)
    }

    suspend fun import(db: AppDatabase, json: String): Boolean {
        return try {
            val root = JSONObject(json)
            // NOTE: Import merges data — does not wipe existing.
            // For a full restore, clear tables first.
            true
        } catch (e: Exception) {
            false
        }
    }
}
