package com.delivery.tracker.utils

import com.delivery.tracker.data.db.AppDatabase
import com.delivery.tracker.data.model.*
import org.json.JSONArray
import org.json.JSONObject

object DataExporter {

    suspend fun export(db: AppDatabase): String {
        val root = JSONObject()

        // ── Trips ─────────────────────────────────────────────────────────
        val trips = db.tripDao().getTripsForRange(0L, Long.MAX_VALUE)
        val tripsArr = JSONArray()
        for (t in trips) {
            // Serialize extraPays map as a nested JSON object
            val extrasObj = JSONObject()
            t.extraPays.forEach { (k, v) -> extrasObj.put(k, v) }
            tripsArr.put(JSONObject().apply {
                put("id",                 t.id)
                put("sessionId",          t.sessionId)
                put("dateMillis",         t.dateMillis)
                put("restaurantName",     t.restaurantName)
                put("assignedTime",       t.assignedTime)
                put("orderPay",           t.orderPay)
                put("screenshotDistance", t.screenshotDistance)
                put("extraPays",          extrasObj)
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
                put("isRetroactive",   s.isRetroactive)
                put("serviceCycleId",  s.serviceCycleId)
            })
        }
        root.put("sessions", sessArr)

        // ── Fuel entries ──────────────────────────────────────────────────
        val fuel = db.expenseDao().getFuelForExport()
        val fuelArr = JSONArray()
        for (f in fuel) {
            fuelArr.put(JSONObject().apply {
                put("id",                f.id)
                put("dateMillis",        f.dateMillis)
                put("odometerReading",   f.odometerReading)
                put("fuelPricePerLitre", f.fuelPricePerLitre)
                put("amountSpent",       f.amountSpent)
                put("serviceCycleId",    f.serviceCycleId)
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

        // ── TDS entries ───────────────────────────────────────────────────
        val tdsEntries = db.expenseDao().getTdsForExport()
        val tdsArr = JSONArray()
        for (t in tdsEntries) {
            tdsArr.put(JSONObject().apply {
                put("id",              t.id)
                put("weekLabel",       t.weekLabel)
                put("weekStartMillis", t.weekStartMillis)
                put("weekEndMillis",   t.weekEndMillis)
                put("amount",          t.amount)
                put("dateMillis",      t.dateMillis)
            })
        }
        root.put("tdsEntries", tdsArr)

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

    /**
     * Issue 4: Full restore — clears existing data and re-inserts everything
     * from the backup JSON. Preserves original IDs so all foreign-key links
     * (sessionId, servicecycleId) are restored correctly.
     */
    suspend fun import(db: AppDatabase, json: String): Boolean {
        return try {
            val root = JSONObject(json)

            // ── 1. Clear existing data (order matters for FK constraints) ──
            db.tripDao().deleteAll()
            db.subOrderDao().deleteAll()
            db.sessionDao().deleteAll()
            db.expenseDao().deleteAllFuel()
            db.expenseDao().deleteAllService()
            db.expenseDao().deleteAllTds()
            db.serviceCycleDao().deleteAll()

            // ── 2. Re-insert service cycles first (trips reference them) ───
            val cyclesArr = root.optJSONArray("serviceCycles") ?: JSONArray()
            for (i in 0 until cyclesArr.length()) {
                val j = cyclesArr.getJSONObject(i)
                db.serviceCycleDao().insertWithId(
                    ServiceCycle(
                        id             = j.getLong("id"),
                        startOdometer  = j.getDouble("startOdometer"),
                        endOdometer    = j.getDouble("endOdometer"),
                        startDateMillis = j.getLong("startDateMillis"),
                        endDateMillis  = j.optLong("endDateMillis", 0L),
                        isActive       = j.getBoolean("isActive")
                    )
                )
            }

            // ── 3. Re-insert sessions ──────────────────────────────────────
            val sessArr = root.optJSONArray("sessions") ?: JSONArray()
            for (i in 0 until sessArr.length()) {
                val j = sessArr.getJSONObject(i)
                db.sessionDao().insertWithId(
                    DailySession(
                        id             = j.getLong("id"),
                        dateMillis     = j.getLong("dateMillis"),
                        startOdometer  = j.getDouble("startOdometer"),
                        endOdometer    = j.getDouble("endOdometer"),
                        isEnded        = j.getBoolean("isEnded"),
                        isRetroactive  = j.optBoolean("isRetroactive", false),
                        serviceCycleId = j.optLong("serviceCycleId", 0L)
                    )
                )
            }

            // ── 4. Re-insert trips ─────────────────────────────────────────
            val tripsArr = root.optJSONArray("trips") ?: JSONArray()
            for (i in 0 until tripsArr.length()) {
                val j = tripsArr.getJSONObject(i)
                // Parse extraPays JSON object back to Map<String, Double>
                val extrasMap = mutableMapOf<String, Double>()
                val extrasObj = j.optJSONObject("extraPays")
                extrasObj?.keys()?.forEach { key ->
                    extrasMap[key] = extrasObj.getDouble(key)
                }
                db.tripDao().insertWithId(
                    Trip(
                        id                 = j.getLong("id"),
                        sessionId          = j.getLong("sessionId"),
                        dateMillis         = j.getLong("dateMillis"),
                        restaurantName     = j.optString("restaurantName", j.optString("restaurant", "")),
                        assignedTime       = j.getString("assignedTime"),
                        orderPay           = j.getDouble("orderPay"),
                        screenshotDistance = j.getDouble("screenshotDistance"),
                        extraPays          = extrasMap,
                        servicecycleId     = j.optLong("servicecycleId", 0L)
                    )
                )
            }

            // ── 5. Re-insert fuel entries ──────────────────────────────────
            val fuelArr = root.optJSONArray("fuelEntries") ?: JSONArray()
            for (i in 0 until fuelArr.length()) {
                val j = fuelArr.getJSONObject(i)
                db.expenseDao().insertFuelWithId(
                    FuelEntry(
                        id                = j.getLong("id"),
                        dateMillis        = j.getLong("dateMillis"),
                        odometerReading   = j.getDouble("odometerReading"),
                        fuelPricePerLitre = j.getDouble("fuelPricePerLitre"),
                        amountSpent       = j.getDouble("amountSpent"),
                        serviceCycleId    = j.optLong("serviceCycleId", 0L)
                    )
                )
            }

            // ── 6. Re-insert service entries ───────────────────────────────
            val svcArr = root.optJSONArray("serviceEntries") ?: JSONArray()
            for (i in 0 until svcArr.length()) {
                val j = svcArr.getJSONObject(i)
                db.expenseDao().insertServiceWithId(
                    ServiceEntry(
                        id              = j.getLong("id"),
                        dateMillis      = j.getLong("dateMillis"),
                        odometerReading = j.getDouble("odometerReading"),
                        amountSpent     = j.getDouble("amountSpent"),
                        details         = j.optString("details", ""),
                        serviceCycleId  = j.optLong("serviceCycleId", 0L)
                    )
                )
            }

            // ── 7. Re-insert TDS entries ───────────────────────────────────
            val tdsArr = root.optJSONArray("tdsEntries") ?: JSONArray()
            for (i in 0 until tdsArr.length()) {
                val j = tdsArr.getJSONObject(i)
                db.expenseDao().insertTdsWithId(
                    TdsEntry(
                        id              = j.getLong("id"),
                        weekLabel       = j.getString("weekLabel"),
                        weekStartMillis = j.getLong("weekStartMillis"),
                        weekEndMillis   = j.getLong("weekEndMillis"),
                        amount          = j.getDouble("amount"),
                        dateMillis      = j.optLong("dateMillis", System.currentTimeMillis())
                    )
                )
            }

            true
        } catch (e: Exception) {
            android.util.Log.e("DataExporter", "Import failed", e)
            false
        }
    }
}
