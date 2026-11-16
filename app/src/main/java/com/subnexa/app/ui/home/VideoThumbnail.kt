package com.subnexa.app.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.SurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections

/**
 * 16:9 preview of a video, shown on the home screen project cards.
 * Frames are grabbed once, then kept in memory and in the app cache folder.
 */
object VideoThumbnails {

  private const val THUMB_WIDTH = 640
  private const val THUMB_HEIGHT = 360

  // Cache file suffix. Changing it makes old (square) cached thumbnails unused.
  private const val CACHE_SUFFIX = "_16x9.jpg"

  // Limited by bitmap bytes (16 MB), not by entry count.
  private val memory = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }
  private val failed: MutableSet<String> = Collections.synchronizedSet(HashSet())

  /** Already loaded thumbnail, if any (no disk or decoder work). */
  fun peek(videoUri: String): Bitmap? = memory.get(videoUri)

  suspend fun load(context: Context, videoUri: String): Bitmap? = withContext(Dispatchers.IO) {
    if (videoUri.isEmpty() || failed.contains(videoUri)) return@withContext null
    memory.get(videoUri)?.let { return@withContext it }

    val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
    val file = File(dir, sha1(videoUri) + CACHE_SUFFIX)

    if (file.exists()) {
      val cached = try {
        BitmapFactory.decodeFile(file.path)
      } catch (_: Exception) {
        null
      }
      if (cached != null) {
        memory.put(videoUri, cached)
        return@withContext cached
      }
    }

    val frame = extractFrame(context, Uri.parse(videoUri))
    if (frame == null) {
      // Video moved/deleted or permission lost: do not retry on every scroll.
      failed.add(videoUri)
      return@withContext null
    }

    try {
      FileOutputStream(file).use { frame.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    } catch (_: Exception) {
      // Disk cache is optional.
    }
    memory.put(videoUri, frame)
    frame
  }

  private fun extractFrame(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(context, uri)
      val durationMs = retriever
        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        ?.toLongOrNull() ?: 0L
      // A frame ~10% into the video avoids black intro screens. (ms * 100 = 10% in microseconds)
      val timeUs = (durationMs * 100L).coerceIn(1_000_000L, 60_000_000L)
      val source = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        ?: retriever.getFrameAtTime()
        ?: return null
      centerCrop16x9(source)
    } catch (_: Exception) {
      null
    } finally {
      try {
        retriever.release()
      } catch (_: Exception) {
      }
    }
  }

  private fun centerCrop16x9(source: Bitmap): Bitmap {
    val targetRatio = THUMB_WIDTH.toFloat() / THUMB_HEIGHT
    val sourceRatio = source.width.toFloat() / source.height
    val cropWidth: Int
    val cropHeight: Int
    if (sourceRatio > targetRatio) {
      cropHeight = source.height
      cropWidth = (cropHeight * targetRatio).toInt().coerceIn(1, source.width)
    } else {
      cropWidth = source.width
      cropHeight = (cropWidth / targetRatio).toInt().coerceIn(1, source.height)
    }
    val cropped = if (cropWidth == source.width && cropHeight == source.height) {
      source
    } else {
      Bitmap.createBitmap(
        source,
        (source.width - cropWidth) / 2,
        (source.height - cropHeight) / 2,
        cropWidth,
        cropHeight
      )
    }
    val scaled = if (cropped.width > THUMB_WIDTH) {
      Bitmap.createScaledBitmap(cropped, THUMB_WIDTH, THUMB_HEIGHT, true)
    } else {
      cropped
    }
    if (cropped !== scaled && cropped !== source) cropped.recycle()
    if (source !== scaled) source.recycle()
    return scaled
  }

  private fun sha1(text: String): String =
    MessageDigest.getInstance("SHA-1")
      .digest(text.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
}

@Composable
fun VideoThumbnail(
  videoUri: String,
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(10.dp),
  iconSize: Dp = 22.dp
) {
  val context = LocalContext.current.applicationContext
  val bitmap by produceState<Bitmap?>(initialValue = VideoThumbnails.peek(videoUri), key1 = videoUri) {
    if (value == null) value = VideoThumbnails.load(context, videoUri)
  }

  Box(
    modifier = modifier
      .clip(shape)
      .background(SurfaceVariant),
    contentAlignment = Alignment.Center
  ) {
    val current = bitmap
    if (current != null) {
      val image = remember(current) { current.asImageBitmap() }
      Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      Icon(
        imageVector = Icons.Outlined.Movie,
        contentDescription = null,
        tint = Accent,
        modifier = Modifier.size(iconSize)
      )
    }
  }
}
