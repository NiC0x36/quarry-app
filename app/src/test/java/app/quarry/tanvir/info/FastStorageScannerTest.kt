package app.quarry.tanvir.info

import app.quarry.tanvir.info.data.filesystem.FastStorageScanner
import app.quarry.tanvir.info.data.filesystem.ScanProgressUpdate
import app.quarry.tanvir.info.domain.scanner.DirectoryScanStatus
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FastStorageScannerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun unreadableDirectoryIsNotAnEmptyDirectory() = runBlocking {
        val root = temporaryFolder.newFolder("storage")
        val blocked = File(root, "blocked").apply { mkdir() }
        val empty = File(root, "empty").apply { mkdir() }
        val readable = File(root, "readable").apply { mkdir() }
        File(readable, "data.bin").writeBytes(ByteArray(12))
        val scanner = FastStorageScanner { directory ->
            if (directory == blocked) throw SecurityException("Access denied")
            directory.listFiles()
        }
        val result = (scanner.scanStorage(rootDirectory = root, estimatedTotalBytes = 100)
            .last() as ScanProgressUpdate.Finished).result
        assertEquals(1L, result.unreadableDirectories)
        assertEquals(12L, result.totalBytes)
        assertEquals(DirectoryScanStatus.UNREADABLE, result.files.single { it.path == blocked.path }.scanStatus)
        assertEquals(DirectoryScanStatus.COMPLETE, result.files.single { it.path == empty.path }.scanStatus)
        val rootEntity = result.files.single { it.path == root.path }
        assertEquals(DirectoryScanStatus.PARTIAL, rootEntity.scanStatus)
        assertEquals(12L, rootEntity.size)
    }

    @Test
    fun hiddenFilesAreIncludedAndExcludedDirectoriesRemainExplicit() = runBlocking {
        val root = temporaryFolder.newFolder("storage")
        File(root, ".hidden.bin").writeBytes(ByteArray(7))
        val excluded = File(root, "excluded").apply { mkdir() }
        File(excluded, "private.bin").writeBytes(ByteArray(20))
        val result = (FastStorageScanner().scanStorage(
            rootDirectory = root, estimatedTotalBytes = 100, excludedPaths = setOf(excluded.path)
        ).last() as ScanProgressUpdate.Finished).result
        assertEquals(7L, result.totalBytes)
        assertEquals(1L, result.excludedEntries)
        assertEquals(0L, result.unreadableDirectories)
        assertEquals(DirectoryScanStatus.EXCLUDED, result.files.single { it.path == excluded.path }.scanStatus)
        assertTrue(result.files.single { it.path == root.path }.hasIncompleteScan)
    }
}
