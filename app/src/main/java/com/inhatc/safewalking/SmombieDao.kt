package com.inhatc.safewalking

import androidx.room.*
import androidx.lifecycle.LiveData

@Dao
interface SmombieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: SmombieSession)

    @Query("""
        SELECT date, 
               SUM(duration) as totalDangerDuration, 
               SUM(warningCount) as totalWarningCount 
        FROM smombie_sessions 
        GROUP BY date 
        ORDER BY date ASC 
        LIMIT 30
    """)
    fun getDailySummaries(): LiveData<List<SmombieRecord>>

    @Query("""
        SELECT date,
               SUM(duration) as totalDangerDuration,
               SUM(warningCount) as totalWarningCount
        FROM smombie_sessions
        GROUP BY date
        ORDER BY date DESC
        LIMIT 7
    """)
    fun getRecent7DaysRecords(): LiveData<List<SmombieRecord>>

    @Query("""
        SELECT strftime('%Y-W%W', date) as date,
               SUM(duration) as totalDangerDuration,
               SUM(warningCount) as totalWarningCount
        FROM smombie_sessions
        GROUP BY strftime('%Y-W%W', date)
        ORDER BY date DESC
        LIMIT 4
    """)
    fun getWeeklyRecords(): LiveData<List<SmombieRecord>>

    @Query("""
        SELECT strftime('%Y-%m', date) as date,
               SUM(duration) as totalDangerDuration,
               SUM(warningCount) as totalWarningCount
        FROM smombie_sessions
        GROUP BY strftime('%Y-%m', date)
        ORDER BY date DESC
        LIMIT 6
    """)
    fun getMonthlyRecords(): LiveData<List<SmombieRecord>>

    @Query("SELECT * FROM smombie_sessions WHERE date = :date ORDER BY id DESC")
    fun getSessionsByDate(date: String): List<SmombieSession>
}