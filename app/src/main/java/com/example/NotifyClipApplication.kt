package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ClipboardEntity
import com.example.data.local.entity.ClipboardType
import com.example.data.local.entity.NotificationEntity
import com.example.data.repository.ClipboardRepository
import com.example.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application class providing application-level singletons and seeding sample data on initial launch.
 */
class NotifyClipApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getDatabase(this) }
    val notificationRepository by lazy { NotificationRepository(database.notificationDao()) }
    val clipboardRepository by lazy { ClipboardRepository(database.clipboardDao()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        seedInitialDataIfNeeded()
    }

    private fun seedInitialDataIfNeeded() {
        applicationScope.launch {
            val existingNotifications = notificationRepository.allNotifications.first()
            if (existingNotifications.isEmpty()) {
                val now = System.currentTimeMillis()
                val initialNotifications = listOf(
                    NotificationEntity(
                        appName = "Slack",
                        packageName = "com.Slack",
                        title = "Engineering #general",
                        body = "Sprint planning is scheduled for 2:30 PM today. Please update your tickets before the sync.",
                        timestamp = now - 1000 * 60 * 12
                    ),
                    NotificationEntity(
                        appName = "Gmail",
                        packageName = "com.google.android.gm",
                        title = "DevOps Weekly Digest",
                        body = "Deployment successful for version 2.4.0. All cluster health checks passed with zero downtime.",
                        timestamp = now - 1000 * 60 * 45
                    ),
                    NotificationEntity(
                        appName = "Calendar",
                        packageName = "com.google.android.calendar",
                        title = "Design System Review",
                        body = "In 15 minutes: Room 4B & Google Meet video call with Product & Design teams.",
                        timestamp = now - 1000 * 60 * 120
                    ),
                    NotificationEntity(
                        appName = "WhatsApp",
                        packageName = "com.whatsapp",
                        title = "Alex Turner",
                        body = "Hey! Here's the verification code you requested: 849-210. Valid for 10 minutes.",
                        timestamp = now - 1000 * 60 * 240
                    ),
                    NotificationEntity(
                        appName = "GitHub",
                        packageName = "com.github.android",
                        title = "Pull Request #142 Merged",
                        body = "feature/clipboard-image-thumbnails has been merged into main by team-lead.",
                        timestamp = now - 1000 * 60 * 360
                    )
                )
                initialNotifications.forEach { notificationRepository.insert(it) }
            }

            val existingClipboard = clipboardRepository.allClipboardItems.first()
            if (existingClipboard.isEmpty()) {
                val now = System.currentTimeMillis()
                val initialClips = listOf(
                    ClipboardEntity(
                        content = "git checkout -b feature/clipboard-history-sync origin/main",
                        type = ClipboardType.TEXT,
                        timestamp = now - 1000 * 60 * 15
                    ),
                    ClipboardEntity(
                        content = "Q3 Performance & Analytics Chart Snapshot",
                        type = ClipboardType.IMAGE,
                        drawableResName = "sample_chart",
                        timestamp = now - 1000 * 60 * 50
                    ),
                    ClipboardEntity(
                        content = "https://developer.android.com/jetpack/compose/designsystems/material3",
                        type = ClipboardType.TEXT,
                        timestamp = now - 1000 * 60 * 110
                    ),
                    ClipboardEntity(
                        content = "Office Supplies & Equipment Receipt Invoice",
                        type = ClipboardType.IMAGE,
                        drawableResName = "sample_memo",
                        timestamp = now - 1000 * 60 * 200
                    ),
                    ClipboardEntity(
                        content = "Meeting agenda:\n1. Metrics review\n2. User retention insights\n3. Q4 Roadmap targets",
                        type = ClipboardType.TEXT,
                        timestamp = now - 1000 * 60 * 320
                    )
                )
                initialClips.forEach { clipboardRepository.insert(it) }
            }
        }
    }

    companion object {
        lateinit var instance: NotifyClipApplication
            private set
    }
}
