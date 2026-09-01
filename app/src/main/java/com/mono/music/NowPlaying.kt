package com.mono.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads cover art off the main thread and holds it for as long as it is shown. */
@Composable
fun rememberArt(song: Song?, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(song?.docId, sizePx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(song?.docId, sizePx) {
        image = song?.let {
            withContext(Dispatchers.IO) { Art.load(context, it.uri, sizePx)?.asImageBitmap() }
        }
    }
    return image
}

/** Square cover with a lettered fallback for tracks that carry no art. */
@Composable
fun AlbumArt(song: Song?, modifier: Modifier = Modifier, sizePx: Int = Art.THUMB_PX, corner: Int = 10) {
    val image = rememberArt(song, sizePx)
    Box(
        modifier = modifier.clip(RoundedCornerShape(corner.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = song?.title?.trim()?.take(1)?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The cover as a spinning record: black disc, grooves, the art as the centre
 * label, spindle hole in the middle. It turns only while the music is playing
 * and freezes where it was when you pause, the way a real deck does.
 */
@Composable
fun VinylRecord(song: Song?, playing: Boolean, modifier: Modifier = Modifier) {
    val image = rememberArt(song, 512)
    var angle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val seconds = (now - last) / 1_000_000_000f
                last = now
                angle = (angle + seconds * DEGREES_PER_SECOND) % 360f
            }
        }
    }

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.fillMaxSize().rotate(angle),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2A2A2A), Color(0xFF0B0B0B)),
                        radius = radius,
                    ),
                    radius = radius,
                )
                // Grooves: faint rings between the label edge and the rim.
                for (i in 1..14) {
                    val t = i / 15f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.045f),
                        radius = radius * (0.66f + t * 0.33f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                    )
                }
            }

            // Centre label — the cover, cropped into the circle.
            Box(
                modifier = Modifier.fillMaxSize(0.60f).clip(CircleShape).background(MonoColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    androidx.compose.foundation.Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = song?.title?.trim()?.take(1)?.uppercase().orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                    )
                }
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF0B0B0B)))
            }
        }
    }
}

/** The bar pinned above the tab content; tapping it opens the full player. */
@Composable
fun NowPlayingBar(
    song: Song,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onExpand),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.outline,
            drawStopIndicator = {},
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(song, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatClock(positionMs)} / ${formatClock(durationMs)}  ·  ${song.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }
    }
}

/** The full player: blurred cover behind, vinyl in the middle, times and controls. */
@Composable
fun NowPlayingScreen(
    song: Song,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffle: Boolean,
    repeat: String,
    liked: Boolean,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
) {
    // Dragging owns the slider while the finger is down, so the twice-a-second
    // position tick cannot yank the thumb back mid-gesture.
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val length = durationMs.coerceAtLeast(song.durationMs)
    val shown = if (dragging) dragValue.toLong() else positionMs

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
        Backdrop(song)

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
                Text(
                    song.folder.ifBlank { "Now playing" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onToggleLike) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (liked) "Unlike" else "Like",
                        tint = if (liked) MonoColors.Accent else Color.White,
                    )
                }
            }

            Spacer(Modifier.weight(0.6f))
            VinylRecord(song, playing, modifier = Modifier.fillMaxWidth(0.82f))
            Spacer(Modifier.weight(0.5f))

            Text(
                song.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(18.dp))
            Slider(
                value = if (length > 0) shown.toFloat() else 0f,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    onSeek(dragValue.toLong())
                    dragging = false
                },
                valueRange = 0f..(if (length > 0) length.toFloat() else 1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatClock(shown),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Text(
                    formatClock(length),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffle) MonoColors.Accent else Color.White.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = Color(0xFF0B0B0B),
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (repeat == "one") Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeat == "off") Color.White.copy(alpha = 0.7f) else MonoColors.Accent,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The cover, blown up behind the player and darkened. A small bitmap stretched
 * over the screen is already soft, which keeps this readable on phones below
 * API 31 where [blur] does nothing.
 */
@Composable
private fun Backdrop(song: Song) {
    val image = rememberArt(song, Art.THUMB_PX)
    if (image != null) {
        androidx.compose.foundation.Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
            modifier = Modifier.fillMaxSize().blur(40.dp),
        )
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.55f),
                    Color.Black.copy(alpha = 0.75f),
                    Color.Black.copy(alpha = 0.92f),
                ),
            ),
        ),
    )
}

/** Slow enough to read as a turning record without pulling the eye off the song. */
private const val DEGREES_PER_SECOND = 45f

/** Wraps the full player in a slide-up transition over the library. */
@Composable
fun NowPlayingSheet(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        content()
    }
}
