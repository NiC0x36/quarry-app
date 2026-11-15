package app.quarry.tanvir.info.ui.home

import app.quarry.tanvir.info.ui.components.QuarryToast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.activity.compose.BackHandler
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.quarry.tanvir.info.R
import androidx.fragment.app.FragmentActivity
import app.quarry.tanvir.info.domain.analyzer.QuickInsight
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.scanner.ScanState
import app.quarry.tanvir.info.ui.explore.FileDetailsBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onNavigateToCategory: ((StorageCategory) -> Unit)? = null,
    onNavigateToInsight: ((QuickInsight) -> Unit)? = null,
    onNavigateToExplore: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var showExitDialog by remember { mutableStateOf(false) }
    val haptics = app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics.current

    val hasActiveHomeScreenOverlay = uiState.activeSheetData != null ||
        uiState.selectedDetailFile != null

    BackHandler(enabled = !hasActiveHomeScreenOverlay && !showExitDialog) {
        showExitDialog = true
    }

    // User Message Toast
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            QuarryToast.show(context, msg)
            viewModel.clearUserMessage()
        }
    }

    // Refresh permission status and volumes when activity resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState()
                viewModel.refreshVolumes()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isPullRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.scanState) {
        if (uiState.scanState !is ScanState.Scanning) {
            isPullRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshing && uiState.scanState is ScanState.Scanning,
        onRefresh = {
            if (uiState.hasStoragePermission && uiState.scanState !is ScanState.Scanning) {
                isPullRefreshing = true
                haptics.click()
                viewModel.startScan()
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        // Permission Banner if required
        if (!uiState.hasStoragePermission) {
            PermissionBanner(
                onGrantClick = {
                    viewModel.refreshPermissionState()
                }
            )
        }

        // Live Scanning Progress Card (Shown during active scan)
        AnimatedVisibility(
            visible = uiState.scanState is ScanState.Scanning,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val scanningState = uiState.scanState as? ScanState.Scanning
            if (scanningState != null) {
                ScanProgressCard(
                    progress = scanningState.progress,
                    onCancelClick = { viewModel.cancelScan() }
                )
            }
        }

        // Storage Overview Card
        StorageOverviewCard(
            overview = uiState.overview,
            availableVolumes = uiState.availableVolumes,
            selectedVolume = uiState.selectedVolume,
            onSelectVolume = { vol -> viewModel.selectVolume(vol) },
            onClick = onNavigateToExplore
        )

        // Analyze Storage Button (Shown when not currently scanning)
        val haptics = app.quarry.tanvir.info.domain.haptics.LocalQuarryHaptics.current
        if (uiState.scanState !is ScanState.Scanning) {
            Button(
                onClick = {
                    haptics.click()
                    viewModel.startScan()
                },
                enabled = uiState.hasStoragePermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.analyze_storage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Quick Insights Section
        if (uiState.isQuickInsightsEnabled) {
            QuickInsightsSection(
                insights = uiState.visibleQuickInsights,
            onInsightClick = { insight ->
                if (onNavigateToInsight != null) {
                    onNavigateToInsight(insight)
                } else {
                    viewModel.selectInsight(insight)
                }
            },
                onInsightLongClick = { insight ->
                    viewModel.selectInsight(insight)
                },
                hapticsEnabled = uiState.isHapticsEnabled,
                hapticStrength = uiState.hapticStrength
            )
        }

        // Storage Categories Grid (respects Miscellaneous visibility, min 4)
        CategoryGrid(
            categories = uiState.visibleCategoryBreakdown.filter { it.category != StorageCategory.APPS },
            onCategoryClick = { category ->
                if (onNavigateToCategory != null) {
                    onNavigateToCategory(category)
                } else {
                    viewModel.selectCategory(category)
                }
            },
            onCategoryLongClick = { category -> viewModel.selectCategory(category) },
            hapticsEnabled = uiState.isHapticsEnabled,
            hapticStrength = uiState.hapticStrength
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
    }

    // Category / Insight Files Detail Sheet (Stays on Home)
    uiState.activeSheetData?.let { sheetData ->
        HomeItemListBottomSheet(
            title = sheetData.title,
            category = sheetData.category,
            files = sheetData.files,
            onFileClick = { file ->
                viewModel.selectDetailFile(file)
            },
            onDismiss = {
                viewModel.dismissSheet()
            }
        )
    }

    // Individual File Details Sheet (Stays on Home)
    uiState.selectedDetailFile?.let { file ->
        FileDetailsBottomSheet(
            file = file,
            onDismiss = { viewModel.selectDetailFile(null) }
        )
    }

    // Exit Confirmation Dialog
    if (showExitDialog) {
        ExitConfirmationDialog(
            onDismiss = { showExitDialog = false },
            onConfirmExit = {
                showExitDialog = false
                activity?.finish()
            }
        )
    }
}
