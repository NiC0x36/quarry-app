package app.quarry.tanvir.info

import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.treemap.TreemapEngine
import app.quarry.tanvir.info.domain.treemap.TreemapNode
import app.quarry.tanvir.info.domain.treemap.TreemapRect
import app.quarry.tanvir.info.domain.treemap.TreemapRenderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TreemapRenderPolicyTest {
    @Test
    fun tenThousandTilesUseNoGradientsOrFolderAnimation() {
        val nodes = grid()
        val viewport = TreemapRenderPolicy.contentViewport(1000f, 1000f, 1f, 0f, 0f)

        // Every tile remains represented when the whole map is visible. Only styles and
        // animation are simplified; there is no arbitrary draw limit that hides files.
        assertEquals(10_000, TreemapRenderPolicy.visibleNodeIndices(nodes, viewport).size)
        assertTrue(TreemapRenderPolicy.gradientNodeIndices(nodes).isEmpty())
        assertFalse(TreemapRenderPolicy.shouldAnimate(nodes.size))
        assertFalse(TreemapRenderPolicy.shouldDrawBorders(nodes.size))
        assertEquals(128, TreemapRenderPolicy.labelBudget(nodes.size))
        assertTrue(TreemapRenderPolicy.FLAT_PALETTE_SIZE <= 64)
        nodes.forEach {
            assertTrue(TreemapRenderPolicy.flatColorIndex(it.path, it.isDirectory) in 0 until TreemapRenderPolicy.FLAT_PALETTE_SIZE)
        }
    }

    @Test
    fun centeredFiveTimesZoomDrawsOnlyTheFourHundredVisibleTiles() {
        val nodes = grid()
        val before = nodes.map { it.rect }
        val viewport = TreemapRenderPolicy.contentViewport(1000f, 1000f, 5f, 0f, 0f)
        val visible = TreemapRenderPolicy.visibleNodeIndices(nodes, viewport)

        assertEquals(TreemapRect(400f, 400f, 600f, 600f), viewport)
        assertEquals(400, visible.size)
        assertTrue(TreemapRenderPolicy.shouldDrawBorders(visible.size))
        assertEquals(128, TreemapRenderPolicy.labelBudget(visible.size))
        visible.forEach { index ->
            val rect = nodes[index].rect
            assertTrue(rect.left >= 400f && rect.right <= 600f)
            assertTrue(rect.top >= 400f && rect.bottom <= 600f)
        }
        assertEquals(before, nodes.map { it.rect })
        assertEquals(10_000L, nodes.sumOf { it.size })
    }

    @Test
    fun panIsInvertedBeforeViewportCulling() {
        val viewport = TreemapRenderPolicy.contentViewport(1000f, 1000f, 5f, 200f, -200f)
        assertEquals(TreemapRect(360f, 440f, 560f, 640f), viewport)
        assertEquals(400, TreemapRenderPolicy.visibleNodeIndices(grid(), viewport).size)
    }

    @Test
    fun smallMapCacheIsBoundedAndDropsAwayAtTheLimit() {
        val small = grid().take(TreemapRenderPolicy.MAX_GRADIENT_TILES)
        assertEquals(TreemapRenderPolicy.MAX_GRADIENT_TILES, TreemapRenderPolicy.gradientNodeIndices(small).size)
        assertTrue(TreemapRenderPolicy.shouldAnimate(small.size))
        assertTrue(TreemapRenderPolicy.gradientNodeIndices(small + small.first()).isEmpty())
        assertFalse(TreemapRenderPolicy.shouldAnimate(small.size + 1))
    }

    @Test
    fun renderingDoesNotEnlargeTinyByteProportionalRectangles() {
        val input = listOf(node("large", 1024L * 1024L * 1024L)) + (1..9).map { node("small-$it", 1024L) }
        val nodes = TreemapEngine.layoutSquarified(input, TreemapRect(0f, 0f, 1000f, 1000f))
        val before = nodes.map { it.rect }
        val viewport = TreemapRenderPolicy.contentViewport(1000f, 1000f, 1f, 0f, 0f)

        assertEquals(10, TreemapRenderPolicy.visibleNodeIndices(nodes, viewport).size)
        assertEquals(10, TreemapRenderPolicy.gradientNodeIndices(nodes).size)
        assertEquals(before, nodes.map { it.rect })
        nodes.filter { it.size == 1024L }.forEach {
            assertTrue(it.rect.width.toDouble() * it.rect.height < 2.0)
        }
    }

    @Test
    fun zeroAndInvalidRectanglesNeverReachTheRenderer() {
        val nodes = listOf(
            node("valid", 1L).copy(rect = TreemapRect(0f, 0f, 10f, 10f)),
            node("zero", 0L).copy(rect = TreemapRect(0f, 0f, 10f, 10f)),
            node("collapsed", 1L),
            node("invalid", 1L).copy(rect = TreemapRect(Float.NaN, 0f, 10f, 10f))
        )
        val viewport = TreemapRect(0f, 0f, 100f, 100f)
        assertEquals(listOf(0), TreemapRenderPolicy.visibleNodeIndices(nodes, viewport).toList())
        assertEquals(listOf(0), TreemapRenderPolicy.gradientNodeIndices(nodes).toList())
        assertTrue(TreemapRenderPolicy.visibleNodeIndices(nodes, TreemapRect(0f, 0f, 0f, 0f)).isEmpty())
    }

    @Test
    fun hitTestPrefersTheSmallestDirectRectangle() {
        val large = node("large", 100L).copy(rect = TreemapRect(0f, 0f, 100f, 100f))
        val small = node("small", 10L).copy(rect = TreemapRect(40f, 40f, 60f, 60f))
        assertSame(small, TreemapRenderPolicy.hitTest(listOf(large, small), 50f, 50f, 10f))
        assertSame(large, TreemapRenderPolicy.hitTest(listOf(large, small), 10f, 10f, 10f))
    }

    @Test
    fun tinyNeighborGetsATouchTargetWithoutInflatingItsRectangle() {
        val large = node("large", 8000L).copy(rect = TreemapRect(0f, 0f, 80f, 100f))
        val tiny = node("tiny", 10L).copy(rect = TreemapRect(80f, 45f, 81f, 55f))
        val nodes = listOf(large, tiny)
        val before = nodes.map { it.rect }

        assertSame(tiny, TreemapRenderPolicy.hitTest(nodes, 78f, 50f, 10f))
        // A neighbor beyond 75% of the radius must not steal a direct hit.
        assertSame(large, TreemapRenderPolicy.hitTest(nodes, 60f, 50f, 10f))
        // The exact shared edge is a direct hit on the smaller rectangle.
        assertSame(tiny, TreemapRenderPolicy.hitTest(nodes, 80f, 50f, 10f))
        assertEquals(before, nodes.map { it.rect })
    }

    @Test
    fun hitTestUsesTheLargerRadiusOnlyOutsideAllRectangles() {
        val node = node("file", 1L).copy(rect = TreemapRect(0f, 0f, 10f, 10f))
        assertSame(node, TreemapRenderPolicy.hitTest(listOf(node), 16f, 5f, 5f))
        assertNull(TreemapRenderPolicy.hitTest(listOf(node), 18f, 5f, 5f))
        assertNull(TreemapRenderPolicy.hitTest(emptyList(), 5f, 5f, 5f))
    }

    @Test
    fun equidistantTouchCandidatesKeepTheirInputOrder() {
        val left = node("left", 1L).copy(rect = TreemapRect(0f, 0f, 10f, 10f))
        val right = node("right", 1L).copy(rect = TreemapRect(90f, 0f, 100f, 10f))
        assertSame(left, TreemapRenderPolicy.hitTest(listOf(left, right), 50f, 5f, 30f))
        assertSame(right, TreemapRenderPolicy.hitTest(listOf(right, left), 50f, 5f, 30f))
    }

    @Test
    fun hitTestingTenThousandTilesStillFindsDirectHitsAndRejectsDistantTouches() {
        val nodes = grid()
        assertSame(nodes[5050], TreemapRenderPolicy.hitTest(nodes, 505f, 505f, 28f))
        assertNull(TreemapRenderPolicy.hitTest(nodes, 2000f, 2000f, 28f))
        assertEquals(10_000, nodes.size)
    }

    @Test
    fun hitTestRejectsNonFiniteCoordinatesAndInvalidRectangles() {
        val valid = node("valid", 1L).copy(rect = TreemapRect(0f, 0f, 10f, 10f))
        val nodes = listOf(
            node("nan", 1L).copy(rect = TreemapRect(Float.NaN, 0f, 10f, 10f)),
            node("infinite", 1L).copy(rect = TreemapRect(0f, 0f, Float.POSITIVE_INFINITY, 10f)),
            node("overflow", 1L).copy(rect = TreemapRect(-Float.MAX_VALUE, 0f, Float.MAX_VALUE, 10f)),
            node("zero-byte", 0L).copy(rect = TreemapRect(0f, 0f, 100f, 100f)),
            node("collapsed", 1L),
            node("reversed", 1L).copy(rect = TreemapRect(10f, 0f, 0f, 10f)),
            valid
        )
        assertSame(valid, TreemapRenderPolicy.hitTest(nodes, 5f, 5f, 0f))
        assertNull(TreemapRenderPolicy.hitTest(nodes, Float.NaN, 5f, 28f))
        assertNull(TreemapRenderPolicy.hitTest(nodes, 5f, Float.NEGATIVE_INFINITY, 28f))
        assertNull(TreemapRenderPolicy.hitTest(nodes, 100f, 100f, 28f))
        assertNull(TreemapRenderPolicy.hitTest(nodes, 11f, 5f, Float.NaN))
    }

    private fun grid(): List<TreemapNode> = (0 until 10_000).map { index ->
        val x = (index % 100) * 10f
        val y = (index / 100) * 10f
        node("tile-$index", 1L).copy(rect = TreemapRect(x, y, x + 10f, y + 10f))
    }

    private fun node(name: String, size: Long) = TreemapNode(
        path = "/$name", name = name, size = size,
        isDirectory = false, category = StorageCategory.OTHER
    )
}
