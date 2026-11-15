package app.quarry.tanvir.info.domain.file

import android.content.Context
import app.quarry.tanvir.info.domain.scanner.ScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrashItem(
    val id: String,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val deletedTimestamp: Long
)

/** Read-only service: never discovers, creates, restores, or purges a trash directory. */
@Suppress("UNUSED_PARAMETER")
class TrashManager() {
    constructor(context: Context, repository: ScanRepository) : this()

    private val items = MutableStateFlow<List<TrashItem>>(emptyList())
    val trashItems: StateFlow<List<TrashItem>> = items.asStateFlow()

    fun loadMetadata() = Unit
    suspend fun moveToTrash(path: String): Result<TrashItem> = AnalysisOnlyPolicy.denied()
    suspend fun moveToTrashBatch(paths: List<String>): Map<String, Boolean> = AnalysisOnlyPolicy.deniedBatch(paths)
    suspend fun restoreItem(trashId: String): Result<String> = AnalysisOnlyPolicy.denied()
    suspend fun restoreItemsBatch(trashIds: List<String>): Map<String, Boolean> = AnalysisOnlyPolicy.deniedBatch(trashIds)
    suspend fun deletePermanently(trashId: String): Result<Boolean> = AnalysisOnlyPolicy.denied()
    suspend fun deletePermanentlyBatch(trashIds: List<String>): Map<String, Boolean> = AnalysisOnlyPolicy.deniedBatch(trashIds)
    suspend fun emptyTrash(): Result<Int> = AnalysisOnlyPolicy.denied()
    suspend fun purgeExpiredItems(maxAgeDays: Int = 30): Result<Int> = AnalysisOnlyPolicy.denied()

    companion object {
        private val instance = TrashManager()
        fun getInstance(context: Context, repository: ScanRepository): TrashManager = instance
    }
}
