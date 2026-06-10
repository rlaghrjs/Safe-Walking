package com.inhatc.safewalking

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SmombieRecord::class, SmombieSession::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smombieDao(): SmombieDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "safewalking_database"
                )
                    .allowMainThreadQueries() // 테스트 편의를 위해 메인스레드 쿼리 허용 (실무에선 비동기 권장)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}