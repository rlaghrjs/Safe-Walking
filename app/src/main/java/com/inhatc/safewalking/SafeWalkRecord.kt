package com.inhatc.safewalking

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "safe_walk_records")
data class SafeWalkRecord(
    @PrimaryKey val date: String,
    val totalSafeDuration: Long = 0L
)