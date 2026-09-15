package com.example.service

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.entity.ClipboardEntity
import com.example.data.local.entity.ClipboardType

/**
 * Manages clipboard operations and listens for system clipboard changes.
 */
class ClipboardMonitor(private val context: Context) {

    private val clipboardManager: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private var clipChangedListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastRecordedContent: String? = null

    /**
     * Copies plain text to the system clipboard.
     */
    fun copyText(text: String, label: String = "Copied Text") {
        try {
            val clip = ClipData.newPlainText(label, text)
            lastRecordedContent = text
            clipboardManager?.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy text to clipboard", e)
        }
    }

    /**
     * Copies an image URI or content URI to the system clipboard.
     */
    fun copyImageUri(uri: Uri, label: String = "Copied Image") {
        try {
            val clip = ClipData.newUri(context.contentResolver, label, uri)
            lastRecordedContent = uri.toString()
            clipboardManager?.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to clipboard", e)
        }
    }

    /**
     * Reads current primary clip if available.
     */
    fun readCurrentClip(): ClipboardEntity? {
        val clip = clipboardManager?.primaryClip ?: return null
        if (clip.itemCount == 0) return null

        val item = clip.getItemAt(0)
        val description = clip.description

        // Check if item contains an Image URI
        val uri = item.uri
        if (uri != null) {
            val isImageMime = description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) == true ||
                (description?.mimeTypeCount ?: 0) > 0 &&
                (0 until (description?.mimeTypeCount ?: 0)).any {
                    description?.getMimeType(it)?.startsWith("image/") == true
                }
            if (isImageMime || uri.toString().contains("image", ignoreCase = true)) {
                return ClipboardEntity(
                    content = "Copied Image",
                    type = ClipboardType.IMAGE,
                    imageUri = uri.toString(),
                    timestamp = System.currentTimeMillis()
                )
            }
        }

        // Fallback to text content
        val text = item.text?.toString() ?: item.coerceToText(context)?.toString()
        if (!text.isNullOrBlank()) {
            return ClipboardEntity(
                content = text,
                type = ClipboardType.TEXT,
                timestamp = System.currentTimeMillis()
            )
        }

        return null
    }

    /**
     * Begins listening for primary clip changes. When a new clip is detected, [onNewClip] is invoked.
     */
    fun startListening(onNewClip: (ClipboardEntity) -> Unit) {
        if (clipChangedListener != null) return

        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val clipEntity = readCurrentClip() ?: return@OnPrimaryClipChangedListener
                // Prevent recording immediate duplicates caused by self-copying
                if (clipEntity.content == lastRecordedContent ||
                    (clipEntity.imageUri != null && clipEntity.imageUri == lastRecordedContent)
                ) {
                    return@OnPrimaryClipChangedListener
                }

                lastRecordedContent = clipEntity.imageUri ?: clipEntity.content
                onNewClip(clipEntity)
            } catch (e: Exception) {
                Log.e(TAG, "Error in primary clip listener", e)
            }
        }
        clipboardManager?.addPrimaryClipChangedListener(listener)
        clipChangedListener = listener
    }

    /**
     * Stops listening for primary clip changes.
     */
    fun stopListening() {
        clipChangedListener?.let {
            clipboardManager?.removePrimaryClipChangedListener(it)
            clipChangedListener = null
        }
    }

    companion object {
        private const val TAG = "ClipboardMonitor"
    }
}
