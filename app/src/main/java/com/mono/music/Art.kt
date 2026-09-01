package com.mono.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Embedded cover art, extracted once per file and cached on disk.
 *
 * The first version of Mono decoded every cover with `inSampleSize = 2`, which
 * halves a 500px cover to 250px and then stretches it back up to fill a phone
 * screen — that is where the soft, blocky artwork came from. Here the
 * sample size is computed from what the caller actually asked for, and full
 * covers are cached at [FULL_PX] so the now-playing screen has real pixels to
 * work with.
 */
object Art {

    /** Long edge kept for the disk cache: enough for a full-screen vinyl. */
    private const val FULL_PX = 1024

    /** Long edge for list rows; decoded from the cached full-size file. */
    const val THUMB_PX = 160

    private const val JPEG_QUALITY = 95

    private val memory = object : LruCache<String, Bitmap>(48) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }

    private fun dir(context: Context) = File(context.cacheDir, "art-v2").apply { mkdirs() }

    private fun key(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    private fun cached(context: Context, uri: Uri) = File(dir(context), key(uri.toString()) + ".jpg")

    /**
     * Sample size that lands just above [target] on the long edge. Powers of
     * two only — that is all BitmapFactory honours — and never below 1, so a
     * cover smaller than the target is decoded at full size rather than
     * being thrown away.
     */
    private fun sampleFor(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= target) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * Pulls the cover out of the file itself and writes it to the disk cache.
     * Returns null when the track has no embedded art.
     */
    private fun extract(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        var bitmap: Bitmap? = null
        try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture
            if (bytes != null) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, FULL_PX)
                }
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        val art = bitmap ?: return null
        runCatching {
            cached(context, uri).outputStream().use {
                art.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            }
        }
        return art
    }

    /**
     * Cover for [uri], no larger than [targetPx] on its long edge. Safe to call
     * from any background thread; never call it on the main thread, since a
     * cache miss reads the audio file.
     */
    fun load(context: Context, uri: Uri, targetPx: Int = FULL_PX): Bitmap? {
        val memoryKey = "${uri}@$targetPx"
        memory.get(memoryKey)?.let { return it }

        val file = cached(context, uri)
        val bitmap = if (file.exists() && file.length() > 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, targetPx)
            }
            runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
        } else {
            extract(context, uri)?.let { full ->
                if (targetPx >= FULL_PX) {
                    full
                } else {
                    val scale = targetPx.toFloat() / maxOf(full.width, full.height)
                    if (scale >= 1f) {
                        full
                    } else {
                        Bitmap.createScaledBitmap(
                            full,
                            (full.width * scale).toInt().coerceAtLeast(1),
                            (full.height * scale).toInt().coerceAtLeast(1),
                            true,
                        )
                    }
                }
            }
        }

        if (bitmap != null) memory.put(memoryKey, bitmap)
        return bitmap
    }
}

/**
 * Teaches the media session how to find cover art.
 *
 * Tracks are published with `artworkUri` set to the audio file's own uri, so no
 * artwork is read while a queue is being built — only the track the
 * notification is currently showing gets decoded, and only once.
 */
@UnstableApi
class ArtBitmapLoader(private val context: Context) : BitmapLoader {

    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit(
            Callable {
                BitmapFactory.decodeByteArray(data, 0, data.size)
                    ?: throw IllegalArgumentException("Not a bitmap")
            },
        )

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit(
            Callable {
                Art.load(context, uri) ?: throw IllegalArgumentException("No embedded artwork")
            },
        )

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        metadata.artworkData?.let { return decodeBitmap(it) }
        val uri = metadata.artworkUri ?: return null
        return loadBitmap(uri)
    }
}
