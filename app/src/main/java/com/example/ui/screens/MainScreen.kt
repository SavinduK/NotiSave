package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.ClipboardEntity
import com.example.service.AppNotificationListenerService
import com.example.ui.components.ImageViewerDialog
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val notificationState by viewModel.notificationUiState.collectAsStateWithLifecycle()
    val clipboardState by viewModel.clipboardUiState.collectAsStateWithLifecycle()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var activeImagePreview by remember { mutableStateOf<ClipboardEntity?>(null) }

    // Re-check notification service permission and detect any clipboard changes when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshServicePermission(context)
                viewModel.checkAndSaveClipboardContent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Observe snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search items...") },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_input")
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Notify & Clip",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) {
                                viewModel.setSearchQuery("")
                            }
                        },
                        modifier = Modifier.testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = if (isSearchExpanded) "Close search" else "Search"
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("more_options_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (selectedTab == 0) "Clear all notifications" else "Clear clipboard history"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showClearDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Material 3 Primary TabRow
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                // Notifications Tab
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    modifier = Modifier.testTag("tab_notifications"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Notifications",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                            if (notificationState.totalCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(
                                    containerColor = if (selectedTab == 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    contentColor = if (selectedTab == 0) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                ) {
                                    Text(notificationState.totalCount.toString())
                                }
                            }
                        }
                    }
                )

                // Clipboard Tab
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    modifier = Modifier.testTag("tab_clipboard"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Clipboard",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                            if (clipboardState.totalCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(
                                    containerColor = if (selectedTab == 1) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    contentColor = if (selectedTab == 1) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                ) {
                                    Text(clipboardState.totalCount.toString())
                                }
                            }
                        }
                    }
                )
            }

            // Tab Content with transition
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TabContentAnimation",
                modifier = Modifier.weight(1f)
            ) { targetTab ->
                when (targetTab) {
                    0 -> NotificationTab(
                        notifications = notificationState.notifications,
                        isServiceEnabled = notificationState.isServiceEnabled,
                        onCopy = { viewModel.copyNotification(it) },
                        onDelete = { viewModel.deleteNotification(it) },
                        onOpenSettings = { AppNotificationListenerService.openNotificationAccessSettings(context) },
                        onClearAll = { showClearDialog = true }
                    )
                    1 -> ClipboardTab(
                        clipboardItems = clipboardState.clipboardItems,
                        onCopy = { viewModel.copyClipboardItem(it) },
                        onDelete = { viewModel.deleteClipboardItem(it) },
                        onViewImage = { activeImagePreview = it },
                        onPasteCurrentSystem = { viewModel.pasteCurrentSystemClipboard() }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showClearDialog) {
        val isNotificationTab = selectedTab == 0
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(if (isNotificationTab) "Clear All Notifications?" else "Clear Clipboard History?")
            },
            text = {
                Text(
                    if (isNotificationTab) {
                        "This will remove all saved notifications from local history. This cannot be undone."
                    } else {
                        "This will remove all text snippets and images from your clipboard history. This cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isNotificationTab) {
                            viewModel.clearAllNotifications()
                        } else {
                            viewModel.clearAllClipboard()
                        }
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    activeImagePreview?.let { item ->
        ImageViewerDialog(
            item = item,
            onDismiss = { activeImagePreview = null },
            onCopy = { viewModel.copyClipboardItem(item) }
        )
    }
}
