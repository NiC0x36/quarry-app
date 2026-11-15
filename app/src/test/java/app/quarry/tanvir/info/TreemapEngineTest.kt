package app.quarry.tanvir.info

import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.treemap.TreemapEngine
import app.quarry.tanvir.info.domain.treemap.TreemapNode
import app.quarry.tanvir.info.domain.treemap.TreemapRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreemapEngineTest {

    @Test
    fun testSquarifiedTreemapLayout() {
        val nodes = listOf(
            TreemapNode(
                path = "/storage/emulated/0/Download/video.mp4",
                name = "video.mp4",
                size = 600L,
                isDirectory = false,
                category = StorageCategory.VIDEOS
            ),
            TreemapNode(
                path = "/storage/emulated/0/Download/photo.jpg",
                name = "photo.jpg",
                size = 200L,
                isDirectory = false,
                category = StorageCategory.IMAGES
            ),
            TreemapNode(
                path = "/storage/emulated/0/Download/doc.pdf",
                name = "doc.pdf",
                size = 200L,
                isDirectory = false,
                category = StorageCategory.DOCUMENTS
            )
        )

        val bounds = TreemapRect(0f, 0f, 1000f, 1000f)
        val laidOut = TreemapEngine.layoutSquarified(nodes, bounds)

        assertEquals(3, laidOut.size)

        // Verify total area coverage matches bounds
        val totalArea = laidOut.sumOf { (it.rect.width * it.rect.height).toDouble() }
        val targetArea = (bounds.width * bounds.height).toDouble()
        assertEquals(targetArea, totalArea, 10.0)

        // Rectangle area is a measurement, not a visibility-weighted approximation.
        val videoArea = (laidOut[0].rect.width * laidOut[0].rect.height).toDouble()
        assertEquals(targetArea * 0.6, videoArea, 0.1)
    }

    @Test
    fun testBuildTreeFromFiles() {
        val files = listOf(
            FileEntity(
                path = "/storage/emulated/0/Download",
                name = "Download",
                size = 800L,
                isDirectory = true,
                category = StorageCategory.OTHER.name,
                parentPath = "/storage/emulated/0",
                directChildrenCount = 2
            ),
            FileEntity(
                path = "/storage/emulated/0/Download/movie.mp4",
                name = "movie.mp4",
                size = 500L,
                isDirectory = false,
                category = StorageCategory.VIDEOS.name,
                parentPath = "/storage/emulated/0/Download",
                extension = "mp4"
            ),
            FileEntity(
                path = "/storage/emulated/0/Download/music.mp3",
                name = "music.mp3",
                size = 300L,
                isDirectory = false,
                category = StorageCategory.AUDIO.name,
                parentPath = "/storage/emulated/0/Download",
                extension = "mp3"
            )
        )

        val tree = TreemapEngine.buildTree(files, "/storage/emulated/0")
        assertEquals("/storage/emulated/0", tree.path)
        assertEquals(1, tree.children.size)
        assertEquals("Download", tree.children[0].name)
        assertEquals(800L, tree.children[0].size)
    }

    @Test
    fun testEmptyTreemapLayout() {
        val laidOut = TreemapEngine.layoutSquarified(emptyList(), TreemapRect(0f, 0f, 500f, 500f))
        assertTrue(laidOut.isEmpty())
    }

    @Test
    fun gigabyteAndKilobyteFilesKeepTheirTrueAreaRatio() {
        val nodes = listOf(node("large", 1024L * 1024L * 1024L)) +
            (1..9).map { node("small-$it", 1024L) }
        val bounds = TreemapRect(0f, 0f, 1000f, 1000f)
        val laidOut = TreemapEngine.layoutSquarified(nodes, bounds)
        val totalBytes = nodes.sumOf { it.size }.toDouble()
        val canvasArea = 1_000_000.0

        assertEquals(10, laidOut.size)
        laidOut.forEach { tile ->
            val actualArea = tile.rect.width.toDouble() * tile.rect.height.toDouble()
            val expectedArea = canvasArea * tile.size / totalBytes
            // Float canvas coordinates quantize a thin strip at the far edge. Even the
            // tiny entries must agree within 1%, rather than receive a 3.5% canvas floor.
            assertEquals(tile.name, expectedArea, actualArea, expectedArea * 0.01)
            assertTrue(tile.rect.width > 0f && tile.rect.height > 0f)
        }
        assertEquals(canvasArea, laidOut.sumOf { it.rect.width.toDouble() * it.rect.height.toDouble() }, 0.1)
    }

    @Test
    fun zeroByteEntriesNeverConsumeAreaOrProduceInvalidRectangles() {
        val positive = node("positive", 10L)
        val emptyFile = node("empty-file", 0L)
        val emptyFolder = node("empty-folder", 0L).copy(isDirectory = true)
        val invalid = node("invalid", -1L)
        val bounds = TreemapRect(10f, 20f, 210f, 120f)

        assertTrue(TreemapEngine.layoutSquarified(listOf(emptyFile, emptyFolder, invalid), bounds).isEmpty())
        val laidOut = TreemapEngine.layoutSquarified(listOf(emptyFile, positive, emptyFolder, invalid), bounds)
        assertEquals(1, laidOut.size)
        assertEquals(bounds, laidOut.single().rect)
    }

    @Test
    fun invalidBoundsProduceNoLayout() {
        val nodes = listOf(node("file", 1L))
        listOf(
            TreemapRect(0f, 0f, 0f, 100f),
            TreemapRect(0f, 100f, 100f, 0f),
            TreemapRect(Float.NaN, 0f, 100f, 100f),
            TreemapRect(0f, 0f, Float.POSITIVE_INFINITY, 100f)
        ).forEach { assertTrue(TreemapEngine.layoutSquarified(nodes, it).isEmpty()) }
    }

    @Test
    fun veryLargeSizesCannotOverflowTheLayoutTotal() {
        val bounds = TreemapRect(0f, 0f, 100f, 100f)
        val laidOut = TreemapEngine.layoutSquarified(
            listOf(node("one", Long.MAX_VALUE), node("two", Long.MAX_VALUE)), bounds
        )
        assertEquals(2, laidOut.size)
        laidOut.forEach { assertEquals(5000.0, it.rect.width.toDouble() * it.rect.height, 0.01) }
    }

    @Test
    fun layoutPartitionsAnOffsetRectangleWithoutOverlap() {
        val bounds = TreemapRect(10f, 20f, 610f, 420f)
        val nodes = listOf(8L, 5L, 3L, 2L, 1L).mapIndexed { index, bytes -> node("item-$index", bytes) }
        val laidOut = TreemapEngine.layoutSquarified(nodes, bounds)
        val totalArea = 240_000.0
        val totalBytes = nodes.sumOf { it.size }.toDouble()

        laidOut.forEachIndexed { index, tile ->
            val rect = tile.rect
            assertTrue(rect.left >= bounds.left && rect.top >= bounds.top)
            assertTrue(rect.right <= bounds.right && rect.bottom <= bounds.bottom)
            assertTrue(rect.width > 0f && rect.height > 0f)
            assertEquals(totalArea * tile.size / totalBytes, rect.width.toDouble() * rect.height, 0.1)
            laidOut.drop(index + 1).forEach { other ->
                val overlapWidth = minOf(rect.right, other.rect.right) - maxOf(rect.left, other.rect.left)
                val overlapHeight = minOf(rect.bottom, other.rect.bottom) - maxOf(rect.top, other.rect.top)
                assertTrue(overlapWidth <= 0f || overlapHeight <= 0f)
            }
        }
        assertEquals(totalArea, laidOut.sumOf { it.rect.width.toDouble() * it.rect.height }, 0.1)
    }

    @Test
    fun rootPathRequiresASegmentBoundaryAndAcceptsTrailingSlash() {
        val root = "/storage/emulated/0/Download"
        val inside = file("$root/inside.txt", 3L)
        val sibling = file("${root}s/outside.txt", 700L)
        val rootRecord = file(root, 999L).copy(isDirectory = true)
        val tree = TreemapEngine.buildTree(listOf(rootRecord, inside, sibling), "$root/")

        assertEquals(root, tree.path)
        assertEquals(3L, tree.size)
        assertEquals(listOf(inside.path), tree.children.map { it.path })
    }

    @Test
    fun directoryFallbackDoesNotCountPrefixSiblingOrLoseZeroByteEntries() {
        val root = "/storage/emulated/0"
        val directory = file("$root/Download", 0L).copy(isDirectory = true)
        val files = listOf(
            directory,
            file("$root/Download/inside.txt", 3L),
            file("$root/Downloads/outside.txt", 700L),
            file("$root/empty", 0L)
        )
        val tree = TreemapEngine.buildTree(files, root)

        assertEquals(3L, tree.children.single { it.path == directory.path }.size)
        assertEquals(703L, tree.size)
        assertEquals(0L, tree.children.single { it.name == "empty" }.size)
        assertEquals(3, tree.children.size)
    }

    private fun node(name: String, size: Long) = TreemapNode(
        path = "/$name",
        name = name,
        size = size,
        isDirectory = false,
        category = StorageCategory.OTHER
    )

    private fun file(path: String, size: Long) = FileEntity(
        path = path,
        name = path.substringAfterLast('/'),
        size = size,
        isDirectory = false,
        category = StorageCategory.OTHER.name,
        parentPath = path.substringBeforeLast('/')
    )
}
