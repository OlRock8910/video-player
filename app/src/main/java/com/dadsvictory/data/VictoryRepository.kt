package com.dadsvictory.data

import android.content.Context
import com.dadsvictory.data.db.AppDatabase
import com.dadsvictory.data.db.CheckInEntity
import com.dadsvictory.data.db.CravingEntity
import com.dadsvictory.data.db.FamilyMessageEntity
import com.dadsvictory.data.db.FavouriteVerseEntity
import com.dadsvictory.data.db.JournalEntity
import com.dadsvictory.data.db.PlanCompletionEntity
import com.dadsvictory.data.db.PlanTaskEntity
import com.dadsvictory.data.db.SlipEntity
import com.dadsvictory.data.db.StoryAchievementEntity
import com.dadsvictory.data.db.TriggerSelectionEntity
import com.dadsvictory.data.local.PhotoStore
import com.dadsvictory.data.prefs.AppSettings
import com.dadsvictory.data.prefs.SettingsStore
import com.dadsvictory.domain.CheckIn
import com.dadsvictory.domain.CravingEvent
import com.dadsvictory.domain.CravingOutcome
import com.dadsvictory.domain.Currency
import com.dadsvictory.domain.Profile
import com.dadsvictory.domain.Slip
import com.dadsvictory.domain.Substance
import com.dadsvictory.domain.backup.BackupCheckIn
import com.dadsvictory.domain.backup.BackupCodec
import com.dadsvictory.domain.backup.BackupCraving
import com.dadsvictory.domain.backup.BackupFamilyMessage
import com.dadsvictory.domain.backup.BackupJournalEntry
import com.dadsvictory.domain.backup.BackupPayload
import com.dadsvictory.domain.backup.BackupPlanCompletion
import com.dadsvictory.domain.backup.BackupPlanTask
import com.dadsvictory.domain.backup.BackupProfile
import com.dadsvictory.domain.backup.BackupSlip
import com.dadsvictory.domain.Country
import com.dadsvictory.domain.DrinkBasis
import com.dadsvictory.domain.content.BibleVersion
import com.dadsvictory.domain.content.DailyPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The single place the UI talks to. Everything it returns comes off this device.
 */
class VictoryRepository(private val context: Context) {

    private val db = AppDatabase.get(context)
    val settingsStore = SettingsStore(context)

    val settings: Flow<AppSettings> = settingsStore.settings

    val slips: Flow<List<Slip>> = db.slipDao().observeAll().map { list -> list.map { it.toDomain() } }

    val cravings: Flow<List<CravingEvent>> =
        db.cravingDao().observeAll().map { list -> list.map { it.toDomain() } }

    val cravingsDefeated: Flow<Int> = db.cravingDao().observeDefeatedCount()

    val checkIns: Flow<List<CheckIn>> =
        db.checkInDao().observeAll().map { list -> list.map { it.toDomain() } }

    val journal: Flow<List<JournalEntity>> = db.journalDao().observeAll()

    val favouriteVerses: Flow<Set<String>> =
        db.favouriteVerseDao().observeAll().map { list -> list.map { it.reference }.toSet() }

    val selectedTriggerIds: Flow<Set<String>> =
        db.triggerDao().observeAll().map { list -> list.map { it.triggerId }.toSet() }

    val storyAchievementIds: Flow<Set<String>> =
        db.storyAchievementDao().observeAll().map { list -> list.map { it.achievementId }.toSet() }

    val familyMessages: Flow<List<FamilyMessageEntity>> = db.familyMessageDao().observeAll()

    val planTasks: Flow<List<PlanTaskEntity>> = db.planDao().observeTasks()

    fun planCompletions(epochDay: Long): Flow<Set<String>> =
        db.planDao().observeCompletions(epochDay).map { list -> list.map { it.taskId }.toSet() }

    fun checkInForDay(epochDay: Long): Flow<CheckIn?> =
        db.checkInDao().observeForDay(epochDay).map { it?.toDomain() }

    // ---------------------------------------------------------------- writes

    suspend fun recordCravingWon(secondsHeld: Int, triggerId: String?, nowMillis: Long) {
        db.cravingDao().insert(
            CravingEntity(
                atMillis = nowMillis,
                outcome = CravingOutcome.WON.id,
                secondsHeld = secondsHeld,
                triggerId = triggerId,
            ),
        )
    }

    suspend fun recordCravingOutcome(outcome: CravingOutcome, secondsHeld: Int, triggerId: String?, nowMillis: Long) {
        db.cravingDao().insert(
            CravingEntity(
                atMillis = nowMillis,
                outcome = outcome.id,
                secondsHeld = secondsHeld,
                triggerId = triggerId,
            ),
        )
    }

    suspend fun recordSlip(
        substances: Set<Substance>,
        triggerId: String?,
        reflection: String?,
        nextChange: String?,
        atMillis: Long,
    ) {
        db.slipDao().insert(
            SlipEntity.from(
                Slip(
                    atMillis = atMillis,
                    substances = substances,
                    triggerId = triggerId,
                    reflection = reflection,
                    nextChange = nextChange,
                ),
            ),
        )
        // Earning this one is the point: he was honest and he came back.
        unlockStoryAchievement("story_got_back_up", atMillis)
    }

    suspend fun saveCheckIn(checkIn: CheckIn) = db.checkInDao().upsert(CheckInEntity.from(checkIn))

    suspend fun addJournalEntry(prompt: String, body: String, nowMillis: Long): Long =
        db.journalDao().insert(JournalEntity(createdAtMillis = nowMillis, prompt = prompt, body = body))

    suspend fun updateJournalEntry(id: Long, prompt: String, body: String) =
        db.journalDao().update(id, prompt, body)

    suspend fun deleteJournalEntry(entry: JournalEntity) = db.journalDao().delete(entry)

    suspend fun toggleFavouriteVerse(reference: String, nowMillis: Long) {
        if (reference in db.favouriteVerseDao().getReferences()) {
            db.favouriteVerseDao().remove(reference)
        } else {
            db.favouriteVerseDao().insert(FavouriteVerseEntity(reference, nowMillis))
        }
    }

    suspend fun setTriggerSelected(triggerId: String, selected: Boolean) {
        if (selected) {
            db.triggerDao().insert(TriggerSelectionEntity(triggerId))
        } else {
            db.triggerDao().remove(triggerId)
        }
    }

    suspend fun unlockStoryAchievement(achievementId: String, nowMillis: Long) =
        db.storyAchievementDao().insert(StoryAchievementEntity(achievementId, nowMillis))

    suspend fun lockStoryAchievement(achievementId: String) =
        db.storyAchievementDao().remove(achievementId)

    suspend fun addFamilyMessage(text: String, nowMillis: Long) =
        db.familyMessageDao().insert(FamilyMessageEntity(text = text, createdAtMillis = nowMillis))

    suspend fun deleteFamilyMessage(message: FamilyMessageEntity) =
        db.familyMessageDao().delete(message)

    suspend fun setPlanTaskDone(epochDay: Long, taskId: String, done: Boolean) {
        if (done) {
            db.planDao().markDone(PlanCompletionEntity(epochDay, taskId))
        } else {
            db.planDao().markNotDone(epochDay, taskId)
        }
    }

    suspend fun upsertPlanTask(task: PlanTaskEntity) = db.planDao().upsertTask(task)

    suspend fun deletePlanTask(taskId: String) = db.planDao().deleteTask(taskId)

    /** Seeds the default victory plan the first time he opens it. */
    suspend fun ensurePlanSeeded(quitNicotine: Boolean, quitAlcohol: Boolean) {
        if (db.planDao().taskCount() > 0) return
        DailyPlan.defaultsFor(quitNicotine, quitAlcohol).forEachIndexed { index, task ->
            db.planDao().upsertTask(
                PlanTaskEntity(
                    taskId = task.id,
                    slotId = task.slot.id,
                    title = task.title,
                    sortOrder = index,
                    enabled = true,
                ),
            )
        }
    }

    // --------------------------------------------------------------- backup

    suspend fun buildBackup(nowMillis: Long): BackupPayload = withContext(Dispatchers.IO) {
        val settings = settingsStore.current()
        val profile = settings.profile
        BackupPayload(
            exportedAtMillis = nowMillis,
            profile = BackupProfile(
                quitNicotine = profile.quitNicotine,
                quitAlcohol = profile.quitAlcohol,
                startMillis = profile.startMillis,
                reasonIds = profile.reasonIds.toList(),
                customReason = profile.customReason,
                countryId = profile.country.id,
                currencyId = profile.currency.id,
                nicotineWeeklySpendMinor = profile.nicotineWeeklySpendMinor,
                alcoholWeeklySpendMinor = profile.alcoholWeeklySpendMinor,
                vapeSessionsPerDay = profile.vapeSessionsPerDay,
                puffsPerDay = profile.puffsPerDay,
                nicotineStrengthMgPerMl = profile.nicotineStrengthMgPerMl,
                drinkBasisId = profile.drinkBasis.id,
                drinksPerWeek = profile.drinksPerWeek,
                savingsGoalName = profile.savingsGoalName,
                savingsGoalMinor = profile.savingsGoalMinor,
                bibleVersionId = settings.bibleVersion.id,
            ),
            slips = db.slipDao().getAll().map { entity ->
                val slip = entity.toDomain()
                BackupSlip(
                    atMillis = slip.atMillis,
                    substances = slip.substances.map { it.name },
                    triggerId = slip.triggerId,
                    reflection = slip.reflection,
                    nextChange = slip.nextChange,
                )
            },
            cravings = db.cravingDao().getAll().map {
                BackupCraving(it.atMillis, it.outcome, it.secondsHeld, it.triggerId)
            },
            checkIns = db.checkInDao().getAll().map {
                BackupCheckIn(
                    it.epochDay, it.moodScore, it.cravingLevel, it.stressLevel,
                    it.stayedNicotineFree, it.stayedAlcoholFree, it.note,
                )
            },
            journal = db.journalDao().getAll().map {
                BackupJournalEntry(it.createdAtMillis, it.prompt, it.body)
            },
            familyMessages = db.familyMessageDao().getAll().map {
                BackupFamilyMessage(it.createdAtMillis, it.text)
            },
            favouriteVerses = db.favouriteVerseDao().getReferences(),
            selectedTriggerIds = db.triggerDao().getIds(),
            storyAchievementIds = db.storyAchievementDao().getIds(),
            planTasks = db.planDao().getTasks().map {
                BackupPlanTask(it.taskId, it.slotId, it.title, it.sortOrder, it.enabled)
            },
            planCompletions = db.planDao().getAllCompletions().map {
                BackupPlanCompletion(it.epochDay, it.taskId)
            },
        )
    }

    suspend fun exportBackup(passphrase: CharArray, nowMillis: Long): ByteArray =
        BackupCodec.export(buildBackup(nowMillis), passphrase)

    /**
     * Replaces everything with the contents of a backup. Destructive on purpose:
     * a partial merge of two histories would produce streaks that never happened.
     */
    suspend fun restoreBackup(payload: BackupPayload) = withContext(Dispatchers.IO) {
        clearDatabase()

        val p = payload.profile
        settingsStore.saveProfile(
            Profile(
                quitNicotine = p.quitNicotine,
                quitAlcohol = p.quitAlcohol,
                startMillis = p.startMillis,
                reasonIds = p.reasonIds.toSet(),
                customReason = p.customReason,
                country = Country.fromId(p.countryId),
                currency = Currency.fromId(p.currencyId),
                nicotineWeeklySpendMinor = p.nicotineWeeklySpendMinor,
                alcoholWeeklySpendMinor = p.alcoholWeeklySpendMinor,
                vapeSessionsPerDay = p.vapeSessionsPerDay,
                puffsPerDay = p.puffsPerDay,
                nicotineStrengthMgPerMl = p.nicotineStrengthMgPerMl,
                drinkBasis = DrinkBasis.fromId(p.drinkBasisId),
                drinksPerWeek = p.drinksPerWeek,
                savingsGoalName = p.savingsGoalName,
                savingsGoalMinor = p.savingsGoalMinor,
            ),
        )
        settingsStore.setBibleVersion(BibleVersion.fromId(p.bibleVersionId))
        settingsStore.setOnboardingComplete(true)

        payload.slips.forEach { slip ->
            db.slipDao().insert(
                SlipEntity(
                    atMillis = slip.atMillis,
                    nicotine = "NICOTINE" in slip.substances,
                    alcohol = "ALCOHOL" in slip.substances,
                    triggerId = slip.triggerId,
                    reflection = slip.reflection,
                    nextChange = slip.nextChange,
                ),
            )
        }
        payload.cravings.forEach {
            db.cravingDao().insert(CravingEntity(atMillis = it.atMillis, outcome = it.outcome, secondsHeld = it.secondsHeld, triggerId = it.triggerId))
        }
        payload.checkIns.forEach {
            db.checkInDao().upsert(
                CheckInEntity(it.epochDay, it.moodScore, it.cravingLevel, it.stressLevel, it.stayedNicotineFree, it.stayedAlcoholFree, it.note),
            )
        }
        payload.journal.forEach {
            db.journalDao().insert(JournalEntity(createdAtMillis = it.createdAtMillis, prompt = it.prompt, body = it.body))
        }
        payload.familyMessages.forEach {
            db.familyMessageDao().insert(FamilyMessageEntity(text = it.text, createdAtMillis = it.createdAtMillis))
        }
        payload.favouriteVerses.forEach { db.favouriteVerseDao().insert(FavouriteVerseEntity(it, payload.exportedAtMillis)) }
        payload.selectedTriggerIds.forEach { db.triggerDao().insert(TriggerSelectionEntity(it)) }
        payload.storyAchievementIds.forEach { db.storyAchievementDao().insert(StoryAchievementEntity(it, payload.exportedAtMillis)) }
        payload.planTasks.forEach {
            db.planDao().upsertTask(PlanTaskEntity(it.taskId, it.slotId, it.title, it.sortOrder, it.enabled))
        }
        payload.planCompletions.forEach { db.planDao().markDone(PlanCompletionEntity(it.epochDay, it.taskId)) }
    }

    private suspend fun clearDatabase() {
        db.slipDao().clear()
        db.cravingDao().clear()
        db.checkInDao().clear()
        db.journalDao().clear()
        db.favouriteVerseDao().clear()
        db.triggerDao().clear()
        db.storyAchievementDao().clear()
        db.familyMessageDao().clear()
        db.planDao().clearTasks()
        db.planDao().clearCompletions()
    }

    /** "Delete everything" in Settings. Nothing survives it, including the photo. */
    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        clearDatabase()
        settingsStore.clearAll()
        PhotoStore.delete(context)
    }
}
