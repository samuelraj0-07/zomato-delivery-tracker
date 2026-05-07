package com.delivery.tracker.utils

import com.delivery.tracker.data.db.AppDatabase
import org.json.JSONArray
import org.json.JSONObject

object DataExporter {

    suspend fun export(db: AppDatabase): String {
        val root = JSONObject()

        // ── Trips ─────────────────────────────────────────────────────────
        val trips = db.tripDao().getTripsForRange(0L, Long.MAX_VALUE)
        val tripsArr = JSONArray()
        for (t in trips) {
            tripsArr.put(JSONObject().apply {
                put("id",                 t.id)
                put("sessionId",          t.sessionId)
                put("dateMillis",         t.dateMillis)
                put("restaurant",         t.restaurantName)
                put("assignedTime",       t.assignedTime)
                put("orderPay",           t.orderPay)
                put("screenshotDistance", t.screenshotDistance)
                put("servicecycleId",     t.servicecycleId)
            })
        }
        root.put("trips", tripsArr)

        // ── Sessions ──────────────────────────────────────────────────────
        val sessions = db.sessionDao().getSessionsForRangeOnce(0L, Long.MAX_VALUE)
        val sessArr = JSONArray()
        for (s in sessions) {
            sessArr.put(JSONObject().apply {
                put("id",              s.id)
                put("dateMillis",      s.dateMillis)
                put("startOdometer",   s.startOdometer)
                put("endOdometer",     s.endOdometer)
                put("isEnded",         s.isEnded)
                put("serviceCycleId",  s.serviceCycleId)
            })
        }
        root.put("sessions", sessArr)

        // ── Fuel entries ──────────────────────────────────────────────────
        val fuel = db.expenseDao().getFuelForExport()
        val fuelArr = JSONArray()
        for (f in fuel) {
            fuelArr.put(JSONObject().apply {
                put("id",               f.id)
                put("dateMillis",       f.dateMillis)
                put("odometerReading",  f.odometerReading)
                put("fuelPricePerLitre",f.fuelPricePerLitre)
                put("amountSpent",      f.amountSpent)
                put("serviceCycleId",   f.serviceCycleId)
            })
        }
        root.put("fuelEntries", fuelArr)

        // ── Service entries ───────────────────────────────────────────────
        val svc = db.expenseDao().getServiceForExport()
        val svcArr = JSONArray()
        for (s in svc) {
            svcArr.put(JSONObject().apply {
                put("id",              s.id)
                put("dateMillis",      s.dateMillis)
                put("odometerReading", s.odometerReading)
                put("amountSpent",     s.amountSpent)
                put("details",         s.details)
                put("serviceCycleId",  s.serviceCycleId)
            })
        }
        root.put("serviceEntries", svcArr)

        // ── Service cycles ────────────────────────────────────────────────
        val cycles = db.serviceCycleDao().getAllCyclesOnce()
        val cycArr = JSONArray()
        for (c in cycles) {
            cycArr.put(JSONObject().apply {
                put("id",              c.id)
                put("startOdometer",   c.startOdometer)
                put("endOdometer",     c.endOdometer)
                put("startDateMillis", c.startDateMillis)
                put("endDateMillis",   c.endDateMillis)
                put("isActive",        c.isActive)
            })
        }
        root.put("serviceCycles", cycArr)

        return root.toString(2)
    }

    suspend fun import(db: AppDatabase, json: String): Boolean {
        return try {
            JSONObject(json) // validate it is parseable JSON
            // Full restore logic can be added here later
            // For now export-only is the primary use case
            true
        } catch (e: Exception) {
            false
        }
    }
}
