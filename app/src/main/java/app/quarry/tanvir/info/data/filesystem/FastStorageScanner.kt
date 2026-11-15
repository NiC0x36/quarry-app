package app.quarry.tanvir.info.data.filesystem

import android.os.Environment
import android.os.StatFs
import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.domain.scanner.FolderScanStatus
import app.quarry.tanvir.info.domain.scanner.DirectoryScanStatus
import app.quarry.tanvir.info.domain.scanner.ScanProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

class FastStorageScanner(
    private val directoryLister: (File) -> Array<File>? = { directory ->
        directory.listFiles() ?: directory.list()?.map { File(directory, it) }?.toTypedArray()
    }
) {

    data class ScanResult(
        val files: List<FileEntity>,
        val totalFiles: Long,
        val totalBytes: Long,
        val durationMs: Long,
        val unreadableDirectories: Long = 0,
        val excludedEntries: Long = 0
    )

    fun scanStorage(
        rootDirectory: File = Environment.getExternalStorageDirectory(),
        volumeId: String = "internal_storage",
        volumeName: String = "Internal Storage",
        estimatedTotalBytes: Long = getUsedStorageBytes(rootDirectory),
        includeHiddenFiles: Boolean = true,
        excludedPaths: Set<String> = emptySet()
    ): Flow<ScanProgressUpdate> = flow {
        val startTime = System.currentTimeMillis()
        var scannedFilesCount = 0L
        var scannedBytesCount = 0L
        var lastEmitTime = 0L
        var unreadableDirectories = 0L
        var excludedEntries = 0L

        val entitiesList = mutableListOf<FileEntity>()
        val directorySizeMap = mutableMapOf<String, Long>()
        val directoryDirectCountMap = mutableMapOf<String, Int>()
        val directoryStatuses = mutableMapOf<String, String>()

        // Post-order processing list for calculating directory total sizes accurately
        val discoveredDirectories = mutableListOf<File>()

        val queue = ArrayDeque<File>()
        queue.add(rootDirectory)

        // Top level folder status tracker for visual progress (e.g. Download, DCIM, Pictures, Android, etc.)
        val topLevelFolders = mutableMapOf<String, Boolean>()

        while (queue.isNotEmpty()) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Scan cancelled by user")
            }

            val currentDir = queue.removeFirst()
            discoveredDirectories.add(currentDir)

            directoryStatuses.putIfAbsent(currentDir.absolutePath, DirectoryScanStatus.COMPLETE)
            val isTopLevel = currentDir.parentFile?.absolutePath == rootDirectory.absolutePath
            if (isTopLevel) {
                topLevelFolders[currentDir.name] = false
            }

            val children = try {
                directoryLister(currentDir)
            } catch (e: Exception) {
                null
            }

            if (children != null) {
                var directFilesAndFoldersCount = 0
                for (child in children) {
                    if (!currentCoroutineContext().isActive) {
                        throw CancellationException("Scan cancelled by user")
                    }

                    val isHiddenItem = child.name.startsWith(".") || child.isHidden
                    val isExcluded = (!includeHiddenFiles && isHiddenItem) ||
                        app.quarry.tanvir.info.domain.scanner.ExclusionMatcher.isExcluded(child.absolutePath, excludedPaths)
                    if (isExcluded) {
                        excludedEntries++
                        directoryStatuses[currentDir.absolutePath] = DirectoryScanStatus.PARTIAL
                        if (child.isDirectory) {
                            discoveredDirectories.add(child)
                            directoryStatuses[child.absolutePath] = DirectoryScanStatus.EXCLUDED
                        }
                        continue
                    }

                    if (child.isDirectory) {
                        if (currentDir == rootDirectory) topLevelFolders[child.name] = false
                        queue.addLast(child)
                        directFilesAndFoldersCount++
                    } else {
                        val fileSize = try { child.length() } catch (e: Exception) { 0L }
                        val ext = child.extension
                        val category = StorageCategory.fromExtension(ext)
                        val path = child.absolutePath
                        val isScreenshot = isScreenshotPath(path, child.name)
                        val isDownload = isDownloadPath(path)

                        scannedFilesCount++
                        scannedBytesCount += fileSize
                        directFilesAndFoldersCount++

                        val fileEntity = FileEntity(
                            volumeId = volumeId,
                            path = path,
                            name = child.name,
                            size = fileSize,
                            isDirectory = false,
                            category = category.name,
                            mimeType = null,
                            lastModified = child.lastModified(),
                            parentPath = currentDir.absolutePath,
                            extension = ext.lowercase(Locale.ROOT),
                            isScreenshot = isScreenshot,
                            isDownload = isDownload,
                            directChildrenCount = 0
                        )
                        entitiesList.add(fileEntity)

                        // Add to current directory size
                        directorySizeMap[currentDir.absolutePath] = (directorySizeMap[currentDir.absolutePath] ?: 0L) + fileSize
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime > 75 || scannedFilesCount % 200 == 0L) {
                        lastEmitTime = now
                        emit(
                            ScanProgressUpdate.Progress(
                                ScanProgress(
                                    currentPath = child.absolutePath,
                                    currentFolderName = currentDir.name,
                                    filesScanned = scannedFilesCount,
                                    bytesScanned = scannedBytesCount,
                                    estimatedTotalBytes = estimatedTotalBytes,
                                    isComplete = false,
                                    activeFolders = topLevelFolders.map { FolderScanStatus(it.key, it.value) }
                                )
                            )
                        )
                    }
                }
                directoryDirectCountMap[currentDir.absolutePath] = directFilesAndFoldersCount
            } else {
                directoryStatuses[currentDir.absolutePath] = DirectoryScanStatus.UNREADABLE
                unreadableDirectories++
            }

            if (isTopLevel) {
                topLevelFolders[currentDir.name] = true
            }
        }

        // Aggregate folder sizes from bottom up
        for (i in discoveredDirectories.indices.reversed()) {
            val dir = discoveredDirectories[i]
            val dirPath = dir.absolutePath
            val dirSize = directorySizeMap[dirPath] ?: 0L
            val parentPath = dir.parentFile?.absolutePath
            val scanStatus = directoryStatuses[dirPath] ?: DirectoryScanStatus.COMPLETE

            if (parentPath != null) {
                directorySizeMap[parentPath] = (directorySizeMap[parentPath] ?: 0L) + dirSize
                if (scanStatus != DirectoryScanStatus.COMPLETE &&
                    directoryStatuses[parentPath] == DirectoryScanStatus.COMPLETE) {
                    directoryStatuses[parentPath] = DirectoryScanStatus.PARTIAL
                }
            }

            val dirEntity = FileEntity(
                volumeId = volumeId,
                path = dirPath,
                name = if (dirPath == rootDirectory.absolutePath) volumeName else dir.name,
                size = dirSize,
                isDirectory = true,
                category = StorageCategory.OTHER.name,
                mimeType = null,
                lastModified = dir.lastModified(),
                parentPath = parentPath,
                extension = "",
                isScreenshot = false,
                isDownload = isDownloadPath(dirPath),
                directChildrenCount = directoryDirectCountMap[dirPath] ?: 0,
                scanStatus = scanStatus
            )
            entitiesList.add(dirEntity)
        }

        val totalDuration = System.currentTimeMillis() - startTime

        emit(
            ScanProgressUpdate.Finished(
                ScanResult(
                    files = entitiesList,
                    totalFiles = scannedFilesCount,
                    totalBytes = scannedBytesCount,
                    durationMs = totalDuration,
                    unreadableDirectories = unreadableDirectories,
                    excludedEntries = excludedEntries
                )
            )
        )
    }


    private fun isScreenshotPath(path: String, name: String): Boolean {
        val lowerPath = path.lowercase(Locale.ROOT)
        val lowerName = name.lowercase(Locale.ROOT)
        return lowerPath.contains("screenshot") ||
                lowerName.startsWith("screenshot") ||
                lowerName.startsWith("screen_") ||
                lowerPath.contains("dcim/screenshots") ||
                lowerPath.contains("pictures/screenshots")
    }

    private fun isDownloadPath(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        return lower.contains("/download") || lower.contains("/downloads")
    }

    companion object {
        fun getUsedStorageBytes(root: File): Long {
            return try {
                val statFs = StatFs(root.absolutePath)
                val totalBytes = statFs.totalBytes
                val availableBytes = statFs.availableBytes
                totalBytes - availableBytes
            } catch (e: Exception) {
                // StatFs fails on some external filesystems (NTFS-3G, exFAT via older FUSE).
                // Fall back to File API which uses a different JNI path (statvfs).
                try {
                    val total = root.totalSpace
                    val free = root.usableSpace
                    (total - free).coerceAtLeast(0L)
                } catch (_: Exception) { 0L }
            }
        }

        fun getTotalStorageBytes(root: File): Long {
            return try {
                val statFs = StatFs(root.absolutePath)
                statFs.totalBytes
            } catch (e: Exception) {
                try { root.totalSpace } catch (_: Exception) { 0L }
            }
        }

        fun getFreeStorageBytes(root: File): Long {
            return try {
                val statFs = StatFs(root.absolutePath)
                statFs.availableBytes
            } catch (e: Exception) {
                // Prefer usableSpace (accounts for reserved blocks), fall back to freeSpace
                try { root.usableSpace } catch (_: Exception) {
                    try { root.freeSpace } catch (_: Exception) { 0L }
                }
            }
        }
    }
}

sealed class ScanProgressUpdate {
    data class Progress(val progress: ScanProgress) : ScanProgressUpdate()
    data class Finished(val result: FastStorageScanner.ScanResult) : ScanProgressUpdate()
}
