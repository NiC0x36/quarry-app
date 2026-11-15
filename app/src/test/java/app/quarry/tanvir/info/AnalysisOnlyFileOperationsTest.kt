package app.quarry.tanvir.info

import app.quarry.tanvir.info.domain.file.FileOperationsManager
import app.quarry.tanvir.info.domain.file.TrashManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The boundary must reject even direct API calls, independently of the visible UI. */
class AnalysisOnlyFileOperationsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun directFileActionsLeaveUserDataUntouched() = runBlocking {
        val source = folder.newFile("private.txt").apply { writeText("keep this data") }
        val destination = java.io.File(source.parentFile, "renamed.txt")
        val nested = folder.newFolder("documents")
        java.io.File(nested, "child.txt").writeText("keep this child")
        val ops = FileOperationsManager()
        val paths = listOf(source.absolutePath, nested.absolutePath)

        assertTrue(ops.renameFile(source.absolutePath, destination.name).isFailure)
        assertTrue(ops.renameFile(source.absolutePath, "../escaped.txt").isFailure)
        assertTrue(ops.moveToTrash(source.absolutePath).isFailure)
        assertEquals(paths.associateWith { false }, ops.bulkMoveToTrash(paths))
        assertTrue(ops.deletePermanently(nested.absolutePath).isFailure)
        assertEquals(paths.associateWith { false }, ops.bulkDelete(paths))
        ops.openFile(source.absolutePath)
        ops.shareFile(source.absolutePath)

        assertEquals("keep this data", source.readText())
        assertEquals("keep this child", java.io.File(nested, "child.txt").readText())
        assertFalse(destination.exists())
        assertEquals(setOf("private.txt", "documents"), folder.root.list()!!.toSet())
    }

    @Test fun trashApisCannotRestoreEraseOrInitializeStorage() = runBlocking {
        val source = folder.newFile("private.txt").apply { writeText("keep this data") }
        val trash = TrashManager()
        val paths = listOf(source.absolutePath)
        trash.loadMetadata()
        assertTrue(trash.moveToTrash(source.absolutePath).isFailure)
        assertEquals(paths.associateWith { false }, trash.moveToTrashBatch(paths))
        assertTrue(trash.restoreItem(source.absolutePath).isFailure)
        assertEquals(paths.associateWith { false }, trash.restoreItemsBatch(paths))
        assertTrue(trash.deletePermanently(source.absolutePath).isFailure)
        assertEquals(paths.associateWith { false }, trash.deletePermanentlyBatch(paths))
        assertTrue(trash.emptyTrash().isFailure)
        assertTrue(trash.purgeExpiredItems(0).isFailure)
        assertTrue(trash.trashItems.value.isEmpty())
        assertEquals("keep this data", source.readText())
        assertEquals(listOf("private.txt"), folder.root.list()!!.toList())
    }
}
