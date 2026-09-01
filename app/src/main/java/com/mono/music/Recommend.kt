package com.mono.music

/** A titled row of suggestions on the For you tab. */
data class Section(val title: String, val subtitle: String, val songs: List<Song>)

/**
 * Builds the For you tab out of what is actually known about this library:
 * the tracks, the likes, and the play history. There is no service to ask, so a
 * suggestion here means "you have this, and here is why it is worth playing" —
 * what you keep going back to, what you have not heard in a while, and more by
 * the artists you liked.
 */
object Recommend {

    private const val ROW = 12

    fun sections(
        songs: List<Song>,
        favorites: Set<String>,
        playCounts: Map<String, Int>,
        lastPlayed: Map<String, Long>,
        day: Long = System.currentTimeMillis() / 86_400_000L,
    ): List<Section> {
        if (songs.isEmpty()) return emptyList()
        val out = mutableListOf<Section>()

        val recent = songs
            .filter { lastPlayed[it.docId] != null }
            .sortedByDescending { lastPlayed[it.docId] ?: 0L }
            .take(ROW)
        if (recent.isNotEmpty()) {
            out.add(Section("Recently played", "Pick up where you left off", recent))
        }

        val mostPlayed = songs
            .filter { (playCounts[it.docId] ?: 0) > 1 }
            .sortedByDescending { playCounts[it.docId] ?: 0 }
            .take(ROW)
        if (mostPlayed.isNotEmpty()) {
            out.add(Section("On repeat", "Your most played", mostPlayed))
        }

        val liked = songs.filter { it.docId in favorites }
        if (liked.isNotEmpty()) {
            out.add(Section("Liked songs", "${liked.size} tracks", liked))

            // More by the artists behind the likes — the tracks themselves
            // excluded, since those are one row up.
            val likedArtists = liked.map { it.artist.lowercase() }.toSet()
            val moreLikeThis = songs
                .filter { it.artist.lowercase() in likedArtists && it.docId !in favorites }
                .sortedByDescending { playCounts[it.docId] ?: 0 }
                .take(ROW)
            if (moreLikeThis.isNotEmpty()) {
                val names = liked.map { it.artist }.distinct()
                val because = if (names.size == 1) {
                    "Because you like ${names.first()}"
                } else {
                    "Based on what you liked"
                }
                out.add(Section("More like this", because, moreLikeThis))
            }
        }

        val added = songs.filter { it.addedAt > 0 }.sortedByDescending { it.addedAt }.take(ROW)
        if (added.isNotEmpty()) {
            out.add(Section("Recently added", "Newest in your folder", added))
        }

        // A rotating handful of tracks that have never been played. The window
        // moves with the day so the row is not the same every time the tab is
        // opened, without needing anything stored.
        val unplayed = songs.filter { playCounts[it.docId] == null }.sortedBy { it.title.lowercase() }
        if (unplayed.size > 2) {
            val start = ((day % unplayed.size) + unplayed.size) % unplayed.size
            val window = (0 until minOf(ROW, unplayed.size)).map {
                unplayed[((start + it) % unplayed.size).toInt()]
            }
            out.add(Section("Never played", "Buried in your library", window))
        }

        return out
    }
}
