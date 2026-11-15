package app.quarry.tanvir.info.ui.explore

import app.quarry.tanvir.info.ui.components.QuarryToast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.ui.home.HomeItemListBottomSheet

private data class ExploreCategorySheetData(
    val title: String,
    val category: StorageCategory,
    val files: List<app.quarry.tanvir.info.data.database.FileEntity>
)

@Composable
fun ExploreScreen(
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val directoryFiles by viewModel.directoryFiles.collectAsStateWithLifecycle()
    val allIndexedFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val allCategorizedFiles by viewModel.allCategorizedFiles.collectAsStateWithLifecycle()
    val treemapNodes by viewModel.treemapLayoutNodes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var activeCategorySheet by remember { mutableStateOf<ExploreCategorySheetData?>(null) }

    // Prioritized Back handling: Dialogs -> Details Sheet -> Selection -> Search -> Folder Hierarchy -> System (Home)
    val isFolderViewMode = uiState.viewMode == ExploreViewMode.TREEMAP ||
            uiState.viewMode == ExploreViewMode.LIST ||
            uiState.viewMode == ExploreViewMode.FOLDERS
    val canNavigateUpFolder = isFolderViewMode &&
            uiState.currentPath != uiState.rootPath

    val hasActiveExploreState = activeCategorySheet != null ||
            uiState.isDeleteCountdownVisible ||
            uiState.activeRenameFile != null ||
            uiState.activeDetailsFile != null ||
            uiState.isSelectionMode ||
            uiState.searchQuery.isNotEmpty() ||
            canNavigateUpFolder

    val haptics = app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics.current

    BackHandler(enabled = hasActiveExploreState) {
        haptics.click()
        when {
            activeCategorySheet != null -> activeCategorySheet = null
            uiState.isDeleteCountdownVisible -> viewModel.dismissDeleteDialog()
            uiState.activeRenameFile != null -> viewModel.dismissRename()
            uiState.activeDetailsFile != null -> viewModel.hideDetails()
            uiState.isSelectionMode -> viewModel.clearSelection()
            uiState.searchQuery.isNotEmpty() -> viewModel.setSearchQuery("")
            canNavigateUpFolder -> viewModel.navigateUp()
        }
    }

    // User Message Toast
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            QuarryToast.show(context, msg)
            viewModel.clearUserMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var showFilterSheet by remember { mutableStateOf(false) }

        // View Mode Selector Chips
        val viewModeScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(viewModeScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExploreViewMode.entries.forEach { mode ->
                FilterChip(
                    selected = uiState.viewMode == mode,
                    onClick = {
                        haptics.click()
                        viewModel.setViewMode(mode)
                    },
                    label = { Text(mode.title, fontWeight = FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Search & Filter Bar (Active & visible only for non-Treemap modes)
        AnimatedVisibility(
            visible = uiState.viewMode != ExploreViewMode.TREEMAP,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search files & folders…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                haptics.click()
                                viewModel.setSearchQuery("")
                            }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    ),
                    singleLine = true
                )

                // Modern Filter & Sort Button
                IconButton(
                    onClick = {
                        haptics.click()
                        showFilterSheet = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = "Filter and sort options",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (showFilterSheet && uiState.viewMode != ExploreViewMode.TREEMAP) {
            FilterSortBottomSheet(
                currentSort = uiState.sortOrder,
                showHiddenFiles = uiState.showHiddenFiles,
                changesScanInclusion = true,
                onApply = { newSort, newHidden ->
                    viewModel.setSortOrder(newSort)
                    viewModel.setShowHiddenFiles(newHidden)
                },
                onDismiss = { showFilterSheet = false }
            )
        }

        // Breadcrumb Navigation Bar (Visible in Treemap, List, Folders modes)
        if (uiState.viewMode == ExploreViewMode.TREEMAP || uiState.viewMode == ExploreViewMode.LIST || uiState.viewMode == ExploreViewMode.FOLDERS) {
            BreadcrumbBar(
                currentPath = uiState.currentPath,
                rootPath = uiState.rootPath,
                onNavigateToSegment = { targetPath ->
                    viewModel.navigateToDirectory(targetPath)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        allIndexedFiles.find { it.path == uiState.currentPath && it.hasIncompleteScan }?.let { folder ->
            Text(folder.scanStatusDescription, color = MaterialTheme.colorScheme.error)
        }
        if (uiState.viewMode == ExploreViewMode.TREEMAP) {
            Text(
                "Areas show scanned bytes. Zoom into folders or use List for small and unreadable items.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Main View Content
        Box(modifier = Modifier.weight(1f)) {
            val displayedFiles = if (uiState.searchQuery.isNotBlank()) {
                uiState.searchResults
            } else {
                directoryFiles
            }

            when (uiState.viewMode) {
                ExploreViewMode.TREEMAP -> {
                    TreemapCanvas(
                        nodes = treemapNodes,
                        currentPath = uiState.currentPath,
                        onNodeClick = { node ->
                            haptics.click()
                            if (node.isDirectory) {
                                viewModel.navigateToDirectory(node.path)
                            } else {
                                val fileEntity = directoryFiles.find { it.path == node.path }
                                if (fileEntity != null) {
                                    viewModel.showDetails(fileEntity)
                                }
                            }
                        },
                        onSizeMeasured = { w, h ->
                            viewModel.recalculateTreemap(w, h)
                        }
                    )
                }

                ExploreViewMode.LIST -> {
                    FileListView(
                        files = displayedFiles,
                        selectedPaths = emptySet(),
                        isSelectionMode = false,
                        emptyMessage = if (uiState.searchQuery.isNotBlank()) {
                            "No files matching \"${uiState.searchQuery}\""
                        } else {
                            "No files or folders in this location."
                        },
                        onItemClick = { file ->
                            if (file.isDirectory) {
                                viewModel.navigateToDirectory(file.path)
                            } else {
                                viewModel.showDetails(file)
                            }
                        },
                        onItemLongClick = { file ->
                            viewModel.showDetails(file)
                        },
                        onToggleSelect = { path ->
                            // Selection is disabled in the analysis-only build.
                        }
                    )
                }

                ExploreViewMode.LARGEST -> {
                    val largestToDisplay = if (uiState.searchQuery.isNotBlank()) {
                        uiState.largestFiles.filter { app.quarry.tanvir.info.domain.model.SearchMatcher.matches(it.name, it.path, uiState.searchQuery) }
                    } else {
                        uiState.largestFiles
                    }
                    LargestFilesView(
                        files = largestToDisplay,
                        onFileClick = { file -> viewModel.showDetails(file) }
                    )
                }

                ExploreViewMode.TYPES -> {
                    val filesToCategorize = if (uiState.searchQuery.isNotBlank()) {
                        allCategorizedFiles.filter { app.quarry.tanvir.info.domain.model.SearchMatcher.matches(it.name, it.path, uiState.searchQuery) }
                    } else {
                        allCategorizedFiles
                    }
                    FileTypeGroupView(
                        files = filesToCategorize,
                        sortOrder = uiState.sortOrder,
                        onCategoryClick = { category, catFiles ->
                            activeCategorySheet = ExploreCategorySheetData(
                                title = category.displayName,
                                category = category,
                                files = catFiles
                            )
                        }
                    )
                }

                ExploreViewMode.FOLDERS -> {
                    FolderSizeView(
                        folders = displayedFiles,
                        sortOrder = uiState.sortOrder,
                        onFolderClick = { folder -> viewModel.navigateToDirectory(folder.path) }
                    )
                }
            }
        }
    }

    // Category Files Bottom Sheet
    activeCategorySheet?.let { sheetData ->
        HomeItemListBottomSheet(
            title = sheetData.title,
            category = sheetData.category,
            files = sheetData.files,
            onFileClick = { file ->
                viewModel.showDetails(file)
            },
            onDismiss = {
                activeCategorySheet = null
            }
        )
    }

    // Active File Details Bottom Sheet
    uiState.activeDetailsFile?.let { file ->
        FileDetailsBottomSheet(
            file = file,
            onDismiss = { viewModel.hideDetails() },
            onOpenContainingFolder = { folderPath ->
                viewModel.navigateToDirectory(folderPath)
                if (uiState.viewMode == ExploreViewMode.LARGEST || uiState.viewMode == ExploreViewMode.TYPES) {
                    viewModel.setViewMode(ExploreViewMode.LIST)
                }
            }
        )
    }

}
