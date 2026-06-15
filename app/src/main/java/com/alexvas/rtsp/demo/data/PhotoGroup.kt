package com.alexvas.rtsp.demo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_groups")
data class PhotoGroup(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val groupName: String,
    val dateTime: String
)
