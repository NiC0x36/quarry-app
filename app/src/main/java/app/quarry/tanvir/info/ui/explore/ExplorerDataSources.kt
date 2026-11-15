package app.quarry.tanvir.info.ui.explore

import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.SearchMatcher
import app.quarry.tanvir.info.domain.model.StorageCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class ExplorerSearchRequest(
    val volumeId: String,
    val query: String,
    val currentPath: String,
    val showHidden: Boolean,
    val category: String?,
    val sort: FileSortOrder,
    val mode: ExploreViewMode = ExploreViewMode.LIST
)

/** Source selection is separate from Android so subscriptions can be regression tested. */
object ExplorerDataSources {
    const val SEARCH_LIMIT = 500

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> typesFiles(
        modes: Flow<ExploreViewMode>,
        volumeIds: Flow<String>,
        queries: Flow<String>,
        loadFiles: (String) -> Flow<List<T>>
    ): Flow<List<T>> = combine(modes, volumeIds, queries) { mode, volume, query ->
        volume.takeIf { mode == ExploreViewMode.TYPES && query.isBlank() }
    }.distinctUntilChanged().flatMapLatest { volume ->
        if (volume == null) flowOf(emptyList()) else loadFiles(volume)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun searchFiles(
        requests: Flow<ExplorerSearchRequest>,
        loadVolumeFiles: (String) -> Flow<List<FileEntity>>
    ): Flow<List<FileEntity>> = requests.distinctUntilChanged().flatMapLatest { request ->
        // Treemap has no search. Largest keeps its existing bounded 100-file source.
        if (request.query.isBlank() || request.mode == ExploreViewMode.TREEMAP ||
            request.mode == ExploreViewMode.LARGEST) flowOf(emptyList())
        else loadVolumeFiles(request.volumeId).map { files -> matchingSearchResults(files, request) }
    }.flowOn(Dispatchers.Default)

    fun matchingSearchResults(files: List<FileEntity>, request: ExplorerSearchRequest): List<FileEntity> =
        files.asSequence().filter { file ->
            val visible = request.showHidden || (!file.name.startsWith(".") && !file.path.contains("/."))
            val modeMatches = when (request.mode) {
                ExploreViewMode.FOLDERS -> file.isDirectory
                ExploreViewMode.TYPES, ExploreViewMode.LARGEST -> !file.isDirectory
                else -> true
            }
            val categoryMatches = request.category == null ||
                (!file.isDirectory && StorageCategory.fromExtension(file.extension).name == request.category)
            visible && modeMatches && categoryMatches && SearchMatcher.matches(file.name, file.path, request.query)
        }.sortedWith(
            compareByDescending<FileEntity> { it.path.startsWith(request.currentPath) }
                .then(request.sort.comparator(keepDirectoriesFirst = true))
        ).take(SEARCH_LIMIT).toList()
}
