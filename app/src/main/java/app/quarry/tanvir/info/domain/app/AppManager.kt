package app.quarry.tanvir.info.domain.app

import android.content.Context

/** App enumeration and management are not part of this analysis-only variant. */
@Suppress("UNUSED_PARAMETER")
class AppManager() {
    constructor(context: Context) : this()

    fun hasUsageAccess(): Boolean = false
    suspend fun getInstalledApps(includeSystemApps: Boolean = true): List<InstalledApp> = emptyList()
    fun openApp(packageName: String): Boolean = false
    fun openAppDetails(packageName: String) = Unit
    fun uninstall(packageName: String) = Unit
    fun openUsageAccessSettings() = Unit
}
