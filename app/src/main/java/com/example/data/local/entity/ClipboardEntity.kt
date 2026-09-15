package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Type of item stored in the clipboard history.
 */
enum class ClipboardType {
    TEXT,
    IMAGE
}

/**
 * Represents a saved clipboard item, supporting text snippets and image thumbnails.
 */
@Entity(tableName = "clipboard_items")
data class ClipboardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val type: ClipboardType = ClipboardType.TEXT,
    val imageUri: String? = null,
    val drawableResName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
