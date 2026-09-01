package com.mono.music

import android.content.ComponentName
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Referencing PlaybackService (a MediaSessionService) is an opt-in API.
@UnstableApi
class MainActivity : ComponentActivity() {

    lateinit var store: Store
        private set

    private var controllerFuture: ListenableFuture<MediaController>? = null

    var player by mutableStateOf<MediaController?>(null)
        private set

    /** Everything found under the chosen folder. */
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set

    var scanning by mutableStateOf(false)
        private set

    /** The list the current track was started from — an album, a playlist, a search. */
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set

    var current by mutableStateOf<Song?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var positionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    var shuffle by mutableStateOf(false)
        private set

    /** One of "off", "all", "one". */
    var repeat by mutableStateOf("off")
        private set

    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Bumped whenever playlists change, to re-read them from the store. */
    var playlistsVersion by mutableIntStateOf(0)
        private set

    private var favoritesWatcher: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            store.folderUri = uri.toString()
            rescan()
        }
    }

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(this)
        shuffle = store.shuffle
        repeat = store.repeat
        favorites = store.favorites()

        // Without this the media notification never appears, so the skip
        // buttons the lock screen shows have nowhere to come from.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // A like from the notification lands in the same prefs file this reads.
        favoritesWatcher = store.onFavoritesChanged { favorites = store.favorites() }

        if (!store.folderUri.isNullOrBlank()) rescan()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    player?.let {
                        positionMs = it.currentPosition.coerceAtLeast(0)
                        val length = it.duration
                        if (length > 0) durationMs = length
                    }
                    delay(POSITION_TICK_MS)
                }
            }
        }

        setContent {
            MonoTheme {
                MonoApp(activity = this, onPickFolder = { pickFolder.launch(null) })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectToService()
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        player = null
    }

    override fun onDestroy() {
        favoritesWatcher?.let { store.stopWatching(it) }
        favoritesWatcher = null
        super.onDestroy()
    }

    private fun connectToService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                player = controller
                controller.addListener(playerListener)
                syncFromPlayer(controller)
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    /**
     * The service may have been playing all along without the activity. Adopt
     * whatever it is doing rather than resetting it.
     */
    private fun syncFromPlayer(controller: MediaController) {
        isPlaying = controller.isPlaying
        shuffle = controller.shuffleModeEnabled
        repeat = when (controller.repeatMode) {
            Player.REPEAT_MODE_ALL -> "all"
            Player.REPEAT_MODE_ONE -> "one"
            else -> "off"
        }
        durationMs = controller.duration.coerceAtLeast(0)
        positionMs = controller.currentPosition.coerceAtLeast(0)
        adoptCurrentItem(controller)
    }

    private fun adoptCurrentItem(controller: MediaController) {
        val docId = controller.currentMediaItem?.mediaId ?: return
        current = queue.firstOrNull { it.docId == docId }
            ?: songs.firstOrNull { it.docId == docId }
            ?: current
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val docId = mediaItem?.mediaId ?: return
            current = queue.firstOrNull { it.docId == docId }
                ?: songs.firstOrNull { it.docId == docId }
                        ?: current
            player?.let { positionMs = it.currentPosition.coerceAtLeast(0) }
        }

        override fun onPlaybackStateChanged(state: Int) {
            val controller = player ?: return
            if (state == Player.STATE_READY) {
                durationMs = controller.duration.coerceAtLeast(0)
            }
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            shuffle = enabled
        }

        override fun onRepeatModeChanged(mode: Int) {
            repeat = when (mode) {
                Player.REPEAT_MODE_ALL -> "all"
                Player.REPEAT_MODE_ONE -> "one"
                else -> "off"
            }
        }
    }

    fun rescan() {
        val saved = store.folderUri ?: return
        val tree = runCatching { Uri.parse(saved) }.getOrNull() ?: return
        scanning = true
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { scanMusic(this@MainActivity, tree) }
            songs = found
            scanning = false
            player?.let { adoptCurrentItem(it) }
            restoreIfIdle()
        }
    }

    fun chooseFolder() = pickFolder.launch(null)

    /**
     * On a cold start there is nothing loaded in the player. Put the library
     * back in, parked at the track that was playing last, so the bar at the
     * bottom is ready to press instead of empty.
     */
    private fun restoreIfIdle() {
        val controller = player ?: return
        if (controller.mediaItemCount > 0 || songs.isEmpty()) return
        val docId = store.resumeDocId() ?: return
        val index = songs.indexOfFirst { it.docId == docId }
        if (index < 0) return
        queue = songs
        current = songs[index]
        controller.setMediaItems(songs.map(::mediaItemFor), index, store.resumePositionMs())
        controller.shuffleModeEnabled = shuffle
        controller.repeatMode = repeatModeOf(repeat)
        controller.prepare()
    }

    /**
     * Artwork is referenced by uri rather than attached as bytes, so building a
     * queue of hundreds of tracks costs nothing; the session decodes the cover
     * for the track it is actually showing. See [ArtBitmapLoader].
     */
    private fun mediaItemFor(song: Song): MediaItem = MediaItem.Builder()
        .setUri(song.uri)
        .setMediaId(song.docId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.folder.ifBlank { null })
                .setArtworkUri(song.uri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()

    /**
     * Hands the whole list to the player rather than one track at a time. That
     * is what makes the player advance by itself with the screen off, and what
     * puts working next/previous buttons in the notification.
     */
    fun playFrom(list: List<Song>, start: Song?) {
        val controller = player ?: return
        if (list.isEmpty()) return
        queue = list
        val index = list.indexOfFirst { it.docId == start?.docId }.coerceAtLeast(0)
        current = list[index]
        durationMs = list[index].durationMs
        positionMs = 0L
        controller.setMediaItems(list.map(::mediaItemFor), index, 0L)
        controller.shuffleModeEnabled = shuffle
        controller.repeatMode = repeatModeOf(repeat)
        controller.prepare()
        controller.play()
    }

    fun togglePlay() {
        val controller = player ?: return
        when {
            controller.mediaItemCount == 0 -> playFrom(songs, songs.firstOrNull())
            controller.isPlaying -> controller.pause()
            else -> controller.play()
        }
    }

    fun next() {
        val controller = player ?: return
        // seekToNext wraps when repeat is on and stops at the end when it is
        // not, which is the behaviour the notification's button gives too.
        if (controller.hasNextMediaItem() || controller.repeatMode != Player.REPEAT_MODE_OFF) {
            controller.seekToNext()
        }
    }

    /** Restarts the track if it is more than a few seconds in, else steps back. */
    fun previous() {
        player?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms.coerceAtLeast(0))
        positionMs = ms.coerceAtLeast(0)
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        store.shuffle = shuffle
        player?.shuffleModeEnabled = shuffle
    }

    fun cycleRepeat() {
        repeat = when (repeat) {
            "off" -> "all"
            "all" -> "one"
            else -> "off"
        }
        store.repeat = repeat
        player?.repeatMode = repeatModeOf(repeat)
    }

    fun toggleFavorite(song: Song) {
        store.toggleFavorite(song.docId)
        favorites = store.favorites()
    }

    fun isFavorite(song: Song) = song.docId in favorites

    fun bumpPlaylists() {
        playlistsVersion++
    }

    private fun repeatModeOf(value: String) = when (value) {
        "all" -> Player.REPEAT_MODE_ALL
        "one" -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
    }
}
