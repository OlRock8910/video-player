package com.mono.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
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
    var addToPlaylistFor by remember { mutableStateOf<Song?>(null) }
    var namingPlaylist by remember { mutableStateOf(false) }

    if (activity.store.folderUri.isNullOrBlank()) {
        Welcome(onPickFolder)
        return
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Header(
                scanning = activity.scanning,
                onPickFolder = onPickFolder,
                onRescan = { activity.rescan() },
            )

            SearchField(query, { query = it })

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (entry in Tab.entries) {
                    Chip(
                        label = entry.label,
                        selected = tab == entry,
                        onClick = {
                            tab = entry
                            openArtist = null
                            openPlaylist = null
                        },
                    )
                }
            }

            Box(Modifier.weight(1f)) {
                when {
                    activity.scanning && songs.isEmpty() -> Loading()

                    openPlaylist != null -> {
                        val name = openPlaylist!!
                        val ids = if (name == LIKED) {
                            activity.favorites
                        } else {
                            remember(name, activity.playlistsVersion) {
                                activity.store.playlist(name).toSet()
                            }
                        }
                        val list = songs.filter { it.docId in ids }
                        SongList(
                            activity = activity,
                            title = name,
                            songs = list,
                            onBack = { openPlaylist = null },
                            onMore = { addToPlaylistFor = it },
                            removeFrom = if (name == LIKED) null else name,
                        )
                    }

                    openArtist != null -> {
                        val name = openArtist!!
                        SongList(
                            activity = activity,
                            title = name,
                            songs = songs.filter { it.artist == name },
                            onBack = { openArtist = null },
                            onMore = { addToPlaylistFor = it },
                        )
                    }

                    tab == Tab.ForYou -> ForYouScreen(activity, matching)

                    tab == Tab.Songs -> SongList(
                        activity = activity,
                        title = "Songs",
                        songs = matching,
                        onMore = { addToPlaylistFor = it },
                    )

                    tab == Tab.Artists -> ArtistsScreen(matching) { openArtist = it }

                    else -> PlaylistsScreen(
                        activity = activity,
                        likedCount = activity.favorites.size,
                        onOpen = { openPlaylist = it },
                        onCreate = { namingPlaylist = true },
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

    addToPlaylistFor?.let { song ->
        AddToPlaylistDialog(
            names = remember(activity.playlistsVersion) { activity.store.playlistNames() },
            onDismiss = { addToPlaylistFor = null },
            onPick = { name ->
                activity.store.addToPlaylist(name, listOf(song.docId))
                activity.bumpPlaylists()
                addToPlaylistFor = null
            },
            onCreate = {
                addToPlaylistFor = null
                namingPlaylist = true
            },
        )
    }

    if (namingPlaylist) {
        NamePlaylistDialog(
            onDismiss = { namingPlaylist = false },
            onConfirm = { name ->
                if (name.isNotBlank()) {
                    activity.store.createPlaylist(name.trim())
                    activity.bumpPlaylists()
                }
                namingPlaylist = false
            },
        )
    }
}

@Composable
private fun Header(scanning: Boolean, onPickFolder: () -> Unit, onRescan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MonoColors.Accent))
        }
        Spacer(Modifier.width(10.dp))
        Text("MONO", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        if (scanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(12.dp))
        }
        TextButton(onClick = onRescan) { Text("Rescan") }
        TextButton(onClick = onPickFolder) { Text("Folder") }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text("Search songs and artists") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.background
            else MaterialTheme.colorScheme.onBackground,
        )
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
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MONO", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Point Mono at the folder your music lives in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
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

// --- Song list ------------------------------------------------------------

@Composable
private fun SongList(
    activity: MainActivity,
    title: String,
    songs: List<Song>,
    onMore: (Song) -> Unit,
    onBack: (() -> Unit)? = null,
    removeFrom: String? = null,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (onBack != null) {
                    Text(
                        "← Back",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onBack),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${songs.size} songs · ${formatLong(songs.sumOf { it.durationMs })}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (songs.isNotEmpty()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.onBackground)
                        .clickable { activity.playFrom(songs, songs.first()) }
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                ) {
                    Text(
                        "Play",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.background,
                    )
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
                        onPlay = { activity.playFrom(songs, song) },
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

@Composable
private fun SongRow(
    song: Song,
    playing: Boolean,
    liked: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(song, modifier = Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) MonoColors.Accent else MaterialTheme.colorScheme.onSurface,
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
        Text(
            formatClock(song.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onToggleLike) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (liked) "Unlike" else "Like",
                tint = if (liked) MonoColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    onClick = {
                        menu = false
                        onAddToPlaylist()
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

    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
        items(sections, key = { it.title }) { section ->
            Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(section.title, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            section.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { activity.playFrom(section.songs, section.songs.first()) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${section.title}")
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(section.songs, key = { it.docId }) { song ->
                        Column(
                            modifier = Modifier
                                .width(132.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { activity.playFrom(section.songs, song) },
                        ) {
                            AlbumArt(song, modifier = Modifier.size(132.dp), sizePx = 256, corner = 14)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodySmall,
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpen(artist) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArt(tracks.first(), modifier = Modifier.size(52.dp), corner = 26)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                icon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = MonoColors.Accent) },
                onClick = { onOpen(LIKED) },
                onDelete = null,
            )
        }
        items(names, key = { it }) { name ->
            PlaylistRow(
                name = name,
                subtitle = "${counts[name] ?: 0} songs",
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = { onOpen(name) },
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
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Create new playlist", style = MaterialTheme.typography.titleMedium)
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
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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

// --- Dialogs ---------------------------------------------------------------

@Composable
private fun AddToPlaylistDialog(
    names: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                if (names.isEmpty()) {
                    Text(
                        "No playlists yet.",
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
private fun NamePlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
