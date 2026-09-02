package com.mono.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class Tab(val label: String) {
    ForYou("For you"),
    Songs("Songs"),
    Artists("Artists"),
    Playlists("Playlists"),
}

/** Name of the built-in likes playlist, kept out of the user's own names. */
private const val LIKED = "Liked songs"

@Composable
fun MonoApp(activity: MainActivity, onPickFolder: () -> Unit) {
    val songs = activity.songs
    val current = activity.current

    var tab by remember { mutableStateOf(Tab.ForYou) }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var openArtist by remember { mutableStateOf<String?>(null) }
    var openPlaylist by remember { mutableStateOf<String?>(null) }

    // Multi-select. Holds document ids so a rescan cannot invalidate it.
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Tracks waiting for a playlist to be picked or created for them: one song
    // from a row menu, or everything currently selected.
    var pendingAdd by remember { mutableStateOf<List<String>?>(null) }
    var namingPlaylist by remember { mutableStateOf(false) }

    // Surface, not Box: it publishes the content colour for everything inside,
    // so headings that do not name a colour inherit one that suits the theme
    // instead of Material's black-on-anything default.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (activity.store.folderUri.isNullOrBlank()) {
            Welcome(onPickFolder)
            return@Surface
        }

        val matching = remember(songs, query) {
            if (query.isBlank()) {
                songs
            } else {
                val needle = query.trim().lowercase()
                songs.filter {
                    needle in it.title.lowercase() ||
                        needle in it.artist.lowercase() ||
                        needle in it.folder.lowercase()
                }
            }
        }

        /**
         * The list the current screen is showing, for select-all and bulk
         * actions. Computed in one unconditional remember rather than per
         * branch, so the branches never move Compose's remember slots around.
         */
        val visible: List<Song> = remember(
            songs,
            matching,
            openPlaylist,
            openArtist,
            activity.favorites,
            activity.playlistsVersion,
        ) {
            when {
                openPlaylist != null -> {
                    val name = openPlaylist!!
                    val ids = if (name == LIKED) {
                        activity.favorites
                    } else {
                        activity.store.playlist(name).toSet()
                    }
                    songs.filter { it.docId in ids }
                }

                openArtist != null -> songs.filter { it.artist == openArtist }
                else -> matching
            }
        }

        val selected = remember(selection, songs) { songs.filter { it.docId in selection } }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if (selection.isNotEmpty()) {
                    SelectionBar(
                        count = selection.size,
                        allSelected = visible.isNotEmpty() && visible.all { it.docId in selection },
                        onClear = { selection = emptySet() },
                        onSelectAll = {
                            selection = if (visible.all { it.docId in selection }) {
                                emptySet()
                            } else {
                                visible.map { it.docId }.toSet()
                            }
                        },
                        onPlay = {
                            activity.playFrom(selected, selected.firstOrNull())
                            selection = emptySet()
                        },
                        onShuffle = {
                            activity.playShuffled(selected)
                            selection = emptySet()
                        },
                        onAddToPlaylist = { pendingAdd = selection.toList() },
                    )
                } else {
                    Header(
                        scanning = activity.scanning,
                        onPickFolder = onPickFolder,
                        onRescan = { activity.rescan() },
                    )
                    SearchField(query, { query = it })
                    TabRow(
                        selected = tab,
                        onSelect = {
                            tab = it
                            openArtist = null
                            openPlaylist = null
                        },
                    )
                }

                Box(Modifier.weight(1f)) {
                    val onToggleSelect: (Song) -> Unit = { song ->
                        selection = if (song.docId in selection) {
                            selection - song.docId
                        } else {
                            selection + song.docId
                        }
                    }

                    when {
                        activity.scanning && songs.isEmpty() -> Loading()

                        openPlaylist != null -> SongList(
                            activity = activity,
                            title = openPlaylist!!,
                            songs = visible,
                            selection = selection,
                            onToggleSelect = onToggleSelect,
                            onMore = { pendingAdd = listOf(it.docId) },
                            onBack = { openPlaylist = null },
                            removeFrom = openPlaylist!!.takeIf { it != LIKED },
                        )

                        openArtist != null -> SongList(
                            activity = activity,
                            title = openArtist!!,
                            songs = visible,
                            selection = selection,
                            onToggleSelect = onToggleSelect,
                            onMore = { pendingAdd = listOf(it.docId) },
                            onBack = { openArtist = null },
                        )

                        tab == Tab.ForYou -> ForYouScreen(activity, matching)

                        tab == Tab.Songs -> SongList(
                            activity = activity,
                            title = "Songs",
                            songs = matching,
                            selection = selection,
                            onToggleSelect = onToggleSelect,
                            onMore = { pendingAdd = listOf(it.docId) },
                        )

                        tab == Tab.Artists -> ArtistsScreen(matching) { openArtist = it }

                        else -> PlaylistsScreen(
                            activity = activity,
                            likedCount = activity.favorites.size,
                            onOpen = { openPlaylist = it },
                            onCreate = {
                                pendingAdd = null
                                namingPlaylist = true
                            },
                        )
                    }
                }

                if (current != null) {
                    NowPlayingBar(
                        song = current,
                        playing = activity.isPlaying,
                        positionMs = activity.positionMs,
                        durationMs = activity.durationMs,
                        onExpand = { expanded = true },
                        onPlayPause = { activity.togglePlay() },
                        onNext = { activity.next() },
                    )
                }
            }

            NowPlayingSheet(visible = expanded && current != null) {
                current?.let { song ->
                    NowPlayingScreen(
                        song = song,
                        playing = activity.isPlaying,
                        positionMs = activity.positionMs,
                        durationMs = activity.durationMs,
                        shuffle = activity.shuffle,
                        repeat = activity.repeat,
                        liked = activity.isFavorite(song),
                        onCollapse = { expanded = false },
                        onPlayPause = { activity.togglePlay() },
                        onNext = { activity.next() },
                        onPrevious = { activity.previous() },
                        onSeek = { activity.seekTo(it) },
                        onToggleShuffle = { activity.toggleShuffle() },
                        onCycleRepeat = { activity.cycleRepeat() },
                        onToggleLike = { activity.toggleFavorite(song) },
                    )
                }
            }
        }

        pendingAdd?.let { docIds ->
            AddToPlaylistDialog(
                count = docIds.size,
                names = remember(activity.playlistsVersion) { activity.store.playlistNames() },
                onDismiss = { pendingAdd = null },
                onPick = { name ->
                    activity.store.addToPlaylist(name, docIds)
                    activity.bumpPlaylists()
                    pendingAdd = null
                    selection = emptySet()
                },
                onCreate = { namingPlaylist = true },
            )
        }

        if (namingPlaylist) {
            NamePlaylistDialog(
                trackCount = pendingAdd?.size ?: 0,
                onDismiss = { namingPlaylist = false },
                onConfirm = { name ->
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        activity.store.createPlaylist(trimmed)
                        pendingAdd?.let { activity.store.addToPlaylist(trimmed, it) }
                        activity.bumpPlaylists()
                        selection = emptySet()
                    }
                    pendingAdd = null
                    namingPlaylist = false
                },
            )
        }
    }
}

// --- Chrome ---------------------------------------------------------------

@Composable
private fun Header(scanning: Boolean, onPickFolder: () -> Unit, onRescan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(MonoColors.Accent))
        }
        Spacer(Modifier.width(9.dp))
        Text(
            "MONO",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (scanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(8.dp))
        }
        TextButton(onClick = onRescan) {
            Text("Rescan", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = onPickFolder) {
            Text("Folder", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Shown in place of the header while tracks are selected. */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
        }
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSelectAll) {
            Icon(
                Icons.Filled.Check,
                contentDescription = if (allSelected) "Select none" else "Select all",
                tint = if (allSelected) MonoColors.Accent else MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = onShuffle) {
            Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle selection")
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play selection")
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist")
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        placeholder = {
            Text(
                "Search songs and artists",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        shape = RoundedCornerShape(26.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MonoColors.Accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * Scrolls sideways rather than wrapping. Four fixed chips did not fit the width
 * of a phone, and the last one broke mid-word ("Playlis / ts").
 */
@Composable
private fun TabRow(selected: Tab, onSelect: (Tab) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Tab.entries.toList(), key = { it.name }) { entry ->
            val active = entry == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
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

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Welcome(onPickFolder: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "MONO",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Point Mono at the folder your music lives in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.onBackground)
                    .clickable(onClick = onPickFolder)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(
                    "Choose folder",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.background,
                )
            }
        }
    }
}

/** The pill used for Play, and its outlined twin for Shuffle. */
@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (filled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) MaterialTheme.colorScheme.background
            else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// --- Song list ------------------------------------------------------------

@Composable
private fun SongList(
    activity: MainActivity,
    title: String,
    songs: List<Song>,
    selection: Set<String>,
    onToggleSelect: (Song) -> Unit,
    onMore: (Song) -> Unit,
    onBack: (() -> Unit)? = null,
    removeFrom: String? = null,
) {
    val selecting = selection.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, bottom = 10.dp)) {
            if (onBack != null) {
                Text(
                    "← Back",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onBack).padding(vertical = 2.dp),
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${songs.size} songs · ${formatLong(songs.sumOf { it.durationMs })}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (songs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Play", filled = true) { activity.playFrom(songs, songs.first()) }
                    PillButton("Shuffle", filled = false) { activity.playShuffled(songs) }
                }
            }
        }

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing here yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(songs, key = { it.docId }) { song ->
                    SongRow(
                        song = song,
                        playing = activity.current?.docId == song.docId,
                        liked = activity.isFavorite(song),
                        selecting = selecting,
                        checked = song.docId in selection,
                        onPlay = {
                            if (selecting) onToggleSelect(song) else activity.playFrom(songs, song)
                        },
                        onLongPress = { onToggleSelect(song) },
                        onToggleLike = { activity.toggleFavorite(song) },
                        onAddToPlaylist = { onMore(song) },
                        onRemove = removeFrom?.let {
                            {
                                activity.store.removeFromPlaylist(it, song.docId)
                                activity.bumpPlaylists()
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    playing: Boolean,
    liked: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (checked) MonoColors.Accent.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surface,
            )
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress)
            .padding(start = 10.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AlbumArt(song, modifier = Modifier.size(50.dp), corner = 12)
            if (selecting) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = if (checked) 0.55f else 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))

        // The title gets the slack. In the first pass the duration, the heart
        // and the overflow button all sat at fixed size beside it, which cut
        // titles down to "After The St…" on a normal phone.
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) MonoColors.Accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                "${song.artist} · ${formatClock(song.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!selecting) {
            IconButton(onClick = onToggleLike, modifier = Modifier.size(38.dp)) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = if (liked) MonoColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        onClick = {
                            menu = false
                            onAddToPlaylist()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Select") },
                        onClick = {
                            menu = false
                            onLongPress()
                        },
                    )
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from this playlist") },
                            onClick = {
                                menu = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
    }
}

// --- For you --------------------------------------------------------------

@Composable
private fun ForYouScreen(activity: MainActivity, songs: List<Song>) {
    val sections = remember(songs, activity.favorites, activity.current?.docId) {
        Recommend.sections(
            songs = songs,
            favorites = activity.favorites,
            playCounts = activity.store.playCounts(),
            lastPlayed = activity.store.lastPlayed(),
        )
    }

    if (sections.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Play a few tracks and this fills up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(sections, key = { it.title }) { section ->
            Column(Modifier.padding(top = 6.dp, bottom = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            section.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { activity.playShuffled(section.songs) }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle ${section.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { activity.playFrom(section.songs, section.songs.first()) }) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play ${section.title}",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(section.songs, key = { it.docId }) { song ->
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { activity.playFrom(section.songs, song) }
                                .padding(bottom = 2.dp),
                        ) {
                            AlbumArt(song, modifier = Modifier.size(140.dp), sizePx = 256, corner = 16)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                song.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Artists and playlists -------------------------------------------------

@Composable
private fun ArtistsScreen(songs: List<Song>, onOpen: (String) -> Unit) {
    val artists = remember(songs) {
        songs.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(artists, key = { it.first }) { (artist, tracks) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpen(artist) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(tracks.first(), modifier = Modifier.size(50.dp), corner = 25)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${tracks.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsScreen(
    activity: MainActivity,
    likedCount: Int,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val names = remember(activity.playlistsVersion) { activity.store.playlistNames() }
    val counts = remember(activity.playlistsVersion) {
        names.associateWith { activity.store.playlist(it).size }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            PlaylistRow(
                name = LIKED,
                subtitle = "$likedCount songs",
                icon = {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = MonoColors.Accent)
                },
                onClick = { onOpen(LIKED) },
                onShuffleOrder = null,
                onDelete = null,
            )
        }
        items(names, key = { it }) { name ->
            PlaylistRow(
                name = name,
                subtitle = "${counts[name] ?: 0} songs",
                icon = {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { onOpen(name) },
                onShuffleOrder = {
                    activity.store.setPlaylist(name, activity.store.playlist(name).shuffled())
                    activity.bumpPlaylists()
                },
                onDelete = {
                    activity.store.deletePlaylist(name)
                    activity.bumpPlaylists()
                },
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MonoColors.Accent)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Create new playlist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    name: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onShuffleOrder: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null || onShuffleOrder != null) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (onShuffleOrder != null) {
                        DropdownMenuItem(
                            text = { Text("Shuffle order") },
                            onClick = {
                                menu = false
                                onShuffleOrder()
                            },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

// --- Dialogs ---------------------------------------------------------------

@Composable
private fun AddToPlaylistDialog(
    count: Int,
    names: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Add to playlist" else "Add $count songs") },
        text = {
            Column {
                if (names.isEmpty()) {
                    Text(
                        "No playlists yet — make one below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (name in names) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(name) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onCreate) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NamePlaylistDialog(
    trackCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            Column {
                if (trackCount > 0) {
                    Text(
                        if (trackCount == 1) "1 song will be added." else "$trackCount songs will be added.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Name") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
