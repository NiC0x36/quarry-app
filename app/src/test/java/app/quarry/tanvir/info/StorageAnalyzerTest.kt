package app.quarry.tanvir.info

import app.quarry.tanvir.info.data.database.CategoryStat
import app.quarry.tanvir.info.data.database.ScanSnapshotEntity
import app.quarry.tanvir.info.domain.analyzer.StorageAnalyzer
import app.quarry.tanvir.info.domain.analyzer.StorageSegmentKind
import app.quarry.tanvir.info.domain.model.StorageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAnalyzerTest {

    @Test
    fun testStorageOverviewCalculations() {
        val totalBytes = 128L * 1024 * 1024 * 1024 // 128 GB
        val freeBytes = 40L * 1024 * 1024 * 1024   // 40 GB
        val usedBytes = totalBytes - freeBytes      // 88 GB

        val categoryStats = listOf(
            CategoryStat(StorageCategory.VIDEOS.name, 25L * 1024 * 1024 * 1024, 150),
            CategoryStat(StorageCategory.IMAGES.name, 15L * 1024 * 1024 * 1024, 3200),
            CategoryStat(StorageCategory.AUDIO.name, 8L * 1024 * 1024 * 1024, 450)
        )

        val snapshots = listOf(
            ScanSnapshotEntity(
                id = 2,
                volumePath = "/storage/emulated/0",
                volumeName = "Internal Storage",
                totalDeviceBytes = totalBytes,
                usedBytes = usedBytes,
                freeBytes = freeBytes,
                totalFiles = 3800
            ),
            ScanSnapshotEntity(
                id = 1,
                volumePath = "/storage/emulated/0",
                volumeName = "Internal Storage",
                totalDeviceBytes = totalBytes,
                usedBytes = usedBytes - (2L * 1024 * 1024 * 1024), // 2 GB less previously
                freeBytes = freeBytes + (2L * 1024 * 1024 * 1024),
                totalFiles = 3500
            )
        )

        val overview = StorageAnalyzer.calculateOverview(
            volumeName = "Internal Storage",
            volumePath = "/storage/emulated/0",
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            categoryStats = categoryStats,
            snapshots = snapshots,
            largeFilesSize = 5L * 1024 * 1024 * 1024,
            largeFilesCount = 12,
            apksSize = 1L * 1024 * 1024 * 1024,
            apksCount = 4
        )

        assertEquals("Internal Storage", overview.volumeName)
        assertEquals(totalBytes, overview.totalBytes)
        assertEquals(usedBytes, overview.usedBytes)
        assertEquals(freeBytes, overview.freeBytes)
        assertEquals(3800L, overview.totalFiles)
        assertEquals(7, overview.categoryBreakdown.size)
        assertTrue(overview.storageGrowthText?.contains("added since last scan") == true)
        assertEquals(2, overview.quickInsights.size)
    }

    @Test
    fun unindexedUsedSpaceKeepsItsShareOfTotalCapacity() {
        val gb = 1_000_000_000L
        val overview = StorageAnalyzer.calculateOverview(
            volumeName = "Test", volumePath = "/test", totalBytes = 100 * gb,
            freeBytes = 10 * gb,
            categoryStats = listOf(CategoryStat(StorageCategory.VIDEOS.name, 20 * gb, 2)),
            snapshots = emptyList(),
            appsSize = 50 * gb, appsCount = 5 // Deliberately ignored in file-only mode.
        )
        assertEquals(90 * gb, overview.usedBytes)
        assertEquals(20 * gb, overview.indexedBytes)
        assertEquals(70 * gb, overview.unindexedUsedBytes)
        assertTrue(overview.categoryBreakdown.none { it.category == StorageCategory.APPS })
        val segments = StorageAnalyzer.storageBarSegments(overview)
        assertEquals(0.2f, segments.single { it.kind == StorageSegmentKind.INDEXED }.fraction, 0.0001f)
        assertEquals(0.7f, segments.single { it.kind == StorageSegmentKind.UNINDEXED }.fraction, 0.0001f)
        assertEquals(0.1f, segments.single { it.kind == StorageSegmentKind.FREE }.fraction, 0.0001f)
        assertEquals(1f, segments.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun scanCoverageBelongsToTheSelectedVolume() {
        val snapshot = ScanSnapshotEntity(
            volumePath = "/test", volumeName = "Test", totalDeviceBytes = 100,
            usedBytes = 90, freeBytes = 10, totalFiles = 1,
            unreadableDirectories = 3, excludedEntries = 2
        )
        val overview = StorageAnalyzer.calculateOverview(
            volumeName = "Test", volumePath = "/test", totalBytes = 100, freeBytes = 10,
            categoryStats = emptyList(),
            snapshots = listOf(snapshot, snapshot.copy(volumePath = "/other", unreadableDirectories = 99))
        )
        assertTrue(overview.hasScanSnapshot)
        assertEquals(3L, overview.unreadableDirectories)
        assertEquals(2L, overview.excludedEntries)
    }
}
