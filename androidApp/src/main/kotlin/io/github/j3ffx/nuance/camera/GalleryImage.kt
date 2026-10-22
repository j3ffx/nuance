package io.github.j3ffx.nuance.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import io.github.j3ffx.nuance.core.charts.SyntheticImage
import kotlin.math.max

/**
 * Decodes a photo picked in the gallery (the system picker: no storage permission), upright and
 * at most [MAX_SIDE] pixels on its longer side, as pixels the renderer shows like any source.
 */
object GalleryImage {
    /** Enough for the screen and for zooming into it; a 50 MP photo would only cost memory. */
    const val MAX_SIDE = 2048

    fun decode(context: Context, uri: Uri): SyntheticImage? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longer = max(info.size.width, info.size.height)
            if (longer > MAX_SIDE) {
                val k = MAX_SIDE.toFloat() / longer
                decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
            }
            // Pixels are read on the CPU once: a software bitmap, in sRGB.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
        }
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(argb.width * argb.height)
        argb.getPixels(pixels, 0, argb.width, 0, 0, argb.width, argb.height)
        SyntheticImage(argb.width, argb.height, pixels)
    }.getOrNull()
}
