package com.example.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.NotifyClipApplication
import com.example.data.local.entity.NotificationEntity
import com.example.data.repository.AppFilterManager
import com.example.data.repository.NotificationRepository
import com.example.data.repository.SelectableApp
import com.example.service.AppNotificationListenerService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI State for the Notification History screen.
 */
data class NotificationUiState(
    val notifications: List<NotificationEntity> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val isServiceEnabled: Boolean = false
)

/**
 * MainViewModel managing data state and actions for Notification history.
 */
class MainViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _isFilterEnabled = MutableStateFlow(false)
    val isFilterEnabled: StateFlow<Boolean> = _isFilterEnabled.asStateFlow()

    private val _selectableApps = MutableStateFlow<List<SelectableApp>>(emptyList())
    val selectableApps: StateFlow<List<SelectableApp>> = _selectableApps.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshServicePermission(context: Context) {
        _isServiceEnabled.value = AppNotificationListenerService.isNotificationAccessGranted(context)
    }

    fun loadAppFilters(context: Context) {
        _isFilterEnabled.value = AppFilterManager.isFilterEnabled(context)
        val recordedPackages = notificationUiState.value.notifications.map { it.packageName }.toSet()
        _selectableApps.value = AppFilterManager.getSelectableApps(context, recordedPackages)
    }

    fun setFilterEnabled(context: Context, enabled: Boolean) {
        AppFilterManager.setFilterEnabled(context, enabled)
        _isFilterEnabled.value = enabled
        viewModelScope.launch {
            if (enabled) {
                val selectedCount = _selectableApps.value.count { it.isSelected }
                _snackbarMessage.emit("App filtering enabled: only saving $selectedCount selected apps")
            } else {
                _snackbarMessage.emit("Saving notifications from all apps")
            }
        }
    }

    fun toggleAppSelection(context: Context, packageName: String, selected: Boolean) {
        AppFilterManager.togglePackageSelection(context, packageName, selected)
        _selectableApps.value = _selectableApps.value.map {
            if (it.packageName == packageName) it.copy(isSelected = selected) else it
        }
    }

    fun selectAllApps(context: Context) {
        val allPkgs = _selectableApps.value.map { it.packageName }.toSet()
        AppFilterManager.setSelectedPackages(context, allPkgs)
        _selectableApps.value = _selectableApps.value.map { it.copy(isSelected = true) }
    }

    fun deselectAllApps(context: Context) {
        AppFilterManager.setSelectedPackages(context, emptySet())
        _selectableApps.value = _selectableApps.value.map { it.copy(isSelected = false) }
    }

    // Reactive notifications stream filtered by search query
    val notificationUiState: StateFlow<NotificationUiState> = combine(
        notificationRepository.allNotifications,
        _searchQuery,
        _isServiceEnabled
    ) { notifications, query, isEnabled ->
        val filtered = if (query.isBlank()) {
            notifications
        } else {
            notifications.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.body.contains(query, ignoreCase = true)
            }
        }
        // Sort notifications alphabetically by App name (case-insensitive), then newest first within each app
        val sortedByApp = filtered.sortedWith(
            compareBy<NotificationEntity> { it.appName.lowercase() }
                .thenByDescending { it.timestamp }
        )
        NotificationUiState(
            notifications = sortedByApp,
            totalCount = notifications.size,
            isLoading = false,
            isServiceEnabled = isEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationUiState(isLoading = true)
    )

    // --- Notification Actions ---

    fun copyNotification(context: Context, notification: NotificationEntity) {
        val textToCopy = if (notification.title.isNotBlank()) {
            "${notification.title}: ${notification.body}"
        } else {
            notification.body
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(notification.appName, textToCopy)
        clipboard?.setPrimaryClip(clip)
        viewModelScope.launch {
            _snackbarMessage.emit("Copied notification text to clipboard")
        }
    }

    fun deleteNotification(notification: NotificationEntity) {
        viewModelScope.launch {
            notificationRepository.delete(notification)
            _snackbarMessage.emit("Notification removed")
        }
    }

    /**
     * Batch clears all notifications belonging to a single application.
     */
    fun clearNotificationsForApp(appName: String) {
        viewModelScope.launch {
            val deletedCount = notificationRepository.deleteByAppName(appName)
            _snackbarMessage.emit("Cleared all $deletedCount notifications from $appName")
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            notificationRepository.clearAll()
            _snackbarMessage.emit("All notifications cleared")
        }
    }

    fun simulateIncomingNotification(context: Context, appName: String, title: String, body: String) {
        viewModelScope.launch {
            val packageName = "com.sample.${appName.lowercase().replace(" ", "")}"
            if (!AppFilterManager.isAppAllowed(context, packageName)) {
                _snackbarMessage.emit("Skipped: $appName is not selected in your App Filters")
                return@launch
            }
            val entity = NotificationEntity(
                appName = appName,
                packageName = packageName,
                title = title,
                body = body,
                timestamp = System.currentTimeMillis()
            )
            val saved = notificationRepository.insertIfDifferent(entity)
            if (saved) {
                _snackbarMessage.emit("Added notification from $appName")
            } else {
                _snackbarMessage.emit("Skipped: Identical to the last saved notification")
            }
        }
    }

    companion object {
        fun provideFactory(application: NotifyClipApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(application.notificationRepository) as T
                }
            }
    }
}
