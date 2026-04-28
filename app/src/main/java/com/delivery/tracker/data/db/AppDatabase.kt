package com.delivery.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.delivery.tracker.data.model.*

@Database(
    entities = [
        Trip::class,
        SubOrder::class,
        DailySession::class,
        FuelEntry::class,
        ServiceEntry::class,
        TdsEntry::class,
        ServiceCycle::class
    ],
    version = 3,                     // bumped from 2 → 3
    exportSchema = false
)
@TypeConverters(MapTypeConverter::class)   // ← register the converter
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun subOrderDao(): SubOrderDao
    abstract fun sessionDao(): SessionDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun serviceCycleDao(): ServiceCycleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * Migration 2 → 3:
         * - Add extraPays TEXT column (stores JSON map of all extra pay types)
         * - Remove the old individual pay columns: incentivePay, tips, surgePay, otherPay
         *
         * SQLite doesn't support DROP COLUMN directly (before API 35), so we
         * recreate the trips table with the new schema.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new table with extraPays column
                db.execSQL("""
                    CREATE TABLE trips_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        restaurantName TEXT NOT NULL,
                        assignedTime TEXT NOT NULL,
                        orderPay REAL NOT NULL,
                        screenshotDistance REAL NOT NULL,
                        extraPays TEXT NOT NULL DEFAULT '{}',
                        dateMillis INTEGER NOT NULL,
                        servicecycleId INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 2. Copy existing rows — merge old pay fields into extraPays JSON
                //    We build a JSON string inline using SQLite string concatenation.
                db.execSQL("""
                    INSERT INTO trips_new 
                        (id, sessionId, restaurantName, assignedTime, orderPay,
                         screenshotDistance, extraPays, dateMillis, servicecycleId)
                    SELECT
                        id, sessionId, restaurantName, assignedTime, orderPay,
                        screenshotDistance,
                        '{"incentive_pay":' || CAST(incentivePay AS TEXT) ||
                        ',"customer_tip":'  || CAST(tips AS TEXT) ||
                        ',"surge_pay":'     || CAST(surgePay AS TEXT) ||
                        ',"other_pay":'     || CAST(otherPay AS TEXT) || '}',
                        dateMillis, servicecycleId
                    FROM trips
                """.trimIndent())

                // 3. Swap tables
                db.execSQL("DROP TABLE trips")
                db.execSQL("ALTER TABLE trips_new RENAME TO trips")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "delivery_tracker.db"
                )
                .addMigrations(MIGRATION_2_3)   // safe migration — existing data preserved
                .build().also { INSTANCE = it }
            }
        }
    }
}
