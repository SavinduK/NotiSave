package com.example.data.repository

import com.example.data.local.dao.ClipboardDao
import com.example.data.local.entity.ClipboardEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstracting clipboard item persistence.
 */
class ClipboardRepository(private val clipboardDao: ClipboardDao) {

    val allClipboardItems: Flow<List<ClipboardEntity>> = clipboardDao.getAllClipboardItems()

    suspend fun insert(item: ClipboardEntity): Long =
        clipboardDao.insertClipboardItem(item)

    suspend fun delete(item: ClipboardEntity) =
        clipboardDao.deleteClipboardItem(item)

    suspend fun deleteById(id: Long) =
        clipboardDao.deleteClipboardItemById(id)

    suspend fun clearAll() =
        clipboardDao.clearAllClipboardItems()
}
