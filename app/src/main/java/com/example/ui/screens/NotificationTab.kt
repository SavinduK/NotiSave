package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.NotificationEntity
import com.example.ui.util.DateTimeUtils

@Composable
fun NotificationTab(
    notifications: List<NotificationEntity>,
    isServiceEnabled: Boolean,
    onCopy: (NotificationEntity) -> Unit,
    onDelete: (NotificationEntity) -> Unit,
    onOpenSettings: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Group notifications by app name, preserving sorted order (alphabetical by app, newest first)
    val groupedNotifications = remember(notifications) {
        notifications.groupBy { it.appName }
    }

    // Track collapsed app sections by app name
    var collapsedApps by rememberSaveable { mutableStateOf(setOf<String>()) }

    Column(modifier = modifier.fillMaxSize()) {
        // Notification Access Status Banner
        if (!isServiceEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Access Disabled",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Enable listener access to automatically record device notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("enable_access_button")
                    ) {
                        Text("Enable", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Notification List or Empty State
        if (notifications.isEmpty()) {
            NotificationEmptyState(
                modifier = Modifier.weight(1f)
            )
        } else {
            // Category header summary bar with toggle all categories
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${groupedNotifications.size} ${if (groupedNotifications.size == 1) "Category" else "Categories"} • ${notifications.size} ${if (notifications.size == 1) "item" else "items"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val allCollapsed = groupedNotifications.keys.isNotEmpty() && groupedNotifications.keys.all { it in collapsedApps }
                TextButton(
                    onClick = {
                        collapsedApps = if (allCollapsed) {
                            emptySet()
                        } else {
                            groupedNotifications.keys.toSet()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("toggle_all_categories_button")
                ) {
                    Text(
                        text = if (allCollapsed) "Expand all" else "Collapse all",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedNotifications.forEach { (appName, appNotifications) ->
                    val isCollapsed = appName in collapsedApps

                    // App Section Header (Clickable to collapse/expand)
                    item(key = "header_$appName") {
                        val appColor = getAppColor(appName)
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (isCollapsed) -90f else 0f,
                            label = "chevron_rotation_$appName"
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    collapsedApps = if (isCollapsed) {
                                        collapsedApps - appName
                                    } else {
                                        collapsedApps + appName
                                    }
                                }
                                .testTag("app_section_header_$appName"),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(appColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = appName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = appColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCollapsed) appColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = if (isCollapsed) "${appNotifications.size} collapsed" else "${appNotifications.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCollapsed) appColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isCollapsed) "Expand $appName notifications" else "Collapse $appName notifications",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(chevronRotation)
                                )
                            }
                        }
                    }

                    // Notification items for this app (only shown when not collapsed)
                    if (!isCollapsed) {
                        items(
                            items = appNotifications,
                            key = { it.id }
                        ) { notification ->
                            NotificationItemCard(
                                notification = notification,
                                onCopy = { onCopy(notification) },
                                onDelete = { onDelete(notification) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItemCard(
    notification: NotificationEntity,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("notification_card_${notification.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: App Icon badge, App Name, and Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val appColor = getAppColor(notification.appName)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(appColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = notification.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = appColor
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.appName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = DateTimeUtils.formatTimestamp(notification.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Notification Title
            if (notification.title.isNotBlank()) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Notification Body Text
            Text(
                text = notification.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Copy and Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("copy_notification_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_notification_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete notification",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationEmptyState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Notifications Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Incoming app notifications will appear here automatically when received.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Returns a distinct color token for known applications or defaults based on name hash.
 */
private fun getAppColor(appName: String): Color {
    return when (appName.lowercase()) {
        "slack" -> Color(0xFF4A154B)
        "gmail" -> Color(0xFFEA4335)
        "whatsapp" -> Color(0xFF25D366)
        "calendar" -> Color(0xFF4285F4)
        "github" -> Color(0xFF24292E)
        "twitter", "x" -> Color(0xFF1DA1F2)
        "telegram" -> Color(0xFF0088CC)
        "instagram" -> Color(0xFFE1306C)
        else -> {
            val palette = listOf(
                Color(0xFF2563EB),
                Color(0xFF7C3AED),
                Color(0xFF059669),
                Color(0xFFD97706),
                Color(0xFFDC2626)
            )
            palette[kotlin.math.abs(appName.hashCode()) % palette.size]
        }
    }
}
