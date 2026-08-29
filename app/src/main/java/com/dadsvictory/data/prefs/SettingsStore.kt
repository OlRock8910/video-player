package com.dadsvictory.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dadsvictory.domain.Country
import com.dadsvictory.domain.Currency
import com.dadsvictory.domain.DrinkBasis
import com.dadsvictory.domain.NotificationSlot
import com.dadsvictory.domain.Profile
import com.dadsvictory.domain.content.BibleVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "Follow system"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    ;

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

data class SlotSetting(val enabled: Boolean, val minuteOfDay: Int) {
    val hour: Int get() = minuteOfDay / 60
    val minute: Int get() = minuteOfDay % 60
}

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val profile: Profile = Profile(),
    val bibleVersion: BibleVersion = BibleVersion.WEB,
    val notifications: Map<NotificationSlot, SlotSetting> = NotificationSlot.entries.associateWith {
        SlotSetting(enabled = true, minuteOfDay = it.defaultMinuteOfDay)
    },
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColour: Boolean = false,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val journalLockEnabled: Boolean = false,
    val journalPinHash: String = "",
    val journalPinSalt: String = "",
    val biometricUnlock: Boolean = false,
    val hasFamilyPhoto: Boolean = false,
    /** Off, and there is no code anywhere in the app that turns it on. */
    val analyticsEnabled: Boolean = false,
    val drinkingDaysPerWeek: Int = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dads_victory_settings")

/**
 * All preferences, on device, in one DataStore. No account, no sync, no server.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val QUIT_NICOTINE = booleanPreferencesKey("quit_nicotine")
        val QUIT_ALCOHOL = booleanPreferencesKey("quit_alcohol")
        val START_MILLIS = longPreferencesKey("start_millis")
        val REASON_IDS = stringSetPreferencesKey("reason_ids")
        val CUSTOM_REASON = stringPreferencesKey("custom_reason")
        val COUNTRY = stringPreferencesKey("country")
        val CURRENCY = stringPreferencesKey("currency")
        val NICOTINE_WEEKLY = longPreferencesKey("nicotine_weekly_minor")
        val ALCOHOL_WEEKLY = longPreferencesKey("alcohol_weekly_minor")
        val VAPE_SESSIONS = intPreferencesKey("vape_sessions_per_day")
        val PUFFS = intPreferencesKey("puffs_per_day")
        val NICOTINE_STRENGTH = doublePreferencesKey("nicotine_strength")
        val DRINK_BASIS = stringPreferencesKey("drink_basis")
        val DRINKS_PER_WEEK = doublePreferencesKey("drinks_per_week")
        val DRINKING_DAYS = intPreferencesKey("drinking_days_per_week")
        val GOAL_NAME = stringPreferencesKey("savings_goal_name")
        val GOAL_MINOR = longPreferencesKey("savings_goal_minor")
        val BIBLE_VERSION = stringPreferencesKey("bible_version")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOUR = booleanPreferencesKey("dynamic_colour")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val JOURNAL_LOCK = booleanPreferencesKey("journal_lock")
        val JOURNAL_PIN_HASH = stringPreferencesKey("journal_pin_hash")
        val JOURNAL_PIN_SALT = stringPreferencesKey("journal_pin_salt")
        val BIOMETRIC = booleanPreferencesKey("biometric_unlock")
        val HAS_PHOTO = booleanPreferencesKey("has_family_photo")

        fun slotEnabled(slot: NotificationSlot) = booleanPreferencesKey("notif_${slot.id}_enabled")
        fun slotMinute(slot: NotificationSlot) = intPreferencesKey("notif_${slot.id}_minute")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { throwable ->
            // A corrupt preferences file should not stop the app opening.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings {
        val country = Country.fromId(this[Keys.COUNTRY])
        return AppSettings(
            onboardingComplete = this[Keys.ONBOARDING_COMPLETE] ?: false,
            profile = Profile(
                quitNicotine = this[Keys.QUIT_NICOTINE] ?: true,
                quitAlcohol = this[Keys.QUIT_ALCOHOL] ?: true,
                startMillis = this[Keys.START_MILLIS] ?: 0L,
                reasonIds = this[Keys.REASON_IDS] ?: emptySet(),
                customReason = this[Keys.CUSTOM_REASON] ?: "",
                country = country,
                currency = this[Keys.CURRENCY]?.let { Currency.fromId(it) } ?: Currency.defaultFor(country),
                nicotineWeeklySpendMinor = this[Keys.NICOTINE_WEEKLY] ?: 0L,
                alcoholWeeklySpendMinor = this[Keys.ALCOHOL_WEEKLY] ?: 0L,
                vapeSessionsPerDay = this[Keys.VAPE_SESSIONS] ?: 0,
                puffsPerDay = this[Keys.PUFFS] ?: 0,
                nicotineStrengthMgPerMl = this[Keys.NICOTINE_STRENGTH] ?: 0.0,
                drinkBasis = this[Keys.DRINK_BASIS]?.let { DrinkBasis.fromId(it) } ?: DrinkBasis.defaultFor(country),
                drinksPerWeek = this[Keys.DRINKS_PER_WEEK] ?: 0.0,
                savingsGoalName = this[Keys.GOAL_NAME] ?: "",
                savingsGoalMinor = this[Keys.GOAL_MINOR] ?: 0L,
            ),
            bibleVersion = BibleVersion.fromId(this[Keys.BIBLE_VERSION]),
            notifications = NotificationSlot.entries.associateWith { slot ->
                SlotSetting(
                    enabled = this[Keys.slotEnabled(slot)] ?: true,
                    minuteOfDay = this[Keys.slotMinute(slot)] ?: slot.defaultMinuteOfDay,
                )
            },
            themeMode = ThemeMode.fromId(this[Keys.THEME_MODE]),
            dynamicColour = this[Keys.DYNAMIC_COLOUR] ?: false,
            reducedMotion = this[Keys.REDUCED_MOTION] ?: false,
            highContrast = this[Keys.HIGH_CONTRAST] ?: false,
            journalLockEnabled = this[Keys.JOURNAL_LOCK] ?: false,
            journalPinHash = this[Keys.JOURNAL_PIN_HASH] ?: "",
            journalPinSalt = this[Keys.JOURNAL_PIN_SALT] ?: "",
            biometricUnlock = this[Keys.BIOMETRIC] ?: false,
            hasFamilyPhoto = this[Keys.HAS_PHOTO] ?: false,
            analyticsEnabled = false,
            drinkingDaysPerWeek = this[Keys.DRINKING_DAYS] ?: 0,
        )
    }

    suspend fun saveProfile(profile: Profile) = context.dataStore.edit { prefs ->
        prefs[Keys.QUIT_NICOTINE] = profile.quitNicotine
        prefs[Keys.QUIT_ALCOHOL] = profile.quitAlcohol
        prefs[Keys.START_MILLIS] = profile.startMillis
        prefs[Keys.REASON_IDS] = profile.reasonIds
        prefs[Keys.CUSTOM_REASON] = profile.customReason
        prefs[Keys.COUNTRY] = profile.country.id
        prefs[Keys.CURRENCY] = profile.currency.id
        prefs[Keys.NICOTINE_WEEKLY] = profile.nicotineWeeklySpendMinor
        prefs[Keys.ALCOHOL_WEEKLY] = profile.alcoholWeeklySpendMinor
        prefs[Keys.VAPE_SESSIONS] = profile.vapeSessionsPerDay
        prefs[Keys.PUFFS] = profile.puffsPerDay
        prefs[Keys.NICOTINE_STRENGTH] = profile.nicotineStrengthMgPerMl
        prefs[Keys.DRINK_BASIS] = profile.drinkBasis.id
        prefs[Keys.DRINKS_PER_WEEK] = profile.drinksPerWeek
        prefs[Keys.GOAL_NAME] = profile.savingsGoalName
        prefs[Keys.GOAL_MINOR] = profile.savingsGoalMinor
    }

    suspend fun setOnboardingComplete(complete: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }

    suspend fun setDrinkingDaysPerWeek(days: Int) =
        context.dataStore.edit { it[Keys.DRINKING_DAYS] = days }

    suspend fun setNotificationSlot(slot: NotificationSlot, enabled: Boolean, minuteOfDay: Int) =
        context.dataStore.edit {
            it[Keys.slotEnabled(slot)] = enabled
            it[Keys.slotMinute(slot)] = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        }

    suspend fun setBibleVersion(version: BibleVersion) =
        context.dataStore.edit { it[Keys.BIBLE_VERSION] = version.id }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.id }

    suspend fun setDynamicColour(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC_COLOUR] = enabled }

    suspend fun setReducedMotion(enabled: Boolean) =
        context.dataStore.edit { it[Keys.REDUCED_MOTION] = enabled }

    suspend fun setHighContrast(enabled: Boolean) =
        context.dataStore.edit { it[Keys.HIGH_CONTRAST] = enabled }

    suspend fun setCountry(country: Country) = context.dataStore.edit {
        it[Keys.COUNTRY] = country.id
        // Only follow the country with the currency if he has not chosen one himself.
        if (it[Keys.CURRENCY] == null) it[Keys.CURRENCY] = Currency.defaultFor(country).id
    }

    suspend fun setCurrency(currency: Currency) =
        context.dataStore.edit { it[Keys.CURRENCY] = currency.id }

    suspend fun setSavingsGoal(name: String, amountMinor: Long) = context.dataStore.edit {
        it[Keys.GOAL_NAME] = name
        it[Keys.GOAL_MINOR] = amountMinor
    }

    suspend fun setJournalLock(enabled: Boolean, pinHash: String, pinSalt: String) =
        context.dataStore.edit {
            it[Keys.JOURNAL_LOCK] = enabled
            it[Keys.JOURNAL_PIN_HASH] = pinHash
            it[Keys.JOURNAL_PIN_SALT] = pinSalt
        }

    suspend fun setBiometricUnlock(enabled: Boolean) =
        context.dataStore.edit { it[Keys.BIOMETRIC] = enabled }

    suspend fun setHasFamilyPhoto(has: Boolean) =
        context.dataStore.edit { it[Keys.HAS_PHOTO] = has }

    /** Used by "delete all my data". Leaves the app exactly as it was on first install. */
    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
