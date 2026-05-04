package com.delivery.tracker.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.delivery.tracker.data.model.DailySession

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: DailySession): Long

    @Update
    suspend fun update(session: DailySession)

    @Query("SELECT * FROM daily_sessions WHERE dateMillis BETWEEN :startMillis AND :endMillis ORDER BY dateMillis DESC")
    fun getSessionsByDateRange(startMillis: Long, endMillis: Long): LiveData<List<DailySession>>

    @Query("SELECT * FROM daily_sessions WHERE dateMillis BETWEEN :startMillis AND :endMillis LIMIT 1")
    suspend fun getSessionForDate(startMillis: Long, endMillis: Long): DailySession?

    @Query("SELECT * FROM daily_sessions WHERE dateMillis BETWEEN :startMillis AND :endMillis")
    suspend fun getSessionsForRangeOnce(startMillis: Long, endMillis: Long): List<DailySession>

    @Query("SELECT * FROM daily_sessions WHERE isEnded = 0 LIMIT 1")
    fun getActiveSession(): LiveData<DailySession?>

    @Query("SELECT * FROM daily_sessions WHERE isEnded = 0 LIMIT 1")
    suspend fun getActiveSessionOnce(): DailySession?

    @Query("SELECT * FROM daily_sessions ORDER BY dateMillis DESC")
    fun getAllSessions(): LiveData<List<DailySession>>

    @Query("SELECT MAX(endOdometer) FROM daily_sessions WHERE isEnded = 1")
    suspend fun getMaxEndOdometer(): Double?
}
