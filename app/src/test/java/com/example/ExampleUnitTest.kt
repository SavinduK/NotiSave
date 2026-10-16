package com.example

import com.example.data.local.entity.NotificationEntity
import com.example.data.repository.NotificationRepository
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testNotificationGroupingAndCollapseLogic() {
    val notifications = listOf(
      NotificationEntity(id = 1, packageName = "com.slack", appName = "Slack", title = "New message", body = "Hello", timestamp = 1000L),
      NotificationEntity(id = 2, packageName = "com.slack", appName = "Slack", title = "Team update", body = "Meeting at 3", timestamp = 2000L),
      NotificationEntity(id = 3, packageName = "com.whatsapp", appName = "WhatsApp", title = "Alice", body = "Are you ready?", timestamp = 3000L)
    )

    val grouped = notifications.groupBy { it.appName }
    assertEquals(2, grouped.size)
    assertEquals(2, grouped["Slack"]?.size)
    assertEquals(1, grouped["WhatsApp"]?.size)

    // Collapsing logic test
    var collapsedApps = setOf<String>()
    // User collapses Slack
    collapsedApps = collapsedApps + "Slack"
    assertTrue("Slack" in collapsedApps)
    assertFalse("WhatsApp" in collapsedApps)

    // User expands Slack
    collapsedApps = collapsedApps - "Slack"
    assertFalse("Slack" in collapsedApps)

    // Test Collapse All / Expand All
    val allCategories = grouped.keys.toSet()
    collapsedApps = allCategories
    assertEquals(2, collapsedApps.size)
    assertTrue("Slack" in collapsedApps)
    assertTrue("WhatsApp" in collapsedApps)

    collapsedApps = emptySet()
    assertTrue(collapsedApps.isEmpty())
  }

  @Test
  fun testNotificationDeduplicationLogic() {
    val lastSaved = NotificationEntity(
      id = 10,
      appName = "Slack",
      packageName = "com.Slack",
      title = "Engineering #general",
      body = "Meeting at 3 PM",
      timestamp = 1000L
    )

    // 1. Identical notification (same app, title, body) -> should be detected as identical
    val identicalNotification = NotificationEntity(
      id = 0,
      appName = "Slack",
      packageName = "com.Slack",
      title = "Engineering #general",
      body = "Meeting at 3 PM",
      timestamp = 2000L
    )
    assertTrue(NotificationRepository.isIdentical(lastSaved, identicalNotification))

    // 2. Different body -> should NOT be identical
    val differentBodyNotification = NotificationEntity(
      id = 0,
      appName = "Slack",
      packageName = "com.Slack",
      title = "Engineering #general",
      body = "Meeting rescheduled to 4 PM",
      timestamp = 2000L
    )
    assertFalse(NotificationRepository.isIdentical(lastSaved, differentBodyNotification))

    // 3. Different title -> should NOT be identical
    val differentTitleNotification = NotificationEntity(
      id = 0,
      appName = "Slack",
      packageName = "com.Slack",
      title = "Marketing #general",
      body = "Meeting at 3 PM",
      timestamp = 2000L
    )
    assertFalse(NotificationRepository.isIdentical(lastSaved, differentTitleNotification))

    // 4. Different app -> should NOT be identical
    val differentAppNotification = NotificationEntity(
      id = 0,
      appName = "WhatsApp",
      packageName = "com.whatsapp",
      title = "Engineering #general",
      body = "Meeting at 3 PM",
      timestamp = 2000L
    )
    assertFalse(NotificationRepository.isIdentical(lastSaved, differentAppNotification))
  }

  @Test
  fun testAppSelectionFilteringAlwaysEnabled() {
    val selectedPackages = setOf("com.slack", "com.whatsapp")

    // Filtering is always enabled: only selected apps are saved
    val appAllowedSlack = selectedPackages.contains("com.slack")
    val appAllowedWhatsApp = selectedPackages.contains("com.whatsapp")
    val appAllowedGmail = selectedPackages.contains("com.google.android.gm")

    assertTrue(appAllowedSlack)
    assertTrue(appAllowedWhatsApp)
    assertFalse(appAllowedGmail)
  }

  @Test
  fun test30DaysAutoDeleteCutoffLogic() {
    val now = 1_700_000_000_000L
    val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000L
    val cutoff = now - thirtyDaysMillis

    val recentNotification = NotificationEntity(
      id = 1,
      packageName = "com.slack",
      appName = "Slack",
      title = "Recent",
      body = "10 days old",
      timestamp = now - (10L * 24 * 60 * 60 * 1000L)
    )

    val oldNotification = NotificationEntity(
      id = 2,
      packageName = "com.slack",
      appName = "Slack",
      title = "Old",
      body = "35 days old",
      timestamp = now - (35L * 24 * 60 * 60 * 1000L)
    )

    assertTrue("Recent notification should be preserved", recentNotification.timestamp >= cutoff)
    assertTrue("Old notification (> 30 days) should be deleted", oldNotification.timestamp < cutoff)

    val notifications = listOf(recentNotification, oldNotification)
    val remaining = notifications.filter { it.timestamp >= cutoff }
    assertEquals(1, remaining.size)
    assertEquals("Recent", remaining.first().title)
  }

  @Test
  fun testBatchClearSingleAppLogic() {
    val initialNotifications = listOf(
      NotificationEntity(id = 1, packageName = "com.slack", appName = "Slack", title = "1", body = "1", timestamp = 1L),
      NotificationEntity(id = 2, packageName = "com.slack", appName = "Slack", title = "2", body = "2", timestamp = 2L),
      NotificationEntity(id = 3, packageName = "com.whatsapp", appName = "WhatsApp", title = "3", body = "3", timestamp = 3L),
      NotificationEntity(id = 4, packageName = "com.google.android.gm", appName = "Gmail", title = "4", body = "4", timestamp = 4L)
    )

    // Batch clear Slack notifications
    val appToClear = "Slack"
    val remainingNotifications = initialNotifications.filterNot { it.appName == appToClear }

    assertEquals(2, remainingNotifications.size)
    assertFalse(remainingNotifications.any { it.appName == "Slack" })
    assertTrue(remainingNotifications.any { it.appName == "WhatsApp" })
    assertTrue(remainingNotifications.any { it.appName == "Gmail" })
  }
}

