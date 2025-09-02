package com.example.whatsappeventer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.*

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WhatsAppAccessibility"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        
        @Volatile
        private var instance: WhatsAppAccessibilityService? = null
        
        fun getInstance(): WhatsAppAccessibilityService? {
            return instance
        }
    }

    data class ChatMessage(
        val sender: String,
        val content: String,
        val timestamp: Long,
        val isFromUser: Boolean
    )

    private var currentChatMessages = mutableListOf<ChatMessage>()
    private var currentChatContext = ""
    private var lastProcessedTime = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "WhatsApp Accessibility Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service Connected - Ready to monitor WhatsApp conversations")
        
        // Configure the service
        val info = AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED
            packageNames = arrayOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val packageName = event.packageName?.toString() ?: return
            
            // Only process WhatsApp events
            if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS_PACKAGE) {
                return
            }

            // Only process relevant event types
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    processWhatsAppScreen(event)
                }
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    // Handle scrolling in chat to capture more messages
                    if (isInChatConversation()) {
                        extractConversationMessages()
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun processWhatsAppScreen(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        
        try {
            // Identify current WhatsApp screen
            when {
                isChatConversationScreen(rootNode) -> {
                    Log.d(TAG, "Detected WhatsApp chat conversation screen")
                    extractConversationMessages(rootNode)
                }
                isChatListScreen(rootNode) -> {
                    Log.d(TAG, "Detected WhatsApp chat list screen")
                    // Clear current conversation when not in chat
                    clearCurrentConversation()
                }
                else -> {
                    Log.d(TAG, "Other WhatsApp screen detected")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing WhatsApp screen: ${e.message}")
            e.printStackTrace()
        } finally {
            rootNode.recycle()
        }
    }

    private fun isChatConversationScreen(rootNode: AccessibilityNodeInfo): Boolean {
        // Look for chat-specific UI elements
        val chatIndicators = listOf(
            "com.whatsapp:id/conversation_contact_name", // Contact name in toolbar
            "com.whatsapp:id/conversation_title",        // Chat title
            "com.whatsapp:id/messages_list",             // Messages list
            "com.whatsapp:id/conversation_layout",       // Main conversation layout
            "com.whatsapp:id/entry"                      // Message input field
        )

        return chatIndicators.any { resourceId ->
            findNodesByViewId(rootNode, resourceId).isNotEmpty()
        }
    }

    private fun isChatListScreen(rootNode: AccessibilityNodeInfo): Boolean {
        // Look for chat list UI elements
        val chatListIndicators = listOf(
            "com.whatsapp:id/conversations_list",
            "com.whatsapp:id/conversations_row_layout",
            "com.whatsapp:id/menuitem_search"
        )

        return chatListIndicators.any { resourceId ->
            findNodesByViewId(rootNode, resourceId).isNotEmpty()
        }
    }

    private fun isInChatConversation(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return try {
            isChatConversationScreen(rootNode)
        } catch (e: Exception) {
            false
        } finally {
            rootNode.recycle()
        }
    }

    private fun extractConversationMessages(rootNode: AccessibilityNodeInfo? = null) {
        val node = rootNode ?: rootInActiveWindow ?: return
        
        try {
            Log.d(TAG, "Starting message extraction from conversation screen")
            
            // First, let's explore the entire UI tree to understand the structure
            exploreUITree(node, 0)
            
            // Find the messages list/recycler view
            val messagesList = findMessagesList(node)
            if (messagesList != null) {
                Log.d(TAG, "Found messages list, extracting messages...")
                val newMessages = extractMessagesFromList(messagesList)
                Log.d(TAG, "Extracted ${newMessages.size} messages")
                updateCurrentConversation(newMessages)
                logCurrentConversation()
            } else {
                Log.w(TAG, "Could not find messages list - trying fallback methods")
                // Try fallback: look for any text content in the screen
                val fallbackMessages = extractAllTextFromScreen(node)
                if (fallbackMessages.isNotEmpty()) {
                    Log.d(TAG, "Found ${fallbackMessages.size} text elements via fallback")
                    updateCurrentConversation(fallbackMessages)
                    logCurrentConversation()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting conversation messages: ${e.message}")
            e.printStackTrace()
        } finally {
            if (rootNode == null) {
                node.recycle()
            }
        }
    }

    private fun exploreUITree(node: AccessibilityNodeInfo, depth: Int) {
        val indent = "  ".repeat(depth)
        val className = node.className?.toString() ?: "null"
        val resourceId = node.viewIdResourceName ?: "no-id"
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        
        Log.d(TAG, "${indent}Node: class=$className, id=$resourceId, text='$text', desc='$contentDesc', children=${node.childCount}")
        
        // Don't go too deep to avoid spam
        if (depth < 4) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        exploreUITree(child, depth + 1)
                    } finally {
                        child.recycle()
                    }
                }
            }
        }
    }

    private fun extractAllTextFromScreen(rootNode: AccessibilityNodeInfo): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val allTexts = mutableListOf<String>()
        
        // Collect all text from the screen
        collectAllText(rootNode, allTexts)
        
        Log.d(TAG, "Fallback: Found ${allTexts.size} text elements - looking for LAST message only")
        
        // Look for the last likely message text (reverse order)
        for (i in allTexts.indices.reversed()) {
            val text = allTexts[i]
            Log.d(TAG, "Checking text[$i]: '$text'")
            
            // Filter out obvious UI elements and keep potential messages
            if (isLikelyMessageText(text)) {
                Log.d(TAG, "Found last message via fallback: '$text'")
                messages.add(ChatMessage(
                    sender = "Unknown",
                    content = text,
                    timestamp = System.currentTimeMillis(),
                    isFromUser = false
                ))
                // Only take the first (most recent) message found
                break
            }
        }
        
        return messages
    }

    private fun collectAllText(node: AccessibilityNodeInfo, textList: MutableList<String>) {
        // Add text from current node
        node.text?.toString()?.let { text ->
            if (text.trim().isNotEmpty()) {
                textList.add(text.trim())
            }
        }
        
        node.contentDescription?.toString()?.let { desc ->
            if (desc.trim().isNotEmpty()) {
                textList.add(desc.trim())
            }
        }
        
        // Recursively collect from children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    collectAllText(child, textList)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    private fun isLikelyMessageText(text: String): Boolean {
        // Filter out common UI elements
        val uiElements = listOf(
            "WhatsApp", "Search", "Call", "Video call", "View contact", "Settings",
            "Mute", "Wallpaper", "More", "Attach", "Camera", "Gallery", "Document",
            "Location", "Contact", "Today", "Yesterday", "Type a message",
            "Emoji", "Voice message", "Send", "Online", "Last seen", "Typing..."
        )
        
        return text.length > 1 && 
               text.length < 500 && 
               !uiElements.any { ui -> text.contains(ui, ignoreCase = true) } &&
               !text.matches(Regex("\\d{1,2}:\\d{2}")) && // Skip timestamps
               !text.matches(Regex("\\d{1,2}/\\d{1,2}/\\d{4}")) // Skip dates
    }

    private fun findMessagesList(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        Log.d(TAG, "Looking for messages list in UI tree...")
        
        // Try different possible resource IDs for the messages list
        val messageListIds = listOf(
            "com.whatsapp:id/messages_list",
            "com.whatsapp:id/conversation_layout", 
            "com.whatsapp:id/conversation_list_view",
            "com.whatsapp:id/messages_recycler_view"
        )

        for (id in messageListIds) {
            Log.d(TAG, "Searching for resource ID: $id")
            val nodes = findNodesByViewId(rootNode, id)
            if (nodes.isNotEmpty()) {
                Log.d(TAG, "Found messages list with ID: $id")
                return nodes[0]
            }
        }

        // Fallback: look for RecyclerView by class name
        Log.d(TAG, "Trying RecyclerView by class name...")
        val recyclerView = findNodeByClassName(rootNode, "androidx.recyclerview.widget.RecyclerView")
        if (recyclerView != null) {
            Log.d(TAG, "Found RecyclerView by class name")
            return recyclerView
        }
        
        // Another fallback: look for ListView
        Log.d(TAG, "Trying ListView by class name...")
        val listView = findNodeByClassName(rootNode, "android.widget.ListView")
        if (listView != null) {
            Log.d(TAG, "Found ListView by class name")
            return listView
        }
        
        Log.w(TAG, "Could not find any messages list container")
        return null
    }

    private fun extractMessagesFromList(messagesList: AccessibilityNodeInfo): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        
        try {
            Log.d(TAG, "Extracting messages - looking for LAST message only")
            // Process child nodes in reverse order to find the most recent message
            // The last message is typically at the bottom of the list (highest index)
            
            for (i in (messagesList.childCount - 1) downTo 0) {
                val messageNode = messagesList.getChild(i) ?: continue
                
                try {
                    val message = extractMessageFromNode(messageNode)
                    if (message != null) {
                        Log.d(TAG, "Found last message: ${message.content}")
                        messages.add(message)
                        // Only take the first (most recent) message found
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error extracting message from node: ${e.message}")
                } finally {
                    messageNode.recycle()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing messages list: ${e.message}")
        }
        
        Log.d(TAG, "Extracted ${messages.size} messages (should be 1 or 0)")
        return messages
    }

    private fun extractMessageFromNode(messageNode: AccessibilityNodeInfo): ChatMessage? {
        try {
            // Extract text content from message node
            val messageText = extractTextFromNode(messageNode)
            if (messageText.isBlank()) return null

            // Determine if message is from user (typically right-aligned) or contact
            val isFromUser = isMessageFromUser(messageNode)
            
            // Extract sender info (for group chats)
            val sender = if (isFromUser) "You" else extractSenderName(messageNode)
            
            return ChatMessage(
                sender = sender,
                content = messageText,
                timestamp = System.currentTimeMillis(),
                isFromUser = isFromUser
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting message: ${e.message}")
            return null
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val textParts = mutableListOf<String>()
        
        // Get direct text content
        node.text?.let { textParts.add(it.toString()) }
        node.contentDescription?.let { textParts.add(it.toString()) }
        
        // Recursively get text from child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    val childText = extractTextFromNode(child)
                    if (childText.isNotBlank()) {
                        textParts.add(childText)
                    }
                } finally {
                    child.recycle()
                }
            }
        }
        
        return textParts.joinToString(" ").trim()
    }

    private fun isMessageFromUser(messageNode: AccessibilityNodeInfo): Boolean {
        // Various heuristics to determine if message is from user
        
        // Check for outgoing message indicators
        val resourceId = messageNode.viewIdResourceName
        if (resourceId != null && resourceId.contains("outgoing")) {
            return true
        }
        
        // Check bounds (user messages typically on right side)
        val bounds = Rect()
        messageNode.getBoundsInScreen(bounds)
        val screenWidth = resources.displayMetrics.widthPixels
        val isRightAligned = bounds.right > screenWidth * 0.6 // Right side of screen
        
        return isRightAligned
    }

    private fun extractSenderName(messageNode: AccessibilityNodeInfo): String {
        // Try to find sender name in group chats
        // This is a simplified implementation
        return "Contact" // Default fallback
    }

    private fun updateCurrentConversation(newMessages: List<ChatMessage>) {
        // Only add genuinely new messages
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime > 1000) { // Throttle updates
            currentChatMessages.clear()
            currentChatMessages.addAll(newMessages)
            lastProcessedTime = currentTime
        }
    }

    private fun logCurrentConversation() {
        if (currentChatMessages.isEmpty()) return
        
        Log.i(TAG, "=== Current WhatsApp Conversation ===")
        Log.i(TAG, "Total messages: ${currentChatMessages.size}")
        
        currentChatMessages.forEach { message ->
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(Date(message.timestamp))
            Log.i(TAG, "[$timestamp] ${message.sender}: ${message.content}")
        }
        
        Log.i(TAG, "====================================")
    }

    private fun clearCurrentConversation() {
        if (currentChatMessages.isNotEmpty()) {
            Log.d(TAG, "Clearing current conversation context")
            currentChatMessages.clear()
            currentChatContext = ""
        }
    }

    fun getCurrentConversationText(): String {
        // Since we now only capture the last message, just return it
        return currentChatMessages.joinToString("\n") { message ->
            "${message.sender}: ${message.content}"
        }
    }

    fun getCurrentMessages(): List<ChatMessage> {
        return currentChatMessages.toList()
    }

    private fun findNodesByViewId(rootNode: AccessibilityNodeInfo, viewId: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        
        fun searchRecursively(node: AccessibilityNodeInfo) {
            if (node.viewIdResourceName == viewId) {
                result.add(node)
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        searchRecursively(child)
                    } finally {
                        // Don't recycle here as we might be adding to result
                    }
                }
            }
        }
        
        searchRecursively(rootNode)
        return result
    }

    private fun findNodeByClassName(rootNode: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        fun searchRecursively(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.className?.toString() == className) {
                return node
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        val result = searchRecursively(child)
                        if (result != null) return result
                    } finally {
                        child.recycle()
                    }
                }
            }
            
            return null
        }
        
        return searchRecursively(rootNode)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "WhatsApp Accessibility Service destroyed")
    }
}