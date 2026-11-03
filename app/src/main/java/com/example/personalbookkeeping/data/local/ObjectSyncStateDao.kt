package com.example.personalbookkeeping.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ObjectSyncStateDao {
    @Query("SELECT * FROM object_sync_record_states WHERE storageId = :storageId")
    suspend fun states(storageId: String): List<ObjectSyncRecordStateEntity>

    @Query("SELECT * FROM object_sync_conflicts WHERE storageId = :storageId ORDER BY recordId, revisionId")
    suspend fun conflicts(storageId: String): List<ObjectSyncConflictEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStates(states: List<ObjectSyncRecordStateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putConflicts(conflicts: List<ObjectSyncConflictEntity>)

    @Query("DELETE FROM object_sync_record_states WHERE storageId = :storageId")
    suspend fun clearStates(storageId: String)

    @Query("DELETE FROM object_sync_conflicts WHERE storageId = :storageId")
    suspend fun clearConflicts(storageId: String)
}
