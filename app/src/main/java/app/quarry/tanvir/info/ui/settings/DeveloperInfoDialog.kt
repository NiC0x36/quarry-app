package app.quarry.tanvir.info.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.quarry.tanvir.info.ui.components.LocalAnimatedDismiss
import app.quarry.tanvir.info.ui.components.QuarryFullScreenDialog
import app.quarry.tanvir.info.ui.components.QuarryToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        @Suppress("DEPRECATION")
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "Unknown"
    }
    QuarryFullScreenDialog(onDismissRequest = onDismiss) {
        val dismiss = LocalAnimatedDismiss.current
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("About Quarry Local") },
                    navigationIcon = {
                        IconButton(onClick = dismiss) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Quarry Local $version", style = MaterialTheme.typography.titleLarge)
                Text("An analysis-only fork for exploring folder sizes. Your files cannot be deleted, renamed, installed, or shared by this version.")
                Text("Scan results stay on this device. Android cloud-backup and device-transfer exclusions are configured, and the index and settings use Android's no-backup storage.")
                Text("Android protects private app directories. Unindexed space may include app data, system files, excluded folders, or filesystem overhead; it is not necessarily removable.")
                Text("Based on Quarry by Tanvir Hasan. Original copyright and Apache License 2.0 notices are retained.")
                TextButton(onClick = { openProjectLink(context, "https://github.com/NiC0x36/quarry-app") }) {
                    Text("Quarry Local source code")
                }
                TextButton(onClick = { openProjectLink(context, "https://github.com/tanvirr007/quarry-app") }) {
                    Text("Original Quarry project")
                }
                Text("Links open in your browser only when you select them.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun openProjectLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { QuarryToast.show(context, "Unable to open link") }
}
