package com.example.whatsappeventer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.util.Log
import androidx.appcompat.app.AlertDialog
import android.widget.TextView
import android.widget.Button
import java.util.*

class OverlayService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var handler: Handler
    
    private var isOverlayVisible = false
    private var isButtonLoading = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val CHECK_INTERVAL = 1000L // Check every second
    }
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        // Start monitoring WhatsApp usage
        startWhatsAppMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service for WhatsApp overlay button"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WhatsApp Eventer")
            .setContentText("Overlay service is running")
            .setSmallIcon(R.drawable.ic_whatsapp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startWhatsAppMonitoring() {
        // Start continuous monitoring loop
        handler.post(object : Runnable {
            override fun run() {
                try {
                    // Check if WhatsApp is in foreground
                    val isWhatsAppActive = isWhatsAppInForeground()
                    
                    if (isWhatsAppActive) {
                        if (!isOverlayVisible) {
                            android.util.Log.d("OverlayService", "WhatsApp detected - showing overlay")
                            showOverlay()
                        } else {
                            android.util.Log.d("OverlayService", "WhatsApp still active - overlay already visible")
                        }
                    } else {
                        if (isOverlayVisible) {
                            android.util.Log.d("OverlayService", "WhatsApp not active - hiding overlay")
                            hideOverlay()
                        } else {
                            android.util.Log.d("OverlayService", "WhatsApp not active - overlay already hidden")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("OverlayService", "Error in monitoring loop: ${e.message}")
                    e.printStackTrace()
                }
                
                // Continue monitoring - this creates an infinite loop
                // that only stops when the service is destroyed
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        })
        
        // Log that monitoring has started
        android.util.Log.d("OverlayService", "WhatsApp monitoring started - checking every ${CHECK_INTERVAL}ms")
    }
    
    private fun isWhatsAppInForeground(): Boolean {
        try {
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 30000 // Check last 30 seconds for much better reliability
            
            // Use queryUsageStats with multiple time windows for better detection
            val usageStats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                beginTime,
                endTime
            )
            
            var lastUsedApp = ""
            var lastTimeUsed = 0L
            
            for (usageStat in usageStats) {
                if (usageStat.lastTimeUsed > lastTimeUsed) {
                    lastTimeUsed = usageStat.lastTimeUsed
                    lastUsedApp = usageStat.packageName
                }
            }
            
            val isWhatsApp = lastUsedApp == WHATSAPP_PACKAGE
            
            // Enhanced persistence logic: if overlay is visible and WhatsApp was recently active, keep it much longer
            if (isWhatsApp && isOverlayVisible) {
                val timeSinceLastUse = endTime - lastTimeUsed
                if (timeSinceLastUse < 45000) { // Keep active for 45 seconds after last use
                    android.util.Log.d("OverlayService", "WhatsApp recently active - keeping overlay visible (${timeSinceLastUse}ms ago)")
                    return true
                }
            }
            
            // Additional logic: if WhatsApp was detected recently, give it more time
            if (isWhatsApp) {
                val timeSinceLastUse = endTime - lastTimeUsed
                if (timeSinceLastUse < 20000) { // If WhatsApp was used in last 20 seconds, consider it active
                    android.util.Log.d("OverlayService", "WhatsApp very recently active (${timeSinceLastUse}ms ago) - showing overlay")
                    return true
                }
            }
            
            android.util.Log.d("OverlayService", "Detection result - app: $lastUsedApp, isWhatsApp: $isWhatsApp, timeWindow: ${endTime - beginTime}ms, lastUse: ${endTime - lastTimeUsed}ms ago")
            return isWhatsApp
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error detecting WhatsApp: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    private fun showOverlay() {
        if (isOverlayVisible) return
        
        android.util.Log.d("OverlayService", "Creating overlay button...")
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }
        
        android.util.Log.d("OverlayService", "Setting up touch listener...")
        setupTouchListener(overlayView, params)
        
        try {
            android.util.Log.d("OverlayService", "Adding overlay to window manager...")
            windowManager.addView(overlayView, params)
            isOverlayVisible = true
            android.util.Log.d("OverlayService", "Overlay button created and visible!")
            
            // Ensure button appearance matches its state
            if (isButtonLoading) {
                ensureButtonLooksLoading()
            }
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error creating overlay: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun hideOverlay() {
        if (!isOverlayVisible) return
        
        try {
            windowManager.removeView(overlayView)
            isOverlayVisible = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupTouchListener(view: View, params: WindowManager.LayoutParams) {
        android.util.Log.d("OverlayService", "Setting up touch listener for overlay button")
        
        view.setOnTouchListener { v, event ->
            android.util.Log.d("OverlayService", "Touch event received: action=${event.action}, x=${event.x}, y=${event.y}")
            
            // ALWAYS ensure button looks correct for its current state
            if (isButtonLoading) {
                ensureButtonLooksLoading()
            }
            
            // Handle different touch actions
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Only allow clicks if button is not in loading state
                    if (!isButtonLoading) {
                        android.util.Log.d("OverlayService", "Button not loading, allowing click!")
                        onOverlayButtonClick()
                    } else {
                        android.util.Log.d("OverlayService", "Button is loading, ignoring click!")
                    }
                }
                android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP -> {
                    // These events are handled above - button appearance is already ensured
                }
            }
            true
        }
        
        android.util.Log.d("OverlayService", "Touch listener setup complete")
    }
    
        private fun onOverlayButtonClick() {
        android.util.Log.d("OverlayService", "Overlay button clicked - extracting events from current conversation")
        
        // IMMEDIATELY turn button gray and set loading state (synchronous)
        overlayView?.let { view ->
            val imageView = view.findViewById<android.widget.ImageView>(R.id.overlay_button_image)
            if (imageView != null) {
                // Turn gray immediately - no delay
                imageView.setColorFilter(android.graphics.Color.parseColor("#757575"), android.graphics.PorterDuff.Mode.SRC_IN)
                imageView.alpha = 0.8f
                android.util.Log.d("OverlayService", "Button turned gray immediately")
            }
        }
        
        // Set button to loading state
        isButtonLoading = true
        android.util.Log.d("OverlayService", "Button set to loading state")
        
        // Now start the animation and processing
        try {
            android.util.Log.d("OverlayService", "Starting button animation...")
            overlayView?.let { view ->
                android.util.Log.d("OverlayService", "Overlay view found, loading animation...")
                val animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_click)
                if (animation != null) {
                    android.util.Log.d("OverlayService", "Animation loaded successfully, starting...")
                    
                    // Button is already gray, just start animation
                    view.startAnimation(animation)
                    android.util.Log.d("OverlayService", "Animation started!")
                    
                    // Wait for animation to complete before showing modal
                    android.util.Log.d("OverlayService", "Waiting for animation to complete...")
                    handler.postDelayed({
                        android.util.Log.d("OverlayService", "Animation completed, now extracting events...")
                        extractEventsFromCurrentConversation()
                    }, 200) // Wait 200ms for the full animation
                    
                } else {
                    android.util.Log.e("OverlayService", "Failed to load animation - animation is null")
                    extractEventsFromCurrentConversation()
                }
            } ?: run {
                android.util.Log.e("OverlayService", "Overlay view is null, cannot animate")
                extractEventsFromCurrentConversation()
            }
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error during animation: ${e.message}")
            e.printStackTrace()
            extractEventsFromCurrentConversation()
        }
    }
    
    private fun extractEventsFromCurrentConversation() {
        try {
            android.util.Log.d("OverlayService", "Attempting to extract events from current conversation")
            
            // Get conversation text from AccessibilityService
            val conversationText = WhatsAppAccessibilityService.getInstance()?.getCurrentConversationText()
            
            if (conversationText.isNullOrEmpty()) {
                android.util.Log.w("OverlayService", "No conversation text available from AccessibilityService")
                showEventsModal("No conversation text found. Make sure accessibility service is enabled.", emptyList())
                return
            }
            
            android.util.Log.d("OverlayService", "Got conversation text: ${conversationText.take(100)}...")
            
            // Use EventDetector to find calendar events
            val eventDetector = EventDetector()
            val detectedEvents = eventDetector.detectEvents(conversationText)
            
            android.util.Log.d("OverlayService", "Detected ${detectedEvents.size} events")
            showEventsModal(conversationText, detectedEvents)
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error extracting events from conversation: ${e.message}")
            e.printStackTrace()
            showEventsModal("Error extracting events: ${e.message}", emptyList())
        }
    }
    
    private var modalWindow: View? = null
    
    private fun showEventsModal(conversationText: String, events: List<DetectedEvent>) {
        android.util.Log.d("OverlayService", "=== SHOWING EVENTS MODAL ===")
        android.util.Log.d("OverlayService", "Events count: ${events.size}")
        android.util.Log.d("OverlayService", "Conversation text length: ${conversationText.length}")
        
        try {
            // Hide any existing modal first
            hideEventsModal()
            
            android.util.Log.d("OverlayService", "Step 1: Inflating dialog layout...")
            // Create dialog with custom layout
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_calendar_events, null)
            android.util.Log.d("OverlayService", "Step 1: Layout inflated successfully")
            
            android.util.Log.d("OverlayService", "Step 2: Finding views...")
            val tvEventsJson = dialogView.findViewById<TextView>(R.id.tvEventsJson)
            val btnOk = dialogView.findViewById<Button>(R.id.btnOk)
            android.util.Log.d("OverlayService", "Step 2: Views found - TextView: $tvEventsJson, Button: $btnOk")
            
            android.util.Log.d("OverlayService", "Step 3: Generating calendar JSON...")
            // Generate calendar JSON
            val calendarJson = EventToCalendarMapper.convertEventsToCalendarJson(events)
            android.util.Log.d("OverlayService", "Step 3: Calendar JSON generated, length: ${calendarJson.length}")
            android.util.Log.d("OverlayService", "JSON Preview: ${calendarJson.take(200)}...")
            
            tvEventsJson.text = calendarJson
            android.util.Log.d("OverlayService", "Step 4: JSON text set to TextView")
            
            // Set up OK button
            btnOk.setOnClickListener {
                android.util.Log.d("OverlayService", "OK button clicked - dismissing modal")
                hideEventsModal()
            }
            android.util.Log.d("OverlayService", "Step 5: OK button listener set")
            
            android.util.Log.d("OverlayService", "Step 6: Creating window layout params...")
            // Create window layout params for overlay dialog
            val dialogParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            android.util.Log.d("OverlayService", "Step 6: Window layout params created")
            
            android.util.Log.d("OverlayService", "Step 7: Adding modal to window manager...")
            windowManager.addView(dialogView, dialogParams)
            modalWindow = dialogView
            android.util.Log.d("OverlayService", "✅ SUCCESS: Events modal displayed with ${events.size} events")
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "❌ ERROR showing events modal: ${e.message}")
            android.util.Log.e("OverlayService", "❌ ERROR stack trace:", e)
            e.printStackTrace()
            // Fallback to toast if dialog fails
            Toast.makeText(this, "Modal error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun hideEventsModal() {
        modalWindow?.let { modal ->
            try {
                android.util.Log.d("OverlayService", "Hiding events modal...")
                windowManager.removeView(modal)
                modalWindow = null
                android.util.Log.d("OverlayService", "Events modal hidden successfully")
                
                // Restore button to normal state after modal is dismissed
                restoreButtonToNormalState()
                
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "Error hiding modal: ${e.message}")
                modalWindow = null
                
                // Also restore button state on error
                restoreButtonToNormalState()
            }
        }
    }
    
    private fun ensureButtonLooksLoading() {
        try {
            overlayView?.let { view ->
                val imageView = view.findViewById<android.widget.ImageView>(R.id.overlay_button_image)
                if (imageView != null) {
                    // Force the button to look gray if it's in loading state
                    imageView.setColorFilter(android.graphics.Color.parseColor("#757575"), android.graphics.PorterDuff.Mode.SRC_IN)
                    imageView.alpha = 0.8f
                    android.util.Log.d("OverlayService", "Button appearance forced to loading state")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error ensuring button looks loading: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun restoreButtonToNormalState() {
        try {
            android.util.Log.d("OverlayService", "Restoring button to normal state...")
            
            overlayView?.let { view ->
                val imageView = view.findViewById<android.widget.ImageView>(R.id.overlay_button_image)
                if (imageView != null) {
                    // Remove gray tint and restore original appearance
                    imageView.clearColorFilter()
                    imageView.alpha = 0.6f // Restore to original alpha from layout
                    android.util.Log.d("OverlayService", "Button appearance restored")
                }
            }
            
            // Reset loading state
            isButtonLoading = false
            android.util.Log.d("OverlayService", "Button loading state reset to false")
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error restoring button state: ${e.message}")
            e.printStackTrace()
            // Ensure loading state is reset even on error
            isButtonLoading = false
        }
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        if (isOverlayVisible) {
            hideOverlay()
        }
        hideEventsModal()
        handler.removeCallbacksAndMessages(null)
    }
} 