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

    /**
     * Max end odometer from sessions strictly BEFORE the given date.
     * Used for retroactive date-aware validation.
     */
    suspend fun getMaxEndOdometerBefore(beforeDateMillis: Long): Double? =
        sessionDao.getMaxEndOdometerBefore(beforeDateMillis)

    /**
     * Min start odometer from sessions strictly AFTER the given date.
     * Used to ensure a retroactive entry doesn't exceed the next recorded day.
     */
    suspend fun getMinStartOdometerAfter(afterDateMillis: Long): Double? =
        sessionDao.getMinStartOdometerAfter(afterDateMillis)

    suspend fun getTotalKmForCycle(cycleId: Long): Double =
        sessionDao.getTotalKmForCycle(cycleId) ?: 0.0

    suspend fun linkSessionsToCycle(cycleId: Long, cycleStartOdo: Double, cycleEndOdo: Double) =
        sessionDao.linkSessionsToCycle(cycleId, cycleStartOdo, cycleEndOdo)

    suspend fun getMaxEndOdometerForCycle(cycleId: Long): Double =
        sessionDao.getMaxEndOdometerForCycle(cycleId) ?: 0.0

    suspend fun deleteSession(session: DailySession) =
        sessionDao.deleteSession(session)

    suspend fun deleteTripsForSession(sessionId: Long) =
        sessionDao.deleteTripsForSession(sessionId)
}
