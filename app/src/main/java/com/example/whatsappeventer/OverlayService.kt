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
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    
    // Screen capture and OCR properties
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var textRecognizer: com.google.mlkit.vision.text.TextRecognizer
    private lateinit var executor: ExecutorService
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val CHECK_INTERVAL = 1000L // Check every second
        private const val SCREEN_CAPTURE_REQUEST_CODE = 2001
    }
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        
        // Initialize OCR components
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        executor = Executors.newSingleThreadExecutor()
        
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
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    android.util.Log.d("OverlayService", "Touch DOWN - X: ${event.rawX}, Y: ${event.rawY}")
                    true
                }
                MotionEvent.ACTION_UP -> {
                    android.util.Log.d("OverlayService", "Touch UP - Every touch counts as click!")
                    onOverlayButtonClick()
                    true
                }
                else -> {
                    android.util.Log.d("OverlayService", "Other touch action: ${event.action}")
                    true
                }
            }
        }
        
        android.util.Log.d("OverlayService", "Touch listener setup complete")
    }
    
    private fun onOverlayButtonClick() {
        android.util.Log.d("OverlayService", "Overlay button clicked - starting screen capture and OCR")
        captureScreenAndPerformOCR()
    }
    
    private fun captureScreenAndPerformOCR() {
        try {
            android.util.Log.d("OverlayService", "Starting real screen capture and OCR process")
            
            // For now, we'll capture the current screen using a different approach
            // Since we don't have MediaProjection permission yet, we'll use a workaround
            captureCurrentScreenAndPerformOCR()
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error in screen capture: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun captureCurrentScreenAndPerformOCR() {
        try {
            android.util.Log.d("OverlayService", "Capturing current screen for OCR")
            
            // Get the current display metrics
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            android.util.Log.d("OverlayService", "Screen dimensions: ${screenWidth}x${screenHeight}")
            
            // Create a bitmap of the current screen (this is a simplified approach)
            // In a real implementation, you'd use MediaProjection API
            val bitmap = createScreenBitmap(screenWidth, screenHeight)
            
            if (bitmap != null) {
                android.util.Log.d("OverlayService", "Screen bitmap created, performing OCR")
                performRealOCR(bitmap)
            } else {
                android.util.Log.e("OverlayService", "Failed to create screen bitmap")
                // Fallback to simulated OCR for now
                performFallbackOCR()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error capturing screen: ${e.message}")
            e.printStackTrace()
            performFallbackOCR()
        }
    }
    
    private fun createScreenBitmap(width: Int, height: Int): Bitmap? {
        return try {
            // This is a simplified approach - in reality you'd need MediaProjection
            // For now, we'll create a test bitmap to demonstrate the OCR flow
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            android.util.Log.d("OverlayService", "Created test bitmap: ${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error creating bitmap: ${e.message}")
            null
        }
    }
    
    private fun performRealOCR(bitmap: Bitmap) {
        try {
            android.util.Log.d("OverlayService", "Starting real OCR on bitmap")
            
            // Convert bitmap to InputImage for ML Kit
            val image = InputImage.fromBitmap(bitmap, 0)
            
            // Perform text recognition
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val ocrText = visionText.text
                    android.util.Log.d("OverlayService", "Real OCR completed! Text length: ${ocrText.length}")
                    android.util.Log.d("OverlayService", "OCR Result: $ocrText")
                    
                    if (ocrText.isNotEmpty()) {
                        processOCRResult(ocrText)
                    } else {
                        android.util.Log.d("OverlayService", "No text detected in image")
                        processOCRResult("No text detected")
                    }
                    
                    // Clean up bitmap
                    bitmap.recycle()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("OverlayService", "OCR failed: ${e.message}")
                    e.printStackTrace()
                    bitmap.recycle()
                    performFallbackOCR()
                }
                
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error in real OCR: ${e.message}")
            e.printStackTrace()
            bitmap.recycle()
            performFallbackOCR()
        }
    }
    
    private fun performFallbackOCR() {
        android.util.Log.d("OverlayService", "Using fallback OCR method")
        val fallbackText = "Fallback: Screen capture not available yet - requires MediaProjection permission"
        android.util.Log.d("OverlayService", "Fallback OCR Result: $fallbackText")
        processOCRResult(fallbackText)
    }
    
    private fun processOCRResult(text: String) {
        try {
            android.util.Log.d("OverlayService", "Processing OCR result: $text")
            
            // Add your custom logic here to process the extracted text
            // For example, search for specific keywords, extract phone numbers, etc.
            
            // Log the final processed result
            android.util.Log.d("OverlayService", "Final processed OCR result: $text")
            
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error processing OCR result: ${e.message}")
            e.printStackTrace()
        }
        
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isOverlayVisible) {
            hideOverlay()
        }
        handler.removeCallbacksAndMessages(null)
        
        // Clean up OCR resources
        textRecognizer.close()
        executor.shutdown()
    }
} 