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
import java.util.*

class OverlayService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var handler: Handler
    
    private var isOverlayVisible = false
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
            android.util.Log.d("OverlayService", "Any touch triggers click!")
            onOverlayButtonClick()
            true
        }
        
        android.util.Log.d("OverlayService", "Touch listener setup complete")
    }
    
    private fun onOverlayButtonClick() {
        android.util.Log.d("OverlayService", "Overlay button clicked - extracting events from current conversation")
        extractEventsFromCurrentConversation()
    }
    
    private fun extractEventsFromCurrentConversation() {
        try {
            android.util.Log.d("OverlayService", "Attempting to extract events from current conversation")
            
            // Get conversation text from AccessibilityService
            val conversationText = WhatsAppAccessibilityService.getInstance()?.getCurrentConversationText()
            
            if (conversationText.isNullOrEmpty()) {
                android.util.Log.w("OverlayService", "No conversation text available from AccessibilityService")
                Toast.makeText(this, "No conversation text found. Make sure accessibility service is enabled.", Toast.LENGTH_SHORT).show()
                return
            }
            
            android.util.Log.d("OverlayService", "Got conversation text: ${conversationText.take(100)}...")
            
            // Use EventDetector to find calendar events
            val eventDetector = EventDetector()
            val detectedEvents = eventDetector.detectEvents(conversationText)
            
            if (detectedEvents.isEmpty()) {
                Toast.makeText(this, "No calendar events detected in conversation", Toast.LENGTH_SHORT).show()
                android.util.Log.d("OverlayService", "No events detected")
            } else {
                val eventSummary = detectedEvents.joinToString("\n") { event ->
                    val dateStr = event.dateTime?.let { 
                        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                        sdf.format(it.time)
                    } ?: "Date TBD"
                    "• ${event.title} - $dateStr"
                }
                
                android.util.Log.d("OverlayService", "Detected events:\n$eventSummary")
                Toast.makeText(this, "Found ${detectedEvents.size} events:\n$eventSummary", Toast.LENGTH_LONG).show()
                
                // TODO: Show events in a proper UI and allow user to add to calendar
            }
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error extracting events from conversation: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error extracting events: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        if (isOverlayVisible) {
            hideOverlay()
        }
        handler.removeCallbacksAndMessages(null)
    }
} 