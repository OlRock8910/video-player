package com.dadsvictory.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dadsvictory.data.VictoryRepository
import com.dadsvictory.data.db.FamilyMessageEntity
import com.dadsvictory.data.db.JournalEntity
import com.dadsvictory.data.db.PlanTaskEntity
import com.dadsvictory.data.local.PinHasher
import com.dadsvictory.data.prefs.AppSettings
import com.dadsvictory.data.prefs.ThemeMode
import com.dadsvictory.domain.Achievements
import com.dadsvictory.domain.CheckIn
import com.dadsvictory.domain.Country
import com.dadsvictory.domain.CravingOutcome
import com.dadsvictory.domain.Currency
import com.dadsvictory.domain.Money
import com.dadsvictory.domain.NotificationSlot
import com.dadsvictory.domain.Profile
import com.dadsvictory.domain.Slip
import com.dadsvictory.domain.Streaks
import com.dadsvictory.domain.Substance
import com.dadsvictory.domain.content.BibleVersion
import com.dadsvictory.domain.content.HealthMilestones
import com.dadsvictory.domain.content.Milestone
import com.dadsvictory.notifications.DailyAlarmScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One view model for the whole app.
 *
 * The app is small enough that a single source of truth beats a dependency
 * injection framework and six near-identical view models, and it means every
 * screen sees exactly the same streak at exactly the same moment.
 */
class VictoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VictoryRepository(application)

    /**
     * Drives the live parts of the dashboard. Fifteen seconds is frequent enough
     * that the minutes counter on day one visibly moves, and rare enough that the
     * app is not redrawing itself every second in his pocket.
     */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(15_000)
        }
    }

    private data class CoreData(
        val settings: AppSettings,
        val slips: List<Slip>,
        val cravingsDefeated: Int,
    )

    private data class ExtraData(
        val storyAchievementIds: Set<String>,
        val favouriteVerses: Set<String>,
        val selectedTriggerIds: Set<String>,
        val checkIns: List<CheckIn>,
    )

    private val core = combine(
        repository.settings,
        repository.slips,
        repository.cravingsDefeated,
    ) { settings, slips, cravings -> CoreData(settings, slips, cravings) }

    private val extra = combine(
        repository.storyAchievementIds,
        repository.favouriteVerses,
        repository.selectedTriggerIds,
        repository.checkIns,
    ) { story, favourites, triggers, checkIns -> ExtraData(story, favourites, triggers, checkIns) }

    val uiState: StateFlow<VictoryUiState> = combine(core, extra, ticker) { c, e, now ->
        VictoryUiState(
            loading = false,
            settings = c.settings,
            nowMillis = now,
            slips = c.slips,
            cravingsDefeated = c.cravingsDefeated,
            storyAchievementIds = e.storyAchievementIds,
            favouriteVerses = e.favouriteVerses,
            selectedTriggerIds = e.selectedTriggerIds,
            checkIns = e.checkIns,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VictoryUiState(),
    )

    val journal: Flow<List<JournalEntity>> = repository.journal
    val familyMessages: Flow<List<FamilyMessageEntity>> = repository.familyMessages
    val planTasks: Flow<List<PlanTaskEntity>> = repository.planTasks

    fun planCompletions(epochDay: Long): Flow<Set<String>> = repository.planCompletions(epochDay)
    fun checkInForDay(epochDay: Long): Flow<CheckIn?> = repository.checkInForDay(epochDay)

    /** Set once the journal PIN has been entered, for this app session only. */
    private val _journalUnlocked = MutableStateFlow(false)
    val journalUnlocked: StateFlow<Boolean> = _journalUnlocked

    // ------------------------------------------------------------ onboarding

    fun completeOnboarding(profile: Profile, drinkingDaysPerWeek: Int) = viewModelScope.launch {
        repository.settingsStore.saveProfile(profile)
        repository.settingsStore.setDrinkingDaysPerWeek(drinkingDaysPerWeek)
        repository.settingsStore.setOnboardingComplete(true)
        repository.ensurePlanSeeded(profile.quitNicotine, profile.quitAlcohol)
        rescheduleNotifications()
    }

    fun setNotificationSlot(slot: NotificationSlot, enabled: Boolean, minuteOfDay: Int) = viewModelScope.launch {
        repository.settingsStore.setNotificationSlot(slot, enabled, minuteOfDay)
        rescheduleNotifications()
    }

    /** Called on launch and whenever a notification setting changes. */
    fun rescheduleNotifications() = viewModelScope.launch {
        val settings = repository.settingsStore.current()
        DailyAlarmScheduler.rescheduleAll(getApplication<Application>(), settings)
    }

    fun seedPlanIfNeeded() = viewModelScope.launch {
        val profile = repository.settingsStore.current().profile
        repository.ensurePlanSeeded(profile.quitNicotine, profile.quitAlcohol)
    }

    // ---------------------------------------------------------------- craving

    fun recordCravingWon(secondsHeld: Int, triggerId: String?) = viewModelScope.launch {
        repository.recordCravingWon(secondsHeld, triggerId, System.currentTimeMillis())
    }

    fun recordCravingOutcome(outcome: CravingOutcome, secondsHeld: Int, triggerId: String?) =
        viewModelScope.launch {
            repository.recordCravingOutcome(outcome, secondsHeld, triggerId, System.currentTimeMillis())
        }

    // ------------------------------------------------------------------ slips

    fun recordSlip(
        substances: Set<Substance>,
        triggerId: String?,
        reflection: String?,
        nextChange: String?,
        atMillis: Long = System.currentTimeMillis(),
    ) = viewModelScope.launch {
        repository.recordSlip(substances, triggerId, reflection, nextChange, atMillis)
    }

    // -------------------------------------------------------------- check-ins

    fun saveCheckIn(checkIn: CheckIn) = viewModelScope.launch { repository.saveCheckIn(checkIn) }

    // ---------------------------------------------------------------- journal

    fun addJournalEntry(prompt: String, body: String) = viewModelScope.launch {
        repository.addJournalEntry(prompt, body, System.currentTimeMillis())
    }

    fun updateJournalEntry(id: Long, prompt: String, body: String) = viewModelScope.launch {
        repository.updateJournalEntry(id, prompt, body)
    }

    fun deleteJournalEntry(entry: JournalEntity) = viewModelScope.launch {
        repository.deleteJournalEntry(entry)
    }

    fun unlockJournal() { _journalUnlocked.value = true }

    fun lockJournal() { _journalUnlocked.value = false }

    fun verifyJournalPin(pin: String): Boolean {
        val settings = uiState.value.settings
        return PinHasher.verify(pin, settings.journalPinSalt, settings.journalPinHash)
    }

    fun setJournalPin(pin: String) = viewModelScope.launch {
        val salt = PinHasher.newSalt()
        repository.settingsStore.setJournalLock(true, PinHasher.hash(pin, salt), salt)
        _journalUnlocked.value = true
    }

    fun removeJournalLock() = viewModelScope.launch {
        repository.settingsStore.setJournalLock(false, "", "")
        repository.settingsStore.setBiometricUnlock(false)
        _journalUnlocked.value = true
    }

    fun setBiometricUnlock(enabled: Boolean) = viewModelScope.launch {
        repository.settingsStore.setBiometricUnlock(enabled)
    }

    // --------------------------------------------------------------- content

    fun toggleFavouriteVerse(reference: String) = viewModelScope.launch {
        repository.toggleFavouriteVerse(reference, System.currentTimeMillis())
    }

    fun setTriggerSelected(triggerId: String, selected: Boolean) = viewModelScope.launch {
        repository.setTriggerSelected(triggerId, selected)
    }

    fun setStoryAchievement(achievementId: String, unlocked: Boolean) = viewModelScope.launch {
        if (unlocked) {
            repository.unlockStoryAchievement(achievementId, System.currentTimeMillis())
        } else {
            repository.lockStoryAchievement(achievementId)
        }
    }

    fun addFamilyMessage(text: String) = viewModelScope.launch {
        repository.addFamilyMessage(text, System.currentTimeMillis())
    }

    fun deleteFamilyMessage(message: FamilyMessageEntity) = viewModelScope.launch {
        repository.deleteFamilyMessage(message)
    }

    // ------------------------------------------------------------------ plan

    fun setPlanTaskDone(epochDay: Long, taskId: String, done: Boolean) = viewModelScope.launch {
        repository.setPlanTaskDone(epochDay, taskId, done)
    }

    fun addPlanTask(slotId: String, title: String) = viewModelScope.launch {
        // Custom tasks sort after the defaults, in the order he added them.
        val now = System.currentTimeMillis()
        repository.upsertPlanTask(
            PlanTaskEntity(
                taskId = "custom_$now",
                slotId = slotId,
                title = title,
                sortOrder = 1_000 + (now % 1_000_000).toInt(),
                enabled = true,
            ),
        )
    }

    fun deletePlanTask(taskId: String) = viewModelScope.launch { repository.deletePlanTask(taskId) }

    // -------------------------------------------------------------- settings

    fun updateProfile(profile: Profile) = viewModelScope.launch {
        repository.settingsStore.saveProfile(profile)
    }

    fun setCountry(country: Country) = viewModelScope.launch { repository.settingsStore.setCountry(country) }

    fun setCurrency(currency: Currency) = viewModelScope.launch { repository.settingsStore.setCurrency(currency) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.settingsStore.setThemeMode(mode) }

    fun setDynamicColour(enabled: Boolean) = viewModelScope.launch { repository.settingsStore.setDynamicColour(enabled) }

    fun setReducedMotion(enabled: Boolean) = viewModelScope.launch { repository.settingsStore.setReducedMotion(enabled) }

    fun setHighContrast(enabled: Boolean) = viewModelScope.launch { repository.settingsStore.setHighContrast(enabled) }

    fun setBibleVersion(version: BibleVersion) = viewModelScope.launch { repository.settingsStore.setBibleVersion(version) }

    fun setSavingsGoal(name: String, amountMinor: Long) = viewModelScope.launch {
        repository.settingsStore.setSavingsGoal(name, amountMinor)
    }

    fun setHasFamilyPhoto(has: Boolean) = viewModelScope.launch { repository.settingsStore.setHasFamilyPhoto(has) }

    fun setStartMillis(startMillis: Long) = viewModelScope.launch {
        repository.settingsStore.setStartMillis(startMillis)
    }

    fun acknowledgeAlcoholSafety() = viewModelScope.launch {
        repository.settingsStore.setAcknowledgedAlcoholSafety(true)
    }

    fun markMoneyCelebrated(units: Long) = viewModelScope.launch {
        repository.settingsStore.setLastCelebratedMoney(units)
    }

    // ---------------------------------------------------------------- backup

    suspend fun exportBackup(passphrase: CharArray): ByteArray =
        repository.exportBackup(passphrase, System.currentTimeMillis())

    suspend fun restoreBackup(file: ByteArray, passphrase: CharArray) {
        val payload = com.dadsvictory.domain.backup.BackupCodec.import(file, passphrase)
        repository.restoreBackup(payload)
        rescheduleNotifications()
    }

    fun deleteAllData(onDone: () -> Unit) = viewModelScope.launch {
        DailyAlarmScheduler.cancelAll(getApplication<Application>())
        repository.deleteAllData()
        _journalUnlocked.value = false
        onDone()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras,
            ): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                return VictoryViewModel(application) as T
            }
        }
    }
}

/**
 * Everything on screen, derived in one place.
 *
 * The computed properties are cheap pure functions over data already in memory,
 * so they can be read straight from composables without caching gymnastics.
 */
data class VictoryUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val nowMillis: Long = System.currentTimeMillis(),
    val slips: List<Slip> = emptyList(),
    val cravingsDefeated: Int = 0,
    val storyAchievementIds: Set<String> = emptySet(),
    val favouriteVerses: Set<String> = emptySet(),
    val selectedTriggerIds: Set<String> = emptySet(),
    val checkIns: List<CheckIn> = emptyList(),
) {
    val profile: Profile get() = settings.profile
    val currency: Currency get() = profile.currency

    val zone: ZoneId get() = ZoneId.systemDefault()

    val today: LocalDate
        get() = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    val todayEpochDay: Long get() = today.toEpochDay()

    val nicotineStats: Streaks.Stats
        get() = Streaks.statsFor(Substance.NICOTINE, profile.startMillis, nowMillis, slips)

    val alcoholStats: Streaks.Stats
        get() = Streaks.statsFor(Substance.ALCOHOL, profile.startMillis, nowMillis, slips)

    /** The headline number: the shorter of the active streaks. */
    val headlineStreakDays: Int get() = Streaks.headlineStreakDays(profile, nowMillis, slips)

    val headlineStats: Streaks.Stats
        get() = when {
            profile.quitNicotine && profile.quitAlcohol ->
                if (nicotineStats.currentStreakDays <= alcoholStats.currentStreakDays) nicotineStats else alcoholStats
            profile.quitAlcohol -> alcoholStats
            else -> nicotineStats
        }

    val bestStreakDays: Int
        get() = maxOf(
            if (profile.quitNicotine) nicotineStats.bestStreakDays else 0,
            if (profile.quitAlcohol) alcoholStats.bestStreakDays else 0,
        )

    val totalFreeDays: Int
        get() = maxOf(
            if (profile.quitNicotine) nicotineStats.totalFreeDays else 0,
            if (profile.quitAlcohol) alcoholStats.totalFreeDays else 0,
        )

    val slipCount: Int
        get() = slips.count { it.atMillis in (profile.startMillis + 1) until nowMillis }

    val journeyDays: Int get() = Streaks.journeyDays(profile.startMillis, nowMillis)

    val moneySaved: Money.Saved get() = Money.savedSoFar(profile, nowMillis, slips)

    val vapesAvoided: Long get() = Money.vapesAvoided(profile, nowMillis, slips)

    val drinksAvoided: Long get() = Money.drinksAvoided(profile, nowMillis, slips)

    val achievements: List<Achievements.Progress>
        get() = Achievements.evaluate(
            bestStreakDays = bestStreakDays,
            savedMinor = moneySaved.totalMinor,
            cravingsDefeated = cravingsDefeated,
            currency = currency,
            manuallyUnlockedIds = storyAchievementIds,
        )

    val nextMilestone: Milestone?
        get() = HealthMilestones.nextAhead(profile.quitNicotine, profile.quitAlcohol, headlineStreakDays)

    val alcoholFreeCheckInDays: Int get() = checkIns.count { it.stayedAlcoholFree == true }
    val nicotineFreeCheckInDays: Int get() = checkIns.count { it.stayedNicotineFree == true }

    val hasCheckedInToday: Boolean get() = checkIns.any { it.epochDay == todayEpochDay }

    /** Day index for rotating content, so every day gets a fresh message. */
    val rotationIndex: Long get() = todayEpochDay
}
