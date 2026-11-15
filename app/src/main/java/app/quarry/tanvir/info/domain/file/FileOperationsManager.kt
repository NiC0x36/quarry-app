package app.quarry.tanvir.info.domain.file

import android.content.Context
import app.quarry.tanvir.info.domain.scanner.ScanRepository

/** Denies file actions before any path is opened or any filesystem/database write occurs. */
@Suppress("UNUSED_PARAMETER")
class FileOperationsManager() {
    constructor(context: Context, repository: ScanRepository) : this()

    suspend fun renameFile(oldPath: String, newName: String): Result<String> = AnalysisOnlyPolicy.denied()
    suspend fun moveToTrash(path: String): Result<TrashItem> = AnalysisOnlyPolicy.denied()
    suspend fun bulkMoveToTrash(paths: List<String>): Map<String, Boolean> = AnalysisOnlyPolicy.deniedBatch(paths)
    suspend fun deletePermanently(path: String): Result<Boolean> = AnalysisOnlyPolicy.denied()
    suspend fun bulkDelete(paths: List<String>): Map<String, Boolean> = AnalysisOnlyPolicy.deniedBatch(paths)

    // External viewers could modify files or install APKs. This variant stays inside its own viewer.
    fun openFile(filePath: String) = Unit
    fun shareFile(filePath: String) = Unit
}
