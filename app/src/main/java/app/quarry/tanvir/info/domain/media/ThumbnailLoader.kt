package app.quarry.tanvir.info.domain.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.media.ThumbnailUtils
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import app.quarry.tanvir.info.domain.model.StorageCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resumeWithException

/** Offline previews with bounded output, cache memory and concurrent decoder work. */
object ThumbnailLoader {
    private val work = ThumbnailWorkLimiter(2)
    private val cacheKiB = (Runtime.getRuntime().maxMemory() / 1024 / 32)
        .coerceIn(2 * 1024L, 8 * 1024L).toInt()
    private val memoryCache = object : LruCache<String, Bitmap>(cacheKiB) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int =
            ((bitmap.allocationByteCount.toLong() + 1023) / 1024).coerceAtLeast(1).toInt()
    }

    suspend fun loadThumbnail(
        context: Context,
        path: String,
        category: StorageCategory,
        targetWidth: Int = 128,
        targetHeight: Int = 128,
        lastModified: Long = 0L
    ): Bitmap? = withContext(Dispatchers.IO) {
        val target = ThumbnailSizing.target(targetWidth, targetHeight) ?: return@withContext null
        val key = "${path}_${lastModified}_${category.name}_${target.width}x${target.height}"
        memoryCache.get(key)?.let { return@withContext it }
        work.run {
            currentCoroutineContext().ensureActive()
            memoryCache.get(key)?.let { return@run it }
            val file = File(path)
            if (!file.isFile || !file.canRead()) return@run null
            var decoded: Bitmap? = null
            try {
                decoded = decodeCancellable { signal ->
                    val result = when (category) {
                        StorageCategory.IMAGES -> decodeImage(file, target, signal)
                        StorageCategory.VIDEOS -> ThumbnailUtils.createVideoThumbnail(file, Size(target.width, target.height), signal)
                        StorageCategory.AUDIO -> ThumbnailUtils.createAudioThumbnail(file, Size(target.width, target.height), signal)
                        StorageCategory.APKS -> decodeApkIcon(context, file, target, signal)
                        StorageCategory.DOCUMENTS -> if (file.extension.equals("pdf", true)) decodePdf(file, target, signal) else null
                        else -> null
                    }
                    result?.let { boundBitmap(it, target) }
                }
                currentCoroutineContext().ensureActive()
                decoded?.let { memoryCache.put(key, it) }
                decoded
            } catch (e: CancellationException) {
                decoded?.recycle()
                throw e
            } catch (_: OutOfMemoryError) {
                // Preview failure is not a reason to try another, potentially larger decoder.
                decoded?.recycle()
                memoryCache.evictAll()
                null
            } catch (_: Exception) {
                decoded?.recycle()
                currentCoroutineContext().ensureActive()
                null
            }
        }
    }

    /** Cancellation reaches platform decoders while their blocking call is still running. */
    private suspend fun decodeCancellable(decode: (CancellationSignal) -> Bitmap?): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                signal.throwIfCanceled()
                val bitmap = decode(signal)
                continuation.resume(bitmap) { _, unused, _ -> unused?.recycle() }
            } catch (e: CancellationException) {
                continuation.cancel(e)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            } catch (e: OutOfMemoryError) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }

    private fun decodeImage(file: File, target: ThumbnailDimensions, signal: CancellationSignal): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        signal.throwIfCanceled()
        val plan = ThumbnailSizing.plan(options.outWidth, options.outHeight, target.width, target.height) ?: return null
        options.inJustDecodeBounds = false
        options.inSampleSize = plan.sampleSize
        options.inPreferredConfig = Bitmap.Config.RGB_565
        // No unscaled fallback if bounds, sampling, or decoding fail.
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun boundBitmap(bitmap: Bitmap, target: ThumbnailDimensions): Bitmap? {
        val size = ThumbnailSizing.fit(bitmap.width.toDouble(), bitmap.height.toDouble(), target)
        if (size == null) {
            bitmap.recycle()
            return null
        }
        if (bitmap.width == size.width && bitmap.height == size.height) return bitmap
        return try {
            Bitmap.createScaledBitmap(bitmap, size.width, size.height, true)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeApkIcon(context: Context, file: File, target: ThumbnailDimensions, signal: CancellationSignal): Bitmap? {
        val manager = context.packageManager
        val info = manager.getPackageArchiveInfo(file.absolutePath, 0)?.applicationInfo ?: return null
        signal.throwIfCanceled()
        info.sourceDir = file.absolutePath
        info.publicSourceDir = file.absolutePath
        return drawableToBitmap(info.loadIcon(manager), target, signal)
    }

    private fun drawableToBitmap(drawable: Drawable, target: ThumbnailDimensions, signal: CancellationSignal): Bitmap? {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: target.width
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: target.height
        val size = ThumbnailSizing.fit(width.toDouble(), height.toDouble(), target) ?: return null
        signal.throwIfCanceled()
        // Never return a BitmapDrawable's full-resolution backing bitmap to the cache.
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, size.width, size.height)
            drawable.draw(Canvas(bitmap))
            return bitmap
        } catch (e: Exception) {
            bitmap.recycle()
            throw e
        } catch (e: OutOfMemoryError) {
            bitmap.recycle()
            throw e
        }
    }

    private fun decodePdf(file: File, target: ThumbnailDimensions, signal: CancellationSignal): Bitmap? {
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            if (renderer.pageCount <= 0) return null
            signal.throwIfCanceled()
            page = renderer.openPage(0)
            val size = ThumbnailSizing.fit(page.width.toDouble(), page.height.toDouble(), target) ?: return null
            val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            try {
                signal.throwIfCanceled()
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            } catch (e: Exception) {
                bitmap.recycle()
                throw e
            } catch (e: OutOfMemoryError) {
                bitmap.recycle()
                throw e
            }
        } finally {
            // Close failures from malformed PDFs must not override cleanup of other handles.
            try { page?.close() } catch (_: Exception) { }
            try { renderer?.close() } catch (_: Exception) { }
            try { descriptor?.close() } catch (_: Exception) { }
        }
    }
}
