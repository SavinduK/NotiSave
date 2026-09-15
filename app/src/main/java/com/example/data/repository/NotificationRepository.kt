package com.example.data.repository

import com.example.data.local.dao.NotificationDao
import com.example.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstracting notification persistence.
 */
class NotificationRepository(private val notificationDao: NotificationDao) {

    val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()

    suspend fun insert(notification: NotificationEntity): Long =
        notificationDao.insertNotification(notification)

    suspend fun delete(notification: NotificationEntity) =
        notificationDao.deleteNotification(notification)

    suspend fun deleteById(id: Long) =
        notificationDao.deleteNotificationById(id)

    suspend fun clearAll() =
        notificationDao.clearAllNotifications()
}
