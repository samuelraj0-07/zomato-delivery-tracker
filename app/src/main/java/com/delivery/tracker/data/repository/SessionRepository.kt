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
}
