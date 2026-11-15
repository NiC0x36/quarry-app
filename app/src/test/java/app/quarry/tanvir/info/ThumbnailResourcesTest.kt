package app.quarry.tanvir.info

import app.quarry.tanvir.info.domain.media.ThumbnailDimensions
import app.quarry.tanvir.info.domain.media.ThumbnailSizing
import app.quarry.tanvir.info.domain.media.ThumbnailWorkLimiter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ThumbnailResourcesTest {
    @Test fun panoramicAndTallImagesAreSampledAlongTheirLargestDimension() {
        for ((width, height) in listOf(60000 to 100, 100 to 60000, 100000 to 1, 1 to 100000)) {
            val plan = requireNotNull(ThumbnailSizing.plan(width, height, 128, 128))
            assertTrue("A strip must not decode at full resolution", plan.sampleSize > 1)
            assertTrue((width.toLong() + plan.sampleSize - 1) / plan.sampleSize <= 128)
            assertTrue((height.toLong() + plan.sampleSize - 1) / plan.sampleSize <= 128)
        }
    }

    @Test fun sampledAndRenderedDimensionsStayWithinTheRequestedCap() {
        for (width in listOf(1, 63, 128, 129, 4096, 60000, Int.MAX_VALUE)) {
            for (height in listOf(1, 100, 128, 3000, Int.MAX_VALUE)) {
                val plan = ThumbnailSizing.plan(width, height, 128, 64) ?: continue
                assertTrue(plan.sampleSize > 0 && plan.sampleSize.countOneBits() == 1)
                assertTrue((width.toLong() + plan.sampleSize - 1) / plan.sampleSize <= 128)
                assertTrue((height.toLong() + plan.sampleSize - 1) / plan.sampleSize <= 64)
                val output = requireNotNull(ThumbnailSizing.fit(width.toDouble(), height.toDouble(), plan.target))
                assertTrue(output.width in 1..128)
                assertTrue(output.height in 1..64)
            }
        }
        assertEquals(ThumbnailDimensions(256, 256), ThumbnailSizing.target(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(ThumbnailDimensions(128, 128), ThumbnailSizing.fit(16384.0, 16384.0, ThumbnailDimensions(128, 128)))
        assertEquals(ThumbnailDimensions(24, 12), ThumbnailSizing.fit(24.0, 12.0, ThumbnailDimensions(128, 128)))
    }

    @Test fun invalidDimensionsAreRejectedWithoutOverflowOrFallback() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0)) {
            assertNull(ThumbnailSizing.fit(invalid, 100.0, ThumbnailDimensions(128, 128)))
            assertNull(ThumbnailSizing.fit(100.0, invalid, ThumbnailDimensions(128, 128)))
        }
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            assertNull(ThumbnailSizing.targetEdge(invalid))
        }
        assertEquals(256, ThumbnailSizing.targetEdge(Float.MAX_VALUE))
        assertNull(ThumbnailSizing.target(0, 128))
        assertNull(ThumbnailSizing.plan(-1, 100, 128, 128))
        assertNull(ThumbnailSizing.plan(100, 100, 0, 128))
        assertNull(ThumbnailSizing.plan(Int.MAX_VALUE, 1, 1, 1))
    }

    @Test fun concurrentPreviewWorkNeverExceedsTwoDecoders() = runBlocking {
        withTimeout(5000) {
            val limiter = ThumbnailWorkLimiter(2)
            val started = Channel<Unit>(Channel.UNLIMITED)
            val finish = CompletableDeferred<Unit>()
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val jobs = List(24) {
                launch(Dispatchers.Default) {
                    limiter.run {
                        val count = active.incrementAndGet()
                        peak.updateAndGet { maxOf(it, count) }
                        try {
                            started.send(Unit)
                            finish.await()
                        } finally {
                            active.decrementAndGet()
                        }
                    }
                }
            }
            repeat(2) { started.receive() }
            assertEquals(2, active.get())
            finish.complete(Unit)
            jobs.joinAll()
            assertEquals(2, peak.get())
            assertEquals(0, active.get())
        }
    }

    @Test fun cancellationOfWaitingAndRunningWorkReleasesAllPermits() = runBlocking {
        withTimeout(5000) {
            val limiter = ThumbnailWorkLimiter(1)
            val started = CompletableDeferred<Unit>()
            val hold = CompletableDeferred<Unit>()
            val running = launch {
                limiter.run { started.complete(Unit); hold.await() }
            }
            started.await()
            var waitingEntered = false
            val waiting = launch { limiter.run { waitingEntered = true } }
            yield()
            waiting.cancelAndJoin()
            running.cancelAndJoin()
            assertFalse(waitingEntered)
            assertEquals("available", limiter.run { "available" })

            val cancelled = async { limiter.run { throw CancellationException("preview left the screen") } }
            try {
                cancelled.await()
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                assertTrue(cancelled.isCancelled)
            }
            assertEquals("still available", limiter.run { "still available" })
        }
    }
}
