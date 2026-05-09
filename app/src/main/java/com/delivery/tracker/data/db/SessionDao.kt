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

    @Query("SELECT MAX(endOdometer) FROM daily_sessions WHERE isEnded = 1 AND isRetroactive = 0")
    suspend fun getMaxEndOdometer(): Double?

    @Query("""
        UPDATE daily_sessions
        SET serviceCycleId = :cycleId
        WHERE serviceCycleId = 0
        AND startOdometer >= :cycleStartOdo
        AND (:cycleEndOdo = 0.0 OR startOdometer < :cycleEndOdo)
    """)
    suspend fun linkSessionsToCycle(cycleId: Long, cycleStartOdo: Double, cycleEndOdo: Double)

    @Query("SELECT * FROM daily_sessions WHERE serviceCycleId = 0 AND startOdometer >= :cycleStartOdo")
    suspend fun getUnlinkedSessionsAfterOdo(cycleStartOdo: Double): List<DailySession>

    @Delete
    suspend fun deleteSession(session: DailySession)

    @Query("DELETE FROM trips WHERE sessionId = :sessionId")
    suspend fun deleteTripsForSession(sessionId: Long)

    @Query("""
        SELECT SUM(endOdometer - startOdometer) 
        FROM daily_sessions 
        WHERE serviceCycleId = :cycleId 
        AND isEnded = 1 
        AND endOdometer > startOdometer
    """)
    suspend fun getTotalKmForCycle(cycleId: Long): Double?

    @Query("""
        SELECT MAX(endOdometer) FROM daily_sessions
        WHERE serviceCycleId = :cycleId
        AND isEnded = 1
        AND endOdometer > 0
    """)
    suspend fun getMaxEndOdometerForCycle(cycleId: Long): Double?

    @Query("DELETE FROM daily_sessions")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithId(session: DailySession): Long
}
