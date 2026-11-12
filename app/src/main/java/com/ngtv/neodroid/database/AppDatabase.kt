package com.ngtv.neodroid.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SSHHostEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun SSHHostDao(): SSHHostDao

    companion object {
        @Volatile
        private var databaseInstance: AppDatabase? = null

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE ssh_hosts ADD COLUMN last_connected_at INTEGER",
                    )
                }
            }

        fun getInstance(context: Context): AppDatabase = databaseInstance ?: synchronized(this) {
            databaseInstance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_database",
            ).addMigrations(MIGRATION_1_2).build().also {
                databaseInstance = it
            }
        }
    }
}
