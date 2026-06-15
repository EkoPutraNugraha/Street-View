package com.alexvas.rtsp.demo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val path: String,
    val coordinates: String,
    val address: String,
    val dateTime: String,
    val latitude: Double,
    val longitude: Double,
    val groupId: Long
)
