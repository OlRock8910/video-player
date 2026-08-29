package com.dadsvictory.ui.faith

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dadsvictory.domain.content.BibleVersion
import com.dadsvictory.domain.content.Scripture
import com.dadsvictory.domain.content.Sources
import com.dadsvictory.domain.content.Verse
import com.dadsvictory.domain.content.VerseTheme
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.EmptyState
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.theme.ScreenPadding

private const val FAVOURITES_FILTER = "favourites"

@Composable
fun FaithScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(VerseTheme.STRENGTH.id) }
    val version = state.settings.bibleVersion
    val dailyVerse = Scripture.daily(state.rotationIndex)

    val shown: List<Verse> = if (selectedFilter == FAVOURITES_FILTER) {
        Scripture.ALL.filter { it.reference in state.favouriteVerses }
    } else {
        Scripture.byTheme(VerseTheme.fromId(selectedFilter))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Faith", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }

        item {
            VictoryCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column {
                    Text(
                        "📖 Verse of the day",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "\"${dailyVerse.text(version)}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "— ${dailyVerse.reference} (${version.abbreviation})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        FavouriteButton(
                            saved = dailyVerse.reference in state.favouriteVerses,
                            onClick = { viewModel.toggleFavouriteVerse(dailyVerse.reference) },
                        )
                    }
                }
            }
        }

        item { SectionHeader("Translation") }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (option in BibleVersion.entries) {
                    FilterChip(
                        selected = version == option,
                        onClick = { viewModel.setBibleVersion(option) },
                        label = { Text(option.abbreviation) },
                    )
                }
            }
        }

        item {
            Text(
                version.fullName + " — " + version.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionHeader("Browse by what you need") }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedFilter == FAVOURITES_FILTER,
                    onClick = { selectedFilter = FAVOURITES_FILTER },
                    label = { Text("⭐ Favourites (${state.favouriteVerses.size})") },
                )
                for (theme in VerseTheme.entries) {
                    FilterChip(
                        selected = selectedFilter == theme.id,
                        onClick = { selectedFilter = theme.id },
                        label = { Text(theme.label) },
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
        }

        if (selectedFilter != FAVOURITES_FILTER) {
            item {
                Text(
                    VerseTheme.fromId(selectedFilter).blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (shown.isEmpty()) {
            item {
                EmptyState(
                    emoji = "⭐",
                    title = "No favourites yet",
                    body = "Tap the heart on any verse and it will be waiting here when you need it.",
                )
            }
        } else {
            items(shown, key = { it.reference }) { verse ->
                VerseCard(
                    verse = verse,
                    version = version,
                    saved = verse.reference in state.favouriteVerses,
                    onToggleFavourite = { viewModel.toggleFavouriteVerse(verse.reference) },
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            InfoNote(Sources.SCRIPTURE_LICENSING)
        }
    }
}

@Composable
private fun VerseCard(
    verse: Verse,
    version: BibleVersion,
    saved: Boolean,
    onToggleFavourite: () -> Unit,
) {
    VictoryCard {
        Column {
            Text("\"${verse.text(version)}\"", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "— ${verse.reference} (${version.abbreviation})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        verse.theme.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FavouriteButton(saved = saved, onClick = onToggleFavourite)
            }
        }
    }
}

/** Heart plus a spoken label, so "saved" is never carried by the colour alone. */
@Composable
private fun FavouriteButton(saved: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.width(48.dp)) {
        Icon(
            imageVector = if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (saved) "Saved to favourites. Tap to remove." else "Save to favourites",
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
