package com.dadsvictory.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dadsvictory.domain.CravingEvent
import com.dadsvictory.domain.CravingOutcome
import com.dadsvictory.domain.CheckIn
import com.dadsvictory.domain.Slip
import com.dadsvictory.domain.Substance

/**
 * Room entities.
 *
 * Substances are stored as two boolean columns rather than a converted set, so the
 * schema stays queryable and there is one less type converter to go wrong. The
 * mapping to and from the pure domain types lives here.
 */

@Entity(tableName = "slips")
data class SlipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val nicotine: Boolean,
    val alcohol: Boolean,
    val triggerId: String?,
    val reflection: String?,
    val nextChange: String?,
) {
    fun toDomain(): Slip = Slip(
        id = id,
        atMillis = atMillis,
        substances = buildSet {
            if (nicotine) add(Substance.NICOTINE)
            if (alcohol) add(Substance.ALCOHOL)
        },
        triggerId = triggerId,
        reflection = reflection,
        nextChange = nextChange,
    )

    companion object {
        fun from(slip: Slip): SlipEntity = SlipEntity(
            id = slip.id,
            atMillis = slip.atMillis,
            nicotine = Substance.NICOTINE in slip.substances,
            alcohol = Substance.ALCOHOL in slip.substances,
            triggerId = slip.triggerId,
            reflection = slip.reflection,
            nextChange = slip.nextChange,
        )
    }
}

@Entity(tableName = "cravings")
data class CravingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val outcome: String,
    val secondsHeld: Int,
    val triggerId: String?,
) {
    fun toDomain(): CravingEvent = CravingEvent(
        id = id,
        atMillis = atMillis,
        outcome = CravingOutcome.fromId(outcome),
        secondsHeld = secondsHeld,
        triggerId = triggerId,
    )
}

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey val epochDay: Long,
    val moodScore: Int,
    val cravingLevel: Int,
    val stressLevel: Int,
    val stayedNicotineFree: Boolean?,
    val stayedAlcoholFree: Boolean?,
    val note: String,
) {
    fun toDomain(): CheckIn = CheckIn(
        epochDay = epochDay,
        moodScore = moodScore,
        cravingLevel = cravingLevel,
        stressLevel = stressLevel,
        stayedNicotineFree = stayedNicotineFree,
        stayedAlcoholFree = stayedAlcoholFree,
        note = note,
    )

    companion object {
        fun from(checkIn: CheckIn): CheckInEntity = CheckInEntity(
            epochDay = checkIn.epochDay,
            moodScore = checkIn.moodScore,
            cravingLevel = checkIn.cravingLevel,
            stressLevel = checkIn.stressLevel,
            stayedNicotineFree = checkIn.stayedNicotineFree,
            stayedAlcoholFree = checkIn.stayedAlcoholFree,
            note = checkIn.note,
        )
    }
}

@Entity(tableName = "journal")
data class JournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val prompt: String,
    val body: String,
)

@Entity(tableName = "favourite_verses")
data class FavouriteVerseEntity(
    @PrimaryKey val reference: String,
    val savedAtMillis: Long,
)

@Entity(tableName = "selected_triggers")
data class TriggerSelectionEntity(
    @PrimaryKey val triggerId: String,
)

@Entity(tableName = "story_achievements")
data class StoryAchievementEntity(
    @PrimaryKey val achievementId: String,
    val unlockedAtMillis: Long,
)

@Entity(tableName = "family_messages")
data class FamilyMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAtMillis: Long,
)

@Entity(tableName = "plan_tasks")
data class PlanTaskEntity(
    @PrimaryKey val taskId: String,
    val slotId: String,
    val title: String,
    val sortOrder: Int,
    val enabled: Boolean,
)

/** One row per task he ticked on a given day. Absent means not done. */
@Entity(tableName = "plan_completions", primaryKeys = ["epochDay", "taskId"])
data class PlanCompletionEntity(
    val epochDay: Long,
    val taskId: String,
)
