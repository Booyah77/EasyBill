package com.example.personalbookkeeping.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LedgerEntryEntity::class, ObjectSyncRecordStateEntity::class, ObjectSyncConflictEntity::class, AiBatchEntryEntity::class],
    version = 3,
    exportSchema = true
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerEntryDao(): LedgerEntryDao
    abstract fun objectSyncStateDao(): ObjectSyncStateDao
    abstract fun aiBatchEntryDao(): AiBatchEntryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `object_sync_record_states` (`storageId` TEXT NOT NULL, `recordId` TEXT NOT NULL, `revisionId` TEXT NOT NULL, PRIMARY KEY(`storageId`, `recordId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `object_sync_conflicts` (`storageId` TEXT NOT NULL, `recordId` TEXT NOT NULL, `revisionId` TEXT NOT NULL, `revisionJson` TEXT NOT NULL, PRIMARY KEY(`storageId`, `recordId`, `revisionId`))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ai_batch_entries` (`entryId` TEXT NOT NULL, `batchId` TEXT NOT NULL, `batchSavedAt` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`entryId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_batch_entries_batchId` ON `ai_batch_entries` (`batchId`)")
            }
        }

        @Volatile
        private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LedgerDatabase::class.java,
                "personal-ledger.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
