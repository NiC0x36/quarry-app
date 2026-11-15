package app.quarry.tanvir.info.domain.media

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

data class ThumbnailDimensions(val width: Int, val height: Int)
data class ThumbnailDecodePlan(val target: ThumbnailDimensions, val sampleSize: Int)

/** Pure sizing rules shared by every decoder, including very wide or tall images. */
object ThumbnailSizing {
    const val MAX_EDGE = 256

    fun target(width: Int, height: Int): ThumbnailDimensions? =
        if (width <= 0 || height <= 0) null
        else ThumbnailDimensions(width.coerceAtMost(MAX_EDGE), height.coerceAtMost(MAX_EDGE))

    fun targetEdge(pixels: Float): Int? =
        if (!pixels.isFinite() || pixels <= 0f) null
        else ceil(pixels.coerceAtMost(MAX_EDGE.toFloat()).toDouble()).toInt().coerceAtLeast(1)

    fun fit(sourceWidth: Double, sourceHeight: Double, target: ThumbnailDimensions): ThumbnailDimensions? {
        if (!sourceWidth.isFinite() || !sourceHeight.isFinite() || sourceWidth <= 0 || sourceHeight <= 0) return null
        val bounded = this.target(target.width, target.height) ?: return null
        val scale = min(1.0, min(bounded.width / sourceWidth, bounded.height / sourceHeight))
        return ThumbnailDimensions(
            floor(sourceWidth * scale).toInt().coerceIn(1, bounded.width),
            floor(sourceHeight * scale).toInt().coerceIn(1, bounded.height)
        )
    }

    fun plan(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): ThumbnailDecodePlan? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        val target = target(width, height) ?: return null
        var sample = 1
        // Bound BOTH decoded dimensions. Requiring both to exceed the target would
        // leave panoramas/strips almost entirely unsampled.
        while ((sourceWidth.toLong() + sample - 1) / sample > target.width ||
            (sourceHeight.toLong() + sample - 1) / sample > target.height) {
            if (sample > Int.MAX_VALUE / 2) return null
            sample *= 2
        }
        return ThumbnailDecodePlan(target, sample)
    }
}

/** Waiting and active preview jobs release their permit when cancelled or failed. */
class ThumbnailWorkLimiter(maxConcurrent: Int = 2) {
    private val permits = Semaphore(maxConcurrent)

    suspend fun <T> run(block: suspend () -> T): T = permits.withPermit {
        currentCoroutineContext().ensureActive()
        block()
    }
}
