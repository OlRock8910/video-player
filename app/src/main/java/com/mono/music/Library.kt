package com.mono.music

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract

/**
 * One playable file found under the folder the user picked.
 *
 * [docId] is the Storage Access Framework document id. It is the stable
 * identity of a track across rescans, so it is what playlists, likes and play
 * counts are keyed on — never the title or the uri.
 */
data class Song(
    val uri: Uri,
    val docId: String,
    val fileName: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val folder: String,
    val addedAt: Long,
)

private data class Node(
    val docId: String,
    val name: String,
    val isDir: Boolean,
    val modified: Long,
)

private val AUDIO_EXTS = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac")

private fun extOf(name: String) = name.substringAfterLast('.', "").lowercase()

fun isAudio(name: String) = extOf(name) in AUDIO_EXTS

/** "Artist - Title.mp3" is the common shape when a file carries no tags. */
private fun fromFileName(name: String): Pair<String, String> {
    val base = name.substringBeforeLast('.', name)
    val parts = base.split(" - ", limit = 2)
    return if (parts.size == 2) parts[0].trim() to parts[1].trim() else base.trim() to ""
}

fun scanMusic(context: Context, treeUri: Uri, maxDepth: Int = 5): List<Song> {
    val resolver = context.contentResolver

    fun children(parentId: String): List<Node> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val out = mutableListOf<Node>()
        try {
            resolver.query(
                uri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2) ?: ""
                    val modified = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    out.add(
                        Node(
                            docId = id,
                            name = name,
                            isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                            modified = modified,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    return try {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val found = mutableListOf<Song>()
        val retriever = MediaMetadataRetriever()

        fun visit(dirId: String, dirName: String, depth: Int) {
            if (depth > maxDepth) return
            val kids = children(dirId)
            for (node in kids.filter { !it.isDir && isAudio(it.name) }) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, node.docId)
                var title = ""
                var artist = ""
                var duration = 0L
                try {
                    retriever.setDataSource(context, uri)
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                    duration = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                }
                val guessed = fromFileName(node.name)
                found.add(
                    Song(
                        uri = uri,
                        docId = node.docId,
                        fileName = node.name,
                        title = title.ifBlank { guessed.first },
                        artist = artist.ifBlank { guessed.second.ifBlank { "Unknown artist" } },
                        durationMs = duration,
                        folder = dirName,
                        addedAt = node.modified,
                    ),
                )
            }
            for (dir in kids.filter { it.isDir }) visit(dir.docId, dir.name, depth + 1)
        }

        visit(rootId, "", 0)
        runCatching { retriever.release() }
        found.sortedBy { it.title.lowercase() }
    } catch (_: Exception) {
        emptyList()
    }
}

/** 3:07 — used for track lengths and playback position. */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60)
}

/** "3 hr 46 min" — used for totals across a list of tracks. */
fun formatLong(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
}
