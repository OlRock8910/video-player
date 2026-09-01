package com.mono.music

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the app remembers between launches, in one SharedPreferences file.
 *
 * The keys "folder_uri", "shuffle", "repeat" and "playlists" keep the exact
 * names and formats the first version of Mono wrote, so an existing install's
 * folder choice and playlists survive the upgrade.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("mono", Context.MODE_PRIVATE)

    var folderUri: String?
        get() = prefs.getString(KEY_FOLDER, null)
        set(value) = prefs.edit().putString(KEY_FOLDER, value).apply()

    var shuffle: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE, value).apply()

    /** One of "off", "all", "one". */
    var repeat: String
        get() = prefs.getString(KEY_REPEAT, "off") ?: "off"
        set(value) = prefs.edit().putString(KEY_REPEAT, value).apply()

    // --- Likes -------------------------------------------------------------
    //
    // Written from two places: the heart in the app, and the heart in the
    // media notification (which runs in PlaybackService). Both hold a Store on
    // the same prefs file, and each watches the other's writes through
    // onFavoritesChanged, so the two hearts never disagree.

    fun favorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    fun isFavorite(docId: String) = docId in favorites()

    /** Returns the state the track ended up in. */
    fun toggleFavorite(docId: String): Boolean {
        val next = favorites().toMutableSet()
        val liked = next.add(docId)
        if (!liked) next.remove(docId)
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
        return liked
    }

    fun onFavoritesChanged(block: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_FAVORITES) block()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun stopWatching(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    // --- Listening history -------------------------------------------------
    //
    // Feeds the For you tab: what you play most, what you played last, and
    // which artists to lean on when suggesting something you have not played.

    fun playCounts(): Map<String, Int> = readMap(KEY_PLAY_COUNTS) { it.toInt() }

    fun lastPlayed(): Map<String, Long> = readMap(KEY_LAST_PLAYED) { it.toLong() }

    /** Called once per track when playback of that track actually starts. */
    fun recordPlay(docId: String) {
        val counts = JSONObject(prefs.getString(KEY_PLAY_COUNTS, "{}") ?: "{}")
        counts.put(docId, counts.optInt(docId, 0) + 1)
        val played = JSONObject(prefs.getString(KEY_LAST_PLAYED, "{}") ?: "{}")
        played.put(docId, System.currentTimeMillis())
        prefs.edit()
            .putString(KEY_PLAY_COUNTS, counts.toString())
            .putString(KEY_LAST_PLAYED, played.toString())
            .apply()
    }

    private fun <T> readMap(key: String, parse: (String) -> T): Map<String, T> {
        val json = JSONObject(prefs.getString(key, "{}") ?: "{}")
        val out = mutableMapOf<String, T>()
        for (id in json.keys()) {
            runCatching { parse(json.getString(id)) }.getOrNull()?.let { out[id] = it }
        }
        return out
    }

    // --- Where playback was when the app was last closed --------------------

    fun saveResumePoint(docId: String, positionMs: Long) {
        prefs.edit()
            .putString(KEY_RESUME_DOC, docId)
            .putLong(KEY_RESUME_POSITION, positionMs)
            .apply()
    }

    fun resumeDocId(): String? = prefs.getString(KEY_RESUME_DOC, null)

    fun resumePositionMs(): Long = prefs.getLong(KEY_RESUME_POSITION, 0L)

    // --- Playlists ----------------------------------------------------------

    private fun raw() = JSONObject(prefs.getString(KEY_PLAYLISTS, "{}") ?: "{}")

    private fun save(json: JSONObject) {
        prefs.edit().putString(KEY_PLAYLISTS, json.toString()).apply()
    }

    fun playlistNames(): List<String> = raw().keys().asSequence().sortedBy { it.lowercase() }.toList()

    fun playlist(name: String): List<String> {
        val array = raw().optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it, null) }
    }

    fun createPlaylist(name: String): Boolean {
        val json = raw()
        if (json.has(name)) return false
        json.put(name, JSONArray())
        save(json)
        return true
    }

    fun addToPlaylist(name: String, docIds: List<String>) {
        val json = raw()
        val array = json.optJSONArray(name) ?: JSONArray()
        val existing = (0 until array.length()).map { array.optString(it) }.toMutableSet()
        for (id in docIds) if (existing.add(id)) array.put(id)
        json.put(name, array)
        save(json)
    }

    fun removeFromPlaylist(name: String, docId: String) {
        val json = raw()
        val array = json.optJSONArray(name) ?: return
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value != docId) kept.put(value)
        }
        json.put(name, kept)
        save(json)
    }

    fun deletePlaylist(name: String) {
        val json = raw()
        json.remove(name)
        save(json)
    }

    fun renamePlaylist(from: String, to: String) {
        val json = raw()
        if (!json.has(from) || json.has(to)) return
        json.put(to, json.optJSONArray(from) ?: JSONArray())
        json.remove(from)
        save(json)
    }

    private companion object {
        const val KEY_FOLDER = "folder_uri"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_FAVORITES = "favorites"
        const val KEY_PLAY_COUNTS = "play_counts"
        const val KEY_LAST_PLAYED = "last_played"
        const val KEY_RESUME_DOC = "resume_doc"
        const val KEY_RESUME_POSITION = "resume_position"
    }
}
