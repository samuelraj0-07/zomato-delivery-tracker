package com.delivery.tracker.data.repository

import com.delivery.tracker.data.db.SessionDao
import com.delivery.tracker.data.model.DailySession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao
) {
    fun getActiveSession() = sessionDao.getActiveSession()
    fun getAllSessions() = sessionDao.getAllSessions()

    fun getSessionsByDateRange(start: Long, end: Long) =
        sessionDao.getSessionsByDateRange(start, end)

    suspend fun getActiveSessionOnce() = sessionDao.getActiveSessionOnce()

    suspend fun getSessionForDate(start: Long, end: Long) =
        sessionDao.getSessionForDate(start, end)

    suspend fun getSessionsForRangeOnce(start: Long, end: Long) =
        sessionDao.getSessionsForRangeOnce(start, end)

    suspend fun startSession(session: DailySession) =
        sessionDao.insert(session)

    suspend fun endSession(session: DailySession) =
        sessionDao.update(session.copy(isEnded = true))

    suspend fun updateSession(session: DailySession) =
        sessionDao.update(session)

    suspend fun getMaxEndOdometer(): Double? = sessionDao.getMaxEndOdometer()

    suspend fun getTotalKmForCycle(cycleId: Long): Double =
        sessionDao.getTotalKmForCycle(cycleId) ?: 0.0

    /**
     * Retroactively stamps serviceCycleId on any sessions that have
     * serviceCycleId=0 and whose odometer falls within this cycle's range.
     * cycleEndOdo = 0.0 means the cycle is still active (no upper bound).
     */
    suspend fun linkSessionsToCycle(cycleId: Long, cycleStartOdo: Double, cycleEndOdo: Double) =
        sessionDao.linkSessionsToCycle(cycleId, cycleStartOdo, cycleEndOdo)

    suspend fun deleteSession(session: com.delivery.tracker.data.model.DailySession) =
        sessionDao.deleteSession(session)

    suspend fun deleteTripsForSession(sessionId: Long) =
        sessionDao.deleteTripsForSession(sessionId)
}
