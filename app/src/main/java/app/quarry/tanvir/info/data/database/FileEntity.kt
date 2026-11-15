package app.quarry.tanvir.info.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Ignore
import androidx.room.ColumnInfo
import app.quarry.tanvir.info.domain.scanner.DirectoryScanStatus

@Entity(
    tableName = "files",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["volumeId"]),
        Index(value = ["volumeId", "isDirectory"]),
        Index(value = ["parentPath"]),
        Index(value = ["category"]),
        Index(value = ["size"]),
        Index(value = ["lastModified"]),
        Index(value = ["extension"]),
        Index(value = ["isDirectory"])
    ]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val volumeId: String = "internal_storage",
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val category: String,
    val mimeType: String? = null,
    val lastModified: Long = 0,
    val parentPath: String? = null,
    val extension: String = "",
    val isScreenshot: Boolean = false,
    val isDownload: Boolean = false,
    val directChildrenCount: Int = 0,
    val partialHash: String? = null,
    val fullHash: String? = null,
    @ColumnInfo(defaultValue = "'PARTIAL'")
    val scanStatus: String = DirectoryScanStatus.COMPLETE
) {
    @get:Ignore
    val hasIncompleteScan: Boolean
        get() = scanStatus != DirectoryScanStatus.COMPLETE

    @get:Ignore
    val scanStatusDescription: String
        get() = when (scanStatus) {
            DirectoryScanStatus.UNREADABLE -> "Not readable · size unknown"
            DirectoryScanStatus.EXCLUDED -> "Excluded from scan · size unknown"
            DirectoryScanStatus.PARTIAL -> "Partial scan · known size only"
            else -> "Scanned"
        }
}

data class CategoryStat(
    val category: String,
    val totalBytes: Long,
    val fileCount: Long
)

data class FolderStat(
    val path: String,
    val name: String,
    val totalBytes: Long,
    val fileCount: Long
)
