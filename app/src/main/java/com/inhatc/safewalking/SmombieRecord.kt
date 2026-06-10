package com.inhatc.safewalking

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "smombie_records")
data class SmombieRecord(
    @PrimaryKey val date: String,          // 저장 날짜 (예: "2026-06-10")
    val totalDangerDuration: Long,        // 총 위험 노출 시간 (밀리초 단위)
    val totalWarningCount: Int             // 총 경고 횟수
)