package app.quarry.tanvir.info.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.SearchMatcher
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.model.StorageFormatter
import app.quarry.tanvir.info.ui.explore.FileListView
import app.quarry.tanvir.info.ui.explore.FileSortOrder
import app.quarry.tanvir.info.ui.explore.FilterSortBottomSheet

/** Searchable file information, without selection or file-management actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun HomeItemListBottomSheet(
    title: String,
    category: StorageCategory,
    files: List<FileEntity>,
    onFileClick: (FileEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(FileSortOrder.SIZE_DESC) }
    var showHiddenFiles by remember { mutableStateOf(true) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val displayed = remember(files, searchQuery, sortOrder, showHiddenFiles) {
        files.filter {
            SearchMatcher.matches(it.name, it.path, searchQuery) &&
                (showHiddenFiles || (!it.name.startsWith(".") && !it.path.contains("/.")))
        }.sortedWith(sortOrder.comparator(keepDirectoriesFirst = true))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${files.size} items · ${StorageFormatter.formatBytes(files.sumOf { it.size })}" +
                            if (files.any { it.hasIncompleteScan }) " known" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(Icons.Rounded.FilterList, contentDescription = "Filter and sort")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search files and folders") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FileListView(
                files = displayed,
                selectedPaths = emptySet(),
                isSelectionMode = false,
                onItemClick = onFileClick,
                onItemLongClick = onFileClick,
                onToggleSelect = {},
                emptyMessage = "No matching scanned items.",
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (showFilterSheet) {
        FilterSortBottomSheet(
            currentSort = sortOrder,
            showHiddenFiles = showHiddenFiles,
            onApply = { sort, hidden -> sortOrder = sort; showHiddenFiles = hidden },
            onDismiss = { showFilterSheet = false }
        )
    }
}
