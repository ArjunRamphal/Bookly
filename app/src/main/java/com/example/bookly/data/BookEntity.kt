package com.example.bookly.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val filePath: String,
    val coverImage: String?, // Path to the cached cover image
    val format: String,      // "pdf" or "epub"
    val progress: Float = 0f,   // Percentage scroll position
    val dateAdded: Long = System.currentTimeMillis()
)