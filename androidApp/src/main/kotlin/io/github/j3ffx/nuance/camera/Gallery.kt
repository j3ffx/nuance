package io.github.j3ffx.nuance.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore

/**
 * Photos and videos go to the phone's gallery, album "Nuance", through MediaStore: no storage
 * permission needed. Nothing adds a location: the JPEG is encoded from pixels (no EXIF), and the
 * video holds pixels only.
 */
class Gallery(private val context: Context) {
    /** Saves [bitmap] as "[name].jpg"; null on failure. */
    fun savePhoto(bitmap: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) } == true
        }.getOrDefault(false)
        if (!ok) {
            resolver.delete(uri, null, null)
            return null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    /** A pending "[name].mp4" for the encoder to write into; [publish] or [discard] it after. */
    fun newVideo(name: String): Pair<Uri, ParcelFileDescriptor>? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$ALBUM")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val fd = runCatching { resolver.openFileDescriptor(uri, "w") }.getOrNull()
        if (fd == null) {
            resolver.delete(uri, null, null)
            return null
        }
        return uri to fd
    }

    fun publish(uri: Uri) {
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
            null,
            null
        )
    }

    fun discard(uri: Uri) {
        context.contentResolver.delete(uri, null, null)
    }

    companion object {
        const val ALBUM = "Nuance"
    }
}
