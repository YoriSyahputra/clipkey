package com.example.clipkey.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
        tableName = "clips",
        indices =
                [
                        Index(value = ["isPinned"]),
                        Index(value = ["category"]),
                        Index(value = ["createdAt"])]
)
data class ClipEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val content: String,
        val category: String = "Umum",
        val isPinned: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
)
