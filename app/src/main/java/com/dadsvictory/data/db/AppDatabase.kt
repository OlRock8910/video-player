package com.dadsvictory.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SlipEntity::class,
        CravingEntity::class,
        CheckInEntity::class,
        JournalEntity::class,
        FavouriteVerseEntity::class,
        TriggerSelectionEntity::class,
        StoryAchievementEntity::class,
        FamilyMessageEntity::class,
        PlanTaskEntity::class,
        PlanCompletionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun slipDao(): SlipDao
    abstract fun cravingDao(): CravingDao
    abstract fun checkInDao(): CheckInDao
    abstract fun journalDao(): JournalDao
    abstract fun favouriteVerseDao(): FavouriteVerseDao
    abstract fun triggerDao(): TriggerDao
    abstract fun storyAchievementDao(): StoryAchievementDao
    abstract fun familyMessageDao(): FamilyMessageDao
    abstract fun planDao(): PlanDao

    companion object {
        private const val NAME = "dads_victory.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .build()
                .also { instance = it }
        }

        /** Used by "delete everything" in Settings. */
        fun destroy(context: Context) {
            synchronized(this) {
                instance?.close()
                instance = null
                context.applicationContext.deleteDatabase(NAME)
            }
        }
    }
}
