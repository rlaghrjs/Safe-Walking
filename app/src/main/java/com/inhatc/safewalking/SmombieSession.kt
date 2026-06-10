package com.inhatc.safewalking

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "smombie_sessions")
data class SmombieSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,            // "2026-06-11" (그래프 그룹화용)
    val startTime: String,       // "14:25:01" (발생 시각 타임라인 표시용)
    val duration: Long,          // 지속 시간 (밀리초)
    val warningCount: Int,       // 해당 세션의 경고 횟수
    val riskLevel: String        // 세션 최고 위험도 ("주의", "위험" 등)
)