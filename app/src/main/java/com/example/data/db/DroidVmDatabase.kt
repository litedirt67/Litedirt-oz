package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VmImageEntity::class,
        VaultItemEntity::class,
        WirelessProfileEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DroidVmDatabase : RoomDatabase() {
    abstract fun droidVmDao(): DroidVmDao

    companion object {
        @Volatile
        private var INSTANCE: DroidVmDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vm_images ADD COLUMN bootBrand TEXT NOT NULL DEFAULT 'LiteDirt'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE vm_images SET bootBrand = 'LiteDirt' WHERE isCustomBuilt = 0 AND bootBrand = 'DroidVM'")
            }
        }

        fun getInstance(context: Context): DroidVmDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DroidVmDatabase::class.java,
                    "droidvm.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
