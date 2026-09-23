package com.example.data.repository

import com.example.data.local.dao.NotificationDao
import com.example.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstracting notification persistence.
 */
class NotificationRepository(private val notificationDao: NotificationDao) {

    val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()

    suspend fun getLatestNotification(): NotificationEntity? =
        notificationDao.getLatestNotification()

    /**
     * Saves the notification only if it is different from the last saved notification.
     * If the current notification is identical to the last saved notification, it will not be saved.
     * Returns true if saved, false if skipped as duplicate.
     */
    suspend fun insertIfDifferent(notification: NotificationEntity): Boolean {
        val lastSaved = notificationDao.getLatestNotification()
        if (lastSaved != null && isIdentical(lastSaved, notification)) {
            return false
        }
        notificationDao.insertNotification(notification)
        return true
    }

    suspend fun insert(notification: NotificationEntity): Long =
        notificationDao.insertNotification(notification)

    suspend fun delete(notification: NotificationEntity) =
        notificationDao.deleteNotification(notification)

    suspend fun deleteById(id: Long) =
        notificationDao.deleteNotificationById(id)

    suspend fun clearAll() =
        notificationDao.clearAllNotifications()

    companion object {
        /**
         * Checks whether two notifications are identical in app identity, title, and body.
         */
        fun isIdentical(a: NotificationEntity, b: NotificationEntity): Boolean {
            val sameApp = a.packageName.equals(b.packageName, ignoreCase = true) ||
                a.appName.equals(b.appName, ignoreCase = true)
            val sameTitle = a.title.trim() == b.title.trim()
            val sameBody = a.body.trim() == b.body.trim()
            return sameApp && sameTitle && sameBody
        }
    }
}
