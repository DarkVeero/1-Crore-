package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserProfileEntity::class, DailyCountEntity::class, CustomCounterEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun dailyCountDao(): DailyCountDao
    abstract fun customCounterDao(): CustomCounterDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_counters` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `targetCount` INTEGER NOT NULL,
                        `currentCount` INTEGER NOT NULL,
                        `pendingSync` INTEGER NOT NULL,
                        `isManualEdit` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `user_profiles` ADD COLUMN `startedAtMillis` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `user_profiles` ADD COLUMN `themeMode` TEXT NOT NULL DEFAULT 'bw'"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // R4-C9: per-day target so History judges each day by the target
                // that was in force on that day. 0 = not recorded (old rows).
                db.execSQL(
                    "ALTER TABLE `daily_counts` ADD COLUMN `dailyTarget` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "one_crore_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
