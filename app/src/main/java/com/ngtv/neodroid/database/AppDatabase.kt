package com.ngtv.neodroid.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SSHHostEntity::class, AuthKeyEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun SSHHostDao(): SSHHostDao

    abstract fun AuthKeyDao(): AuthKeyDao

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

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `auth_keys` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`algorithm` TEXT NOT NULL, " +
                            "`display_name` TEXT NOT NULL, " +
                            "`encrypted_private_key` TEXT NOT NULL, " +
                            "`public_key` TEXT NOT NULL, " +
                            "`encrypted_dek` TEXT NOT NULL, " +
                            "`created_at` INTEGER NOT NULL, " +
                            "`last_used_at` INTEGER" +
                            ")",
                    )
                    db.execSQL(
                        "ALTER TABLE `ssh_hosts` ADD COLUMN `auth_key_id` INTEGER " +
                            "REFERENCES `auth_keys`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_ssh_hosts_auth_key_id` " +
                            "ON `ssh_hosts` (`auth_key_id`)",
                    )
                }
            }

        fun getInstance(context: Context): AppDatabase = databaseInstance ?: synchronized(this) {
            databaseInstance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_database",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also {
                databaseInstance = it
            }
        }
    }
}
