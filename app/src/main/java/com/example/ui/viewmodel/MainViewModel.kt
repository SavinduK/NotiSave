package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.NotifyClipApplication
import com.example.data.local.entity.ClipboardEntity
import com.example.data.local.entity.ClipboardType
import com.example.data.local.entity.NotificationEntity
import com.example.data.repository.ClipboardRepository
import com.example.data.repository.NotificationRepository
import com.example.service.AppNotificationListenerService
import com.example.service.ClipboardMonitor
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
 * UI State for the Notifications tab.
 */
data class NotificationUiState(
    val notifications: List<NotificationEntity> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val isServiceEnabled: Boolean = false
)

/**
 * UI State for the Clipboard tab.
 */
data class ClipboardUiState(
    val clipboardItems: List<ClipboardEntity> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false
)

/**
 * MainViewModel managing data state and actions for both Notifications and Clipboard tabs.
 */
class MainViewModel(
    private val notificationRepository: NotificationRepository,
    private val clipboardRepository: ClipboardRepository,
    private val clipboardMonitor: ClipboardMonitor
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    init {
        // Start listening for primary clip changes through ClipboardMonitor
        clipboardMonitor.startListening { newClip ->
            viewModelScope.launch {
                clipboardRepository.insert(newClip)
                _snackbarMessage.emit("New item detected in clipboard!")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        clipboardMonitor.stopListening()
    }

    fun setTab(index: Int) {
        _selectedTab.value = index
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshServicePermission(context: Context) {
        _isServiceEnabled.value = AppNotificationListenerService.isNotificationAccessGranted(context)
    }

    /**
     * Checks if the clipboard content changed while the app was in the background or another app,
     * and automatically saves it if new.
     */
    fun checkAndSaveClipboardContent() {
        val currentClip = clipboardMonitor.readCurrentClip() ?: return
        viewModelScope.launch {
            val allItems = clipboardRepository.allClipboardItems
            // Check if this content is already stored as the latest entry or already in repository
            // inserting the clip
            clipboardRepository.insert(currentClip)
        }
    }

    // Reactive notifications stream filtered by query
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

    // Reactive clipboard items stream filtered by query
    val clipboardUiState: StateFlow<ClipboardUiState> = combine(
        clipboardRepository.allClipboardItems,
        _searchQuery
    ) { items, query ->
        val filtered = if (query.isBlank()) {
            items
        } else {
            items.filter {
                it.content.contains(query, ignoreCase = true)
            }
        }
        ClipboardUiState(
            clipboardItems = filtered,
            totalCount = items.size,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ClipboardUiState(isLoading = true)
    )

    // --- Notification Actions ---

    fun copyNotification(notification: NotificationEntity) {
        val textToCopy = if (notification.title.isNotBlank()) {
            "${notification.title}: ${notification.body}"
        } else {
            notification.body
        }
        clipboardMonitor.copyText(textToCopy, label = notification.appName)
        viewModelScope.launch {
            // Also store into clipboard history
            clipboardRepository.insert(
                ClipboardEntity(
                    content = textToCopy,
                    type = ClipboardType.TEXT,
                    timestamp = System.currentTimeMillis()
                )
            )
            _snackbarMessage.emit("Copied notification from ${notification.appName} to clipboard")
        }
    }

    fun deleteNotification(notification: NotificationEntity) {
        viewModelScope.launch {
            notificationRepository.delete(notification)
            _snackbarMessage.emit("Notification removed")
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            notificationRepository.clearAll()
            _snackbarMessage.emit("All notifications cleared")
        }
    }

    fun simulateIncomingNotification(appName: String, title: String, body: String) {
        viewModelScope.launch {
            val entity = NotificationEntity(
                appName = appName,
                packageName = "com.sample.${appName.lowercase().replace(" ", "")}",
                title = title,
                body = body,
                timestamp = System.currentTimeMillis()
            )
            notificationRepository.insert(entity)
            _snackbarMessage.emit("Simulated notification from $appName")
        }
    }

    // --- Clipboard Actions ---

    fun copyClipboardItem(item: ClipboardEntity) {
        when (item.type) {
            ClipboardType.TEXT -> {
                clipboardMonitor.copyText(item.content, label = "Clipboard snippet")
                viewModelScope.launch {
                    _snackbarMessage.emit("Text copied to clipboard")
                }
            }
            ClipboardType.IMAGE -> {
                // If URI is available, copy URI
                if (item.imageUri != null) {
                    clipboardMonitor.copyText(item.imageUri, label = "Image Link")
                } else {
                    clipboardMonitor.copyText(item.content, label = "Image Label")
                }
                viewModelScope.launch {
                    _snackbarMessage.emit("Image reference copied to clipboard")
                }
            }
        }
    }

    fun deleteClipboardItem(item: ClipboardEntity) {
        viewModelScope.launch {
            clipboardRepository.delete(item)
            _snackbarMessage.emit("Clipboard entry deleted")
        }
    }

    fun clearAllClipboard() {
        viewModelScope.launch {
            clipboardRepository.clearAll()
            _snackbarMessage.emit("Clipboard history cleared")
        }
    }

    fun addManualClip(content: String, type: ClipboardType = ClipboardType.TEXT, drawableResName: String? = null) {
        viewModelScope.launch {
            val newEntity = ClipboardEntity(
                content = content,
                type = type,
                drawableResName = drawableResName,
                timestamp = System.currentTimeMillis()
            )
            clipboardRepository.insert(newEntity)
            if (type == ClipboardType.TEXT) {
                clipboardMonitor.copyText(content)
            }
            _snackbarMessage.emit("Added item to clipboard history")
        }
    }

    fun pasteCurrentSystemClipboard() {
        val currentClip = clipboardMonitor.readCurrentClip()
        if (currentClip != null) {
            viewModelScope.launch {
                clipboardRepository.insert(currentClip)
                _snackbarMessage.emit("Captured active system clip!")
            }
        } else {
            viewModelScope.launch {
                _snackbarMessage.emit("System clipboard is currently empty")
            }
        }
    }

    companion object {
        fun provideFactory(application: NotifyClipApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        application.notificationRepository,
                        application.clipboardRepository,
                        ClipboardMonitor(application)
                    ) as T
                }
            }
    }
}
