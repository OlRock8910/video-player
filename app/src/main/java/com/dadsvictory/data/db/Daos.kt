package com.dadsvictory.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SlipDao {
    @Query("SELECT * FROM slips ORDER BY atMillis ASC")
    fun observeAll(): Flow<List<SlipEntity>>

    @Query("SELECT * FROM slips ORDER BY atMillis ASC")
    suspend fun getAll(): List<SlipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(slip: SlipEntity): Long

    @Delete
    suspend fun delete(slip: SlipEntity)

    @Query("DELETE FROM slips")
    suspend fun clear()
}

@Dao
interface CravingDao {
    @Query("SELECT * FROM cravings ORDER BY atMillis DESC")
    fun observeAll(): Flow<List<CravingEntity>>

    @Query("SELECT COUNT(*) FROM cravings WHERE outcome = 'won'")
    fun observeDefeatedCount(): Flow<Int>


    @Query("SELECT * FROM cravings ORDER BY atMillis DESC")
    suspend fun getAll(): List<CravingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(craving: CravingEntity): Long

    @Query("DELETE FROM cravings")
    suspend fun clear()
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<CheckInEntity>>


    @Query("SELECT * FROM check_ins WHERE epochDay = :epochDay")
    fun observeForDay(epochDay: Long): Flow<CheckInEntity?>

    @Query("SELECT * FROM check_ins WHERE epochDay = :epochDay")
    suspend fun getForDay(epochDay: Long): CheckInEntity?

    @Query("SELECT * FROM check_ins ORDER BY epochDay ASC")
    suspend fun getAll(): List<CheckInEntity>


    @Upsert
    suspend fun upsert(checkIn: CheckInEntity)

    @Query("DELETE FROM check_ins")
    suspend fun clear()
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal ORDER BY createdAtMillis ASC")
    suspend fun getAll(): List<JournalEntity>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntity): Long

    @Query("UPDATE journal SET prompt = :prompt, body = :body WHERE id = :id")
    suspend fun update(id: Long, prompt: String, body: String)

    @Delete
    suspend fun delete(entry: JournalEntity)

    @Query("DELETE FROM journal")
    suspend fun clear()
}

@Dao
interface FavouriteVerseDao {
    @Query("SELECT * FROM favourite_verses ORDER BY savedAtMillis DESC")
    fun observeAll(): Flow<List<FavouriteVerseEntity>>

    @Query("SELECT reference FROM favourite_verses")
    suspend fun getReferences(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(verse: FavouriteVerseEntity)

    @Query("DELETE FROM favourite_verses WHERE reference = :reference")
    suspend fun remove(reference: String)

    @Query("DELETE FROM favourite_verses")
    suspend fun clear()
}

@Dao
interface TriggerDao {
    @Query("SELECT * FROM selected_triggers")
    fun observeAll(): Flow<List<TriggerSelectionEntity>>

    @Query("SELECT triggerId FROM selected_triggers")
    suspend fun getIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(selection: TriggerSelectionEntity)

    @Query("DELETE FROM selected_triggers WHERE triggerId = :triggerId")
    suspend fun remove(triggerId: String)

    @Query("DELETE FROM selected_triggers")
    suspend fun clear()
}

@Dao
interface StoryAchievementDao {
    @Query("SELECT * FROM story_achievements")
    fun observeAll(): Flow<List<StoryAchievementEntity>>

    @Query("SELECT achievementId FROM story_achievements")
    suspend fun getIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(achievement: StoryAchievementEntity)

    @Query("DELETE FROM story_achievements WHERE achievementId = :achievementId")
    suspend fun remove(achievementId: String)

    @Query("DELETE FROM story_achievements")
    suspend fun clear()
}

@Dao
interface FamilyMessageDao {
    @Query("SELECT * FROM family_messages ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<FamilyMessageEntity>>

    @Query("SELECT * FROM family_messages ORDER BY createdAtMillis ASC")
    suspend fun getAll(): List<FamilyMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: FamilyMessageEntity): Long

    @Delete
    suspend fun delete(message: FamilyMessageEntity)

    @Query("DELETE FROM family_messages")
    suspend fun clear()
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_tasks ORDER BY sortOrder ASC")
    fun observeTasks(): Flow<List<PlanTaskEntity>>

    @Query("SELECT * FROM plan_tasks ORDER BY sortOrder ASC")
    suspend fun getTasks(): List<PlanTaskEntity>

    @Query("SELECT COUNT(*) FROM plan_tasks")
    suspend fun taskCount(): Int

    @Upsert
    suspend fun upsertTask(task: PlanTaskEntity)

    @Query("DELETE FROM plan_tasks WHERE taskId = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("SELECT * FROM plan_completions WHERE epochDay = :epochDay")
    fun observeCompletions(epochDay: Long): Flow<List<PlanCompletionEntity>>

    @Query("SELECT * FROM plan_completions")
    suspend fun getAllCompletions(): List<PlanCompletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markDone(completion: PlanCompletionEntity)

    @Query("DELETE FROM plan_completions WHERE epochDay = :epochDay AND taskId = :taskId")
    suspend fun markNotDone(epochDay: Long, taskId: String)

    @Query("DELETE FROM plan_tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM plan_completions")
    suspend fun clearCompletions()
}
