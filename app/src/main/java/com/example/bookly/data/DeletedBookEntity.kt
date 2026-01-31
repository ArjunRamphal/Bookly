package com.example.bookly.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_books")
data class DeletedBookEntity(
    @PrimaryKey val fileName: String
)