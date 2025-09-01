package com.example.whatsappeventer

import android.app.*
import android.content.Context
import android.content.Intent
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
        startForeground(NOTIFICATION_ID, createNotification())
        
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
            val beginTime = endTime - 20000 // Check last 20 seconds for much better reliability
            
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
            
            // Smart persistence logic: if overlay is visible and WhatsApp was recently active, keep it much longer
            if (isWhatsApp && isOverlayVisible) {
                val timeSinceLastUse = endTime - lastTimeUsed
                if (timeSinceLastUse < 30000) { // Keep active for 30 seconds after last use
                    android.util.Log.d("OverlayService", "WhatsApp recently active - keeping overlay visible (${timeSinceLastUse}ms ago)")
                    return true
                }
            }
            
            // Additional logic: if WhatsApp was detected recently, give it more time
            if (isWhatsApp) {
                val timeSinceLastUse = endTime - lastTimeUsed
                if (timeSinceLastUse < 12000) { // If WhatsApp was used in last 12 seconds, consider it active
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
            x = 100
            y = 200
        }
        
        setupTouchListener(overlayView, params)
        
        try {
            windowManager.addView(overlayView, params)
            isOverlayVisible = true
        } catch (e: Exception) {
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
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    
                    try {
                        windowManager.updateViewLayout(overlayView, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (Math.abs(event.rawX - initialTouchX) < 10 && 
                        Math.abs(event.rawY - initialTouchY) < 10) {
                        // This was a click, not a drag
                        onOverlayButtonClick()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    private fun onOverlayButtonClick() {
        // Handle overlay button click
        Toast.makeText(this, "WhatsApp Event Button Clicked!", Toast.LENGTH_SHORT).show()
        
        // You can add your custom logic here
        // For example, open a specific activity or perform an action
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isOverlayVisible) {
            hideOverlay()
        }
        handler.removeCallbacksAndMessages(null)
    }
} 