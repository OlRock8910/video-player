package com.dadsvictory.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * His photo, on his phone, and nowhere else.
 *
 * The picked image is copied straight into the app's private files directory,
 * which no other app can read, and it is never transmitted anywhere: this app has
 * no networking code and does not hold the INTERNET permission, so there is no
 * mechanism by which it could be.
 */
object PhotoStore {

    private const val FILE_NAME = "family_photo.jpg"

    /** Long edge cap. Big enough to look good on a phone, small enough to decode fast. */
    private const val MAX_DIMENSION = 1600

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).exists()

    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(context, uri) ?: return@runCatching false
            file(context).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            true
        }.getOrDefault(false)
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val file = file(context)
        if (!file.exists()) return@withContext null
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun delete(context: Context): Boolean = file(context).let { if (it.exists()) it.delete() else true }

    /**
     * Two-pass decode: read the bounds first, then decode subsampled, so a 50MP
     * camera photo does not have to be fully decoded into memory to be resized.
     */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }
}
