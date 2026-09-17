package com.mono.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How much you have listened, over a window you choose.
 *
 * One hero figure for the total, four stat tiles, a column chart of when the
 * listening happened, then the two top-five lists. The chart plots a single
 * series so it carries no legend — the heading says what is plotted — and only
 * the busiest column is labelled; the rest is a tap away, which is this screen's
 * answer to the desktop build's hover.
 */
@Composable
fun StatsScreen(activity: MainActivity) {
    var period by remember { mutableStateOf(Stats.DEFAULT_PERIOD) }

    // Re-read on anything that means the log has just been written to: a track
    // change, a pause, or thirty seconds of playing (the service banks a
    // still-playing track on that same cadence).
    val summary = remember(
        period,
        activity.songs,
        activity.isPlaying,
        activity.current?.docId,
        activity.positionMs / 30_000L,
    ) {
        val plays = activity.store.plays()
        val range = Stats.rangeFor(period, plays)
        Stats.summarise(
            plays = plays,
            tracks = activity.songs.map { TrackRef(it.docId, it.title, it.artist) },
            from = range.from,
            to = range.to,
            grain = range.grain,
        )
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, bottom = 4.dp)) {
                Text(
                    "Listening",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Counted while audio is actually playing, not by track length.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Stats.periods, key = { it.id }) { entry ->
                    val active = entry.id == period
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { period = entry.id }
                            .padding(horizontal = 15.dp, vertical = 8.dp),
                    ) {
                        Text(
                            entry.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }

        if (summary.playCount == 0) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (period == "all") {
                            "Nothing logged yet. Play something and it will show up here."
                        } else {
                            "Nothing played in this period."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@LazyColumn
        }

        item { Hero(summary.totalMs) }

        item {
            val perActiveDay =
                if (summary.activeDays > 0) summary.totalMs / summary.activeDays else 0L
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Songs played", summary.playCount.toString(), Modifier.weight(1f))
                    StatTile("Different tracks", summary.uniqueCount.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Days listened", summary.activeDays.toString(), Modifier.weight(1f))
                    StatTile(
                        "Average a day",
                        Stats.formatSpan(perActiveDay),
                        Modifier.weight(1f),
                        note = "on days you listened",
                    )
                }
            }
        }

        item { ColumnChart(summary.buckets) }

        if (summary.topTracks.isNotEmpty()) {
            item {
                TopList(
                    title = "Top songs",
                    entries = summary.topTracks.map {
                        TopEntry(it.title, it.artist, it.ms)
                    },
                )
            }
        }

        if (summary.topArtists.isNotEmpty()) {
            item {
                TopList(
                    title = "Top artists",
                    entries = summary.topArtists.map {
                        TopEntry(it.artist, if (it.count == 1) "1 play" else "${it.count} plays", it.ms)
                    },
                )
            }
        }
    }
}

@Composable
private fun Hero(totalMs: Long) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 20.dp),
    ) {
        Text(
            "TIME LISTENED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            Stats.formatSpan(totalMs),
            fontSize = 38.sp,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Time per bucket. Bars are capped in width so a short period does not render
 * slabs, separated by a small gap in the page colour rather than by strokes, and
 * rounded only at the data end.
 */
@Composable
private fun ColumnChart(buckets: List<BucketSlice>) {
    val peak = buckets.maxOfOrNull { it.ms }?.coerceAtLeast(1L) ?: 1L
    val peakIndex = buckets.indexOfFirst { it.ms == peak }
    var picked by remember(buckets.size) { mutableStateOf<Int?>(null) }

    val shown = buckets.getOrNull(picked ?: peakIndex)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Text(
            "When you listened",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (shown == null) {
                "Tap a column to read it."
            } else if (picked != null) {
                "${shown.label} · ${Stats.formatSpan(shown.ms)}"
            } else {
                "Busiest: ${shown.label} · ${Stats.formatSpan(shown.ms)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (picked != null) MonoColors.Chart else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))

        val gap = 3.dp
        BoxWithConstraints(Modifier.fillMaxWidth().height(132.dp)) {
            // 24dp is as wide as a bar wants to be; a week of columns would
            // otherwise render as slabs.
            val slot = (maxWidth - gap * (buckets.size - 1).coerceAtLeast(0)) / buckets.size.coerceAtLeast(1)
            val barWidth = if (slot < 24.dp) slot else 24.dp

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Bottom,
            ) {
                buckets.forEachIndexed { index, bucket ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { picked = if (picked == index) null else index },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        // A column with no listening still gets its slot, so
                        // the gaps in a week read as gaps.
                        val fraction = if (bucket.ms > 0) {
                            (bucket.ms.toFloat() / peak).coerceAtLeast(0.015f)
                        } else {
                            0f
                        }
                        if (fraction > 0f) {
                            Box(
                                Modifier
                                    .width(barWidth)
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        // Nothing is dimmed until a column is
                                        // picked: the default view is one colour.
                                        if (picked == null || picked == index) MonoColors.Chart
                                        else MonoColors.Chart.copy(alpha = 0.4f),
                                    ),
                            )
                        } else {
                            // A hairline where the baseline would be, so an
                            // empty bucket is visibly empty rather than absent.
                            Box(
                                Modifier
                                    .width(barWidth)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outline),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Axis(buckets)
    }
}

/**
 * Every bucket gets a label while they still fit; beyond that only the ends and
 * the middle, which is all a phone-width axis has room for.
 */
@Composable
private fun Axis(buckets: List<BucketSlice>) {
    val tickStyle = MaterialTheme.typography.labelSmall
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (buckets.size <= 8) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (bucket in buckets) {
                Text(
                    bucket.label,
                    style = tickStyle,
                    color = tickColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        val ends = listOf(buckets.first(), buckets[buckets.size / 2], buckets.last())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (bucket in ends) {
                Text(
                    bucket.label,
                    style = tickStyle,
                    color = tickColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private data class TopEntry(val title: String, val subtitle: String, val ms: Long)

@Composable
private fun TopList(title: String, entries: List<TopEntry>) {
    val peak = entries.firstOrNull()?.ms?.coerceAtLeast(1L) ?: 1L

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 18.dp, end = 16.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(18.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(7.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((entry.ms.toFloat() / peak).coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MonoColors.Chart),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Stats.formatSpan(entry.ms),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
