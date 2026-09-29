package com.gratitudegarden.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

// ── Entry photos (roadmap 1.4) ───────────────────────────────────────
// One photo per entry, in the private bucket from migration 20260929120000. The same
// relative path names it on the server and under the device's photo folder, so the path
// in Room is both the upload key and where the local copy lives.

/** The storage bucket entry photos live in. */
const val PHOTO_BUCKET = "entry-photos"

/** Longest edge of a stored photo: enough to fill a phone screen, a few hundred KB as JPEG. */
const val PHOTO_MAX_EDGE = 1600

const val PHOTO_JPEG_QUALITY = 82

/**
 * Where a photo lives: `{user}/{entry}/{photo}.jpg`. The photo id is new each time a photo
 * is set, so a replacement never takes the name of the photo it replaces, and an upload
 * sent twice writes the same bytes to the same name.
 */
fun photoPathFor(userId: String, entryId: String, photoId: String = UUID.randomUUID().toString()): String =
    "$userId/$entryId/$photoId.jpg"

// Looser than the server's check (ids here needn't be UUIDs, as in the tests), but strict
// enough that a path can't climb out of the photo folder.
private val PHOTO_PATH = Regex("""^[A-Za-z0-9-]+/[A-Za-z0-9-]+/[A-Za-z0-9-]+\.jpg$""")

/** Whether [path] is shaped like [photoPathFor]'s, and so safe to use as a file path. */
fun isPhotoPath(path: String): Boolean = PHOTO_PATH.matches(path)

/**
 * The size an image of [width] x [height] is stored at: its longer edge at most [maxEdge],
 * its shape kept. Never scales up.
 */
fun targetSize(width: Int, height: Int, maxEdge: Int = PHOTO_MAX_EDGE): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return width to height
    val scale = maxEdge.toDouble() / longest
    return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
}

/**
 * The largest power-of-two subsampling that still leaves the longer edge at least [maxEdge],
 * so a 50-megapixel photo is never decoded whole just to be shrunk.
 */
fun sampleSizeFor(longestEdge: Int, maxEdge: Int = PHOTO_MAX_EDGE): Int {
    var sample = 1
    while (longestEdge / (sample * 2) >= maxEdge) sample *= 2
    return sample
}

/**
 * Turns a picked or captured image into the JPEG that is stored: upright, at most
 * [PHOTO_MAX_EDGE] on its longer edge.
 *
 * Re-encoding drops every EXIF tag, the location included. ImageDecoder has already applied
 * the orientation tag to the pixels, so nothing that matters is lost with them.
 */
class PhotoPreparer(private val context: Context) {

    /** Decode [uri] and write the stored form to a new file in the staging folder. */
    suspend fun prepare(uri: Uri): File = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.setTargetSampleSize(sampleSizeFor(maxOf(info.size.width, info.size.height)))
            // A hardware bitmap can't be scaled or compressed.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        // Scaled from the decoded bitmap's own size, which is already upright, rather than
        // the header's, which may not be.
        val (w, h) = targetSize(decoded.width, decoded.height)
        var bitmap = if (w == decoded.width && h == decoded.height) decoded
        else Bitmap.createScaledBitmap(decoded, w, h, true).also { decoded.recycle() }
        // JPEG has no transparency: a see-through PNG would come out black where it's clear.
        if (bitmap.hasAlpha()) {
            val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(opaque).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
            bitmap.recycle()
            bitmap = opaque
        }
        val out = File(stagingDir(context), "${UUID.randomUUID()}.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
        bitmap.recycle()
        out
    }

    companion object {
        /** Under the cache folder; must match `res/xml/file_paths.xml`. */
        const val CAPTURE_DIR = "camera"

        /**
         * A new empty file for the camera app to write into, shared through the FileProvider
         * (`res/xml/file_paths.xml` exposes only this folder).
         */
        fun newCaptureFile(context: Context): File =
            File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }.let { File(it, "${UUID.randomUUID()}.jpg") }

        /** Where a prepared photo waits until its entry is saved or the sheet is closed. */
        fun stagingDir(context: Context): File = File(context.cacheDir, "photo-staging").apply { mkdirs() }
    }
}
