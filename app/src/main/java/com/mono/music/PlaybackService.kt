package com.mono.music

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Where playback actually happens.
 *
 * The whole queue is handed to this service's player, so the phone can be
 * locked, or the app swiped away, and the player still advances to the next
 * track on its own and still answers the skip buttons in the notification.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var store: Store

    /** docId of the track the current play has already been counted for. */
    private var countedDocId: String? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                // Take audio focus: when a video, a call or another player
                // starts, this pauses instead of playing underneath it, and
                // ducks for short notification sounds.
                /* handleAudioFocus = */ true,
            )
            // Pause when headphones are pulled out or bluetooth drops, rather
            // than carrying on out loud.
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(playerListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setBitmapLoader(ArtBitmapLoader(this))
            .setCallback(callback)
            .build()

        refreshLikeButton()

        // Keep the notification's heart in step with likes made inside the app.
        favoritesWatcher = store.onFavoritesChanged { refreshLikeButton() }
    }

    private var favoritesWatcher: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            countedDocId = null
            refreshLikeButton()
            recordPlayIfStarted()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) recordPlayIfStarted() else saveResumePoint()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) recordPlayIfStarted()
        }
    }

    /**
     * A track counts as played the first time it actually starts, not each time
     * it is skipped past, so the For you tab reflects listening rather than
     * scrolling.
     */
    private fun recordPlayIfStarted() {
        val player = session?.player ?: return
        if (!player.isPlaying) return
        val docId = player.currentMediaItem?.mediaId ?: return
        if (docId == countedDocId) return
        countedDocId = docId
        store.recordPlay(docId)
    }

    private fun saveResumePoint() {
        val player = session?.player ?: return
        val docId = player.currentMediaItem?.mediaId ?: return
        store.saveResumePoint(docId, player.currentPosition)
    }

    // --- The like button in the notification ---------------------------------

    private fun likeButton(liked: Boolean) = CommandButton.Builder()
        .setDisplayName(getString(if (liked) R.string.unlike else R.string.like))
        .setIconResId(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
        .setSessionCommand(SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY))
        .setEnabled(true)
        .build()

    private fun refreshLikeButton() {
        val session = session ?: return
        val docId = session.player.currentMediaItem?.mediaId
        val liked = docId != null && store.isFavorite(docId)
        session.setCustomLayout(listOf(likeButton(liked)))
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY))
                .build()
            val docId = session.player.currentMediaItem?.mediaId
            val liked = docId != null && store.isFavorite(docId)
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(listOf(likeButton(liked)))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                val docId = session.player.currentMediaItem?.mediaId
                if (docId != null) {
                    store.toggleFavorite(docId)
                    refreshLikeButton()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away should not kill music that is still playing.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveResumePoint()
        favoritesWatcher?.let { store.stopWatching(it) }
        favoritesWatcher = null
        session?.let {
            it.player.removeListener(playerListener)
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE_LIKE = "com.mono.music.TOGGLE_LIKE"
    }
}
