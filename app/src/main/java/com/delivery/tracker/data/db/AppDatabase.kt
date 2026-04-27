package com.delivery.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun subOrderDao(): SubOrderDao
    abstract fun sessionDao(): SessionDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun serviceCycleDao(): ServiceCycleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "delivery_tracker.db"
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
