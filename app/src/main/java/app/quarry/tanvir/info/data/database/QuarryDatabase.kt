package app.quarry.tanvir.info.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        FileEntity::class,
        ScanSnapshotEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class QuarryDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun scanSnapshotDao(): ScanSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: QuarryDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE files ADD COLUMN scanStatus TEXT NOT NULL DEFAULT 'PARTIAL'")
                db.execSQL("UPDATE files SET scanStatus = 'COMPLETE' WHERE isDirectory = 0")
                db.execSQL("ALTER TABLE scan_snapshots ADD COLUMN unreadableDirectories INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_snapshots ADD COLUMN excludedEntries INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): QuarryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return@synchronized it }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuarryDatabase::class.java,
                    File(context.noBackupFilesDir, "quarry_storage.db").absolutePath
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
