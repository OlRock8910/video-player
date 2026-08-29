package com.dadsvictory.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted backup file format.
 *
 * The app never uploads anything. If he wants a copy of his data — to move phones,
 * or just to have one — he exports a single file that he controls, encrypted with
 * a passphrase only he knows. Losing the passphrase means losing the file, and the
 * export screen says so plainly, because there is no recovery path by design.
 *
 * Layout: "DVBK" | version (1 byte) | salt (16) | iv (12) | AES-256-GCM ciphertext.
 */
object BackupCrypto {

    private val MAGIC = byteArrayOf('D'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 210_000

    private const val HEADER_BYTES = 4 + 1 + SALT_BYTES + IV_BYTES

    class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val keyBytes = try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "A passphrase is required" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        return MAGIC + byteArrayOf(VERSION) + salt + iv + ciphertext
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): ByteArray {
        if (file.size <= HEADER_BYTES) {
            throw BackupFormatException("This file is too small to be a Dad's Victory backup.")
        }
        if (!file.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw BackupFormatException("This doesn't look like a Dad's Victory backup file.")
        }
        if (file[4] != VERSION) {
            throw BackupFormatException("This backup was made by a newer version of the app.")
        }
        val salt = file.copyOfRange(5, 5 + SALT_BYTES)
        val iv = file.copyOfRange(5 + SALT_BYTES, HEADER_BYTES)
        val ciphertext = file.copyOfRange(HEADER_BYTES, file.size)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw BackupFormatException("Wrong passphrase, or the file has been damaged.", e)
        } catch (e: javax.crypto.BadPaddingException) {
            throw BackupFormatException("Wrong passphrase, or the file has been damaged.", e)
        }
    }
}

@Serializable
data class BackupProfile(
    val quitNicotine: Boolean = true,
    val quitAlcohol: Boolean = true,
    val startMillis: Long = 0L,
    val reasonIds: List<String> = emptyList(),
    val customReason: String = "",
    val countryId: String = "uk",
    val currencyId: String = "gbp",
    val nicotineWeeklySpendMinor: Long = 0L,
    val alcoholWeeklySpendMinor: Long = 0L,
    val vapeSessionsPerDay: Int = 0,
    val puffsPerDay: Int = 0,
    val nicotineStrengthMgPerMl: Double = 0.0,
    val drinkBasisId: String = "uk_units",
    val drinksPerWeek: Double = 0.0,
    val savingsGoalName: String = "",
    val savingsGoalMinor: Long = 0L,
    val bibleVersionId: String = "web",
)

@Serializable
data class BackupSlip(
    val atMillis: Long,
    val substances: List<String>,
    val triggerId: String? = null,
    val reflection: String? = null,
    val nextChange: String? = null,
)

@Serializable
data class BackupCraving(
    val atMillis: Long,
    val outcome: String,
    val secondsHeld: Int,
    val triggerId: String? = null,
)

@Serializable
data class BackupCheckIn(
    val epochDay: Long,
    val moodScore: Int,
    val cravingLevel: Int,
    val stressLevel: Int,
    val stayedNicotineFree: Boolean? = null,
    val stayedAlcoholFree: Boolean? = null,
    val note: String = "",
)

@Serializable
data class BackupJournalEntry(
    val createdAtMillis: Long,
    val prompt: String = "",
    val body: String = "",
)

@Serializable
data class BackupFamilyMessage(
    val createdAtMillis: Long,
    val text: String,
)

@Serializable
data class BackupPlanTask(
    val taskId: String,
    val slotId: String,
    val title: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)

@Serializable
data class BackupPlanCompletion(
    val epochDay: Long,
    val taskId: String,
)

/**
 * Everything worth carrying to a new phone. Note what is *not* here: the journal
 * PIN, which is deliberately not exported, and the family photo, which stays on
 * the device it was chosen on.
 */
@Serializable
data class BackupPayload(
    val formatVersion: Int = 1,
    val exportedAtMillis: Long = 0L,
    val profile: BackupProfile = BackupProfile(),
    val slips: List<BackupSlip> = emptyList(),
    val cravings: List<BackupCraving> = emptyList(),
    val checkIns: List<BackupCheckIn> = emptyList(),
    val journal: List<BackupJournalEntry> = emptyList(),
    val familyMessages: List<BackupFamilyMessage> = emptyList(),
    val favouriteVerses: List<String> = emptyList(),
    val selectedTriggerIds: List<String> = emptyList(),
    val storyAchievementIds: List<String> = emptyList(),
    val planTasks: List<BackupPlanTask> = emptyList(),
    val planCompletions: List<BackupPlanCompletion> = emptyList(),
)

object BackupCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun toBytes(payload: BackupPayload): ByteArray =
        json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)

    fun fromBytes(bytes: ByteArray): BackupPayload = try {
        json.decodeFromString(BackupPayload.serializer(), bytes.toString(Charsets.UTF_8))
    } catch (e: Exception) {
        throw BackupCrypto.BackupFormatException("This backup file could not be read.", e)
    }

    fun export(payload: BackupPayload, passphrase: CharArray): ByteArray =
        BackupCrypto.encrypt(toBytes(payload), passphrase)

    fun import(file: ByteArray, passphrase: CharArray): BackupPayload =
        fromBytes(BackupCrypto.decrypt(file, passphrase))

    /** Suggested filename: sortable, and obvious a year later what it is. */
    fun suggestedFileName(year: Int, month: Int, day: Int): String {
        fun pad(v: Int) = v.toString().padStart(2, '0')
        return "dads-victory-backup-$year-${pad(month)}-${pad(day)}.dvbk"
    }
}
