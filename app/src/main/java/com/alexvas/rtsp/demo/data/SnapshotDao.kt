package com.alexvas.rtsp.demo.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots ORDER BY dateTime DESC")
    fun getAllSnapshots(): LiveData<List<SnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: SnapshotEntity)

    @Delete
    suspend fun delete(snapshot: SnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: PhotoGroup): Long

    @Delete
    suspend fun deleteGroup(group: PhotoGroup)

    @Query("SELECT * FROM snapshots WHERE groupId = :groupId")
    fun getSnapshotsByGroup(groupId: Long): LiveData<List<SnapshotEntity>>

    @Query("SELECT * FROM photo_groups")
    fun getAllGroups(): LiveData<List<PhotoGroup>>
}
