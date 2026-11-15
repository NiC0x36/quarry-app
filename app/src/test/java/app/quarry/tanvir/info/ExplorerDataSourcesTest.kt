package app.quarry.tanvir.info

import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.ui.explore.ExploreViewMode
import app.quarry.tanvir.info.ui.explore.ExplorerDataSources
import app.quarry.tanvir.info.ui.explore.ExplorerSearchRequest
import app.quarry.tanvir.info.ui.explore.FileSortOrder
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerDataSourcesTest {
    @Test
    fun browsingFoldersNeverSubscribesToTheFullVolumeFileSource() = runBlocking {
        for (mode in listOf(ExploreViewMode.TREEMAP, ExploreViewMode.LIST, ExploreViewMode.FOLDERS, ExploreViewMode.LARGEST)) {
            var loads = 0
            val result = ExplorerDataSources.typesFiles(
                flowOf(mode), flowOf("internal"), flowOf("")
            ) { _: String ->
                loads++
                flowOf(listOf("full index"))
            }.first()
            assertTrue(result.isEmpty())
            assertEquals(0, loads)
        }
    }

    @Test
    fun leavingTypesOrStartingSearchCancelsAndReleasesItsFullVolumeSource() = runBlocking {
        val mode = MutableStateFlow(ExploreViewMode.TYPES)
        val query = MutableStateFlow("")
        val results = Channel<List<String>>(Channel.UNLIMITED)
        var loads = 0
        var cancellations = 0
        val job = launch {
            ExplorerDataSources.typesFiles(mode, flowOf("internal"), query) { volume ->
                loads++
                flow {
                    try {
                        emit(listOf(volume))
                        awaitCancellation()
                    } finally {
                        cancellations++
                    }
                }
            }.collect { results.send(it) }
        }
        try {
            withTimeout(3_000) {
                assertEquals(listOf("internal"), results.receive())
                mode.value = ExploreViewMode.TREEMAP
                assertTrue(results.receive().isEmpty())
                assertEquals(1, cancellations)
                mode.value = ExploreViewMode.TYPES
                assertEquals(listOf("internal"), results.receive())
                query.value = "video"
                assertTrue(results.receive().isEmpty())
                assertEquals(2, cancellations)
                assertEquals(2, loads)
            }
        } finally {
            job.cancel()
            results.close()
        }
    }

    @Test
    fun blankSearchCancelsAndReleasesItsVolumeSource() = runBlocking {
        val requests = MutableStateFlow(request("   ").copy(volumeId = "external"))
        val results = Channel<List<FileEntity>>(Channel.UNLIMITED)
        val loadedVolumes = mutableListOf<String>()
        var cancellations = 0
        val job = launch {
            ExplorerDataSources.searchFiles(requests) { volume ->
                loadedVolumes += volume
                flow {
                    try {
                        emit(listOf(file("/storage/match.txt", 10)))
                        awaitCancellation()
                    } finally {
                        cancellations++
                    }
                }
            }.collect { results.send(it) }
        }
        try {
            withTimeout(3_000) {
                assertTrue(results.receive().isEmpty())
                assertTrue(loadedVolumes.isEmpty())
                requests.value = requests.value.copy(query = "match")
                assertEquals(1, results.receive().size)
                assertEquals(listOf("external"), loadedVolumes)
                requests.value = requests.value.copy(query = "")
                assertTrue(results.receive().isEmpty())
                assertEquals(1, cancellations)
            }
        } finally {
            job.cancel()
            results.close()
        }
    }

    @Test
    fun unicodeCaseAndCompactSearchKeepTheExistingMatcherBehavior() {
        val files = listOf(
            file("/storage/ÄPFEL_Überblick.jpg", 20),
            file("/storage/Wake.Up.Dead.Man.mkv", 10)
        )
        assertEquals(files.take(1), ExplorerDataSources.matchingSearchResults(files, request("äpfel überblick")))
        assertEquals(files.drop(1), ExplorerDataSources.matchingSearchResults(files, request("wakeup")))
        assertEquals(files.drop(1), ExplorerDataSources.matchingSearchResults(files, request("wake dead")))
    }

    @Test
    fun modeFilteringAndSortingPrecedeTheResultLimit() {
        val folders = (0 until 600).map { file("/storage/current/match_dir$it", it.toLong(), directory = true) }
        val files = (0 until 800).map { file("/storage/other/match_file$it.txt", it.toLong()) }
        val types = ExplorerDataSources.matchingSearchResults(
            folders + files, request("match").copy(mode = ExploreViewMode.TYPES, currentPath = "/storage/current")
        )
        assertEquals(500, types.size)
        assertTrue(types.none { it.isDirectory })
        assertEquals(799L, types.first().size)
        assertEquals(300L, types.last().size)

        // Matching files in the preferred folder must not consume the folder-only result cap.
        val folderResults = ExplorerDataSources.matchingSearchResults(
            files + folders, request("match").copy(mode = ExploreViewMode.FOLDERS, currentPath = "/storage/other")
        )
        assertEquals(500, folderResults.size)
        assertTrue(folderResults.all { it.isDirectory })
        assertEquals(599L, folderResults.first().size)
    }

    private fun file(path: String, size: Long, directory: Boolean = false) = FileEntity(
        path = path, name = path.substringAfterLast('/'), size = size, isDirectory = directory,
        category = "OTHER", extension = if (directory) "" else path.substringAfterLast('.', "")
    )

    private fun request(query: String) = ExplorerSearchRequest(
        volumeId = "internal", query = query, currentPath = "/storage",
        showHidden = true, category = null, sort = FileSortOrder.SIZE_DESC
    )
}
